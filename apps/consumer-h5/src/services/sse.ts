import { ApiProblemError } from '@minipay/api-client';
import { createRequestId } from '@minipay/shared';
import { problemFromPayload } from './http';

/**
 * 运行事件流（SSE）。
 *
 * 这里刻意使用 `fetch` + `ReadableStream`，而不是 Umi Request：
 * Umi Request 基于 axios，浏览器适配器会把响应体整体读完，无法逐帧暴露 SSE。
 * 这不是「第二套请求层」——业务读写仍然全部走 Umi Request，
 * 本模块只负责 `GET /api/v1/ai/runs/{runId}/events` 的流式读取。
 *
 * 语义对齐 Android 的 `AiConversationRepository.resumableEvents`：
 *   - 用 `Last-Event-ID` 断点续传，事件 id 单调递增时才推进游标；
 *   - 心跳（heartbeat）事件由上层过滤；
 *   - 非终态断开时按 500/1000/2000ms 退避重连，最多 3 次；
 *   - 收到终态事件（stream.completed）立即停止并取消读取。
 */

export interface SseFrame {
  id?: string;
  event?: string;
  data: string;
}

export interface ParsedSseBuffer {
  frames: SseFrame[];
  rest: string;
}

const MAX_RECONNECTS = 3;
const RECONNECT_DELAYS_MS = [500, 1_000, 2_000];

/** 纯函数：从缓冲区切出完整的事件块，返回未完成部分。 */
export function parseSseFrames(buffer: string): ParsedSseBuffer {
  const normalized = buffer.replace(/\r\n/g, '\n');
  const blocks = normalized.split('\n\n');
  const rest = blocks.pop() ?? '';
  const frames: SseFrame[] = [];
  for (const block of blocks) {
    if (!block.trim()) continue;
    const frame: SseFrame = { data: '' };
    const dataLines: string[] = [];
    let hasField = false;
    for (const line of block.split('\n')) {
      if (!line || line.startsWith(':')) continue;
      const separator = line.indexOf(':');
      const field = separator === -1 ? line : line.slice(0, separator);
      const rawValue = separator === -1 ? '' : line.slice(separator + 1);
      const value = rawValue.startsWith(' ') ? rawValue.slice(1) : rawValue;
      if (field === 'id') {
        frame.id = value;
        hasField = true;
      } else if (field === 'event') {
        frame.event = value;
        hasField = true;
      } else if (field === 'data') {
        dataLines.push(value);
        hasField = true;
      }
    }
    if (!hasField) continue;
    frame.data = dataLines.join('\n');
    frames.push(frame);
  }
  return { frames, rest };
}

class StreamClosedEarlyError extends Error {
  constructor() {
    super('SSE_CLOSED_EARLY');
    this.name = 'StreamClosedEarlyError';
  }
}

export interface StreamRunEventsOptions {
  url: string;
  signal: AbortSignal;
  lastEventId?: string | null;
  /** 返回 true 表示收到终态事件，可以停止读取。 */
  isTerminal: (frame: SseFrame) => boolean;
  onFrame: (frame: SseFrame) => void;
  onReconnecting?: (attempt: number) => void;
  onCursor?: (eventId: string) => void;
  /**
   * 非终态断开时是否重连。默认重连；上层已收到明确的失败事件时可以返回 false，
   * 让流以「已结束」收尾而不是继续退避重试。
   */
  shouldReconnect?: () => boolean;
}

function delay(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = globalThis.setTimeout(resolve, ms);
    signal.addEventListener(
      'abort',
      () => {
        globalThis.clearTimeout(timer);
        resolve();
      },
      { once: true }
    );
  });
}

function isAbort(error: unknown): boolean {
  return (error as { name?: string } | undefined)?.name === 'AbortError';
}

async function readStreamOnce(
  options: StreamRunEventsOptions,
  lastEventId: string | null,
  state: { terminal: boolean }
): Promise<void> {
  const requestId = createRequestId();
  const response = await fetch(options.url, {
    method: 'GET',
    credentials: 'include',
    headers: {
      Accept: 'text/event-stream',
      'Cache-Control': 'no-cache',
      'X-Request-Id': requestId,
      ...(lastEventId ? { 'Last-Event-ID': lastEventId } : {})
    },
    signal: options.signal
  });

  if (!response.ok) {
    const text = await response.text().catch(() => '');
    let body: unknown;
    try {
      body = text ? JSON.parse(text) : undefined;
    } catch {
      body = undefined;
    }
    throw problemFromPayload(response.status, body, requestId);
  }
  if (!response.body) {
    throw problemFromPayload(response.status, undefined, requestId);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const { frames, rest } = parseSseFrames(buffer);
      buffer = rest;
      for (const frame of frames) {
        if (frame.id) options.onCursor?.(frame.id);
        options.onFrame(frame);
        if (options.isTerminal(frame)) {
          state.terminal = true;
          return;
        }
      }
    }
  } finally {
    await reader.cancel().catch(() => undefined);
  }
}

/** 建立一次带自动重连的事件流订阅，直到终态或重试耗尽。 */
export async function streamRunEvents(options: StreamRunEventsOptions): Promise<void> {
  let lastEventId = options.lastEventId ?? null;
  const state = { terminal: false };
  let attempt = 0;
  // 本地游标必须随事件推进，重连时才能用 Last-Event-ID 从断点续传。
  const tracked: StreamRunEventsOptions = {
    ...options,
    onCursor: (eventId) => {
      lastEventId = eventId;
      options.onCursor?.(eventId);
    }
  };

  for (;;) {
    if (options.signal.aborted) return;
    try {
      await readStreamOnce(tracked, lastEventId, state);
      if (state.terminal) return;
      throw new StreamClosedEarlyError();
    } catch (error) {
      if (options.signal.aborted || isAbort(error)) return;
      if (error instanceof ApiProblemError) throw error;
      const retryable = error instanceof StreamClosedEarlyError;
      if (!retryable) throw error;
      if (options.shouldReconnect && !options.shouldReconnect()) return;
      if (attempt >= MAX_RECONNECTS) {
        throw problemFromPayload(0, undefined, createRequestId());
      }
      options.onReconnecting?.(attempt + 1);
      await delay(RECONNECT_DELAYS_MS[attempt] ?? 2_000, options.signal);
      attempt += 1;
    }
  }
}

export const SSE_MAX_RECONNECTS = MAX_RECONNECTS;
