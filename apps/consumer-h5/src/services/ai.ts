import type {
  AiConversation,
  AiConversationPage,
  AiMessage,
  AiMessagePage,
  AiRunEvent,
  AiRunHandle
} from '../types/consumer';
import { asRecord, readFen, readPage, readString } from './parsers';
import { buildQuery, httpRequest } from './http';
import type { SseFrame } from './sse';

/**
 * 米灵 AI：
 *   GET  /api/v1/ai/conversations
 *   POST /api/v1/ai/conversations
 *   GET  /api/v1/ai/conversations/{id}/messages
 *   POST /api/v1/ai/conversations/{id}/messages   {content, clientMessageId?} → {runId}
 *   SSE  /api/v1/ai/runs/{runId}/events
 */

export const AI_EVENT_TYPE = {
  DELTA: 'message.delta',
  MESSAGE_COMPLETED: 'message.completed',
  TASK_ERROR: 'task.error',
  SECURITY_ERROR: 'security.error',
  RUN_FAILED: 'run.failed',
  STREAM_COMPLETED: 'stream.completed'
} as const;

const ERROR_EVENT_TYPES: string[] = [
  AI_EVENT_TYPE.TASK_ERROR,
  AI_EVENT_TYPE.SECURITY_ERROR,
  AI_EVENT_TYPE.RUN_FAILED,
  'stream.error',
  'error'
];

function parseConversation(raw: unknown): AiConversation {
  const record = asRecord(raw);
  return {
    id: readString(record, 'id', 'conversationId') ?? '',
    title: readString(record, 'title') ?? '新会话',
    status: readString(record, 'status'),
    lastMessageAt: readString(record, 'lastMessageAt'),
    createdAt: readString(record, 'createdAt'),
    updatedAt: readString(record, 'updatedAt')
  };
}

function parseMessage(raw: unknown): AiMessage {
  const record = asRecord(raw);
  const role = (readString(record, 'role') ?? 'ASSISTANT').toUpperCase();
  return {
    id: readString(record, 'id', 'messageId') ?? '',
    runId: readString(record, 'runId'),
    role: role === 'USER' || role === 'SYSTEM' || role === 'TOOL' ? role : 'ASSISTANT',
    content: readString(record, 'content', 'text') ?? '',
    cardType: readString(record, 'cardType'),
    sequenceNo: readFen(record, 'sequenceNo'),
    createdAt: readString(record, 'createdAt', 'occurredAt') ?? ''
  };
}

export async function listConversations(): Promise<AiConversationPage> {
  const raw = await httpRequest<unknown>(`/api/v1/ai/conversations${buildQuery({ limit: 50 })}`, {
    method: 'GET'
  });
  const page = readPage(raw);
  return { items: page.items.map(parseConversation).filter((item) => item.id), nextCursor: page.nextCursor };
}

export async function createConversation(title?: string): Promise<AiConversation> {
  const raw = await httpRequest<unknown>('/api/v1/ai/conversations', {
    method: 'POST',
    data: title ? { title } : {}
  });
  return parseConversation(asRecord(raw).conversation ?? raw);
}

export async function listMessages(conversationId: string): Promise<AiMessagePage> {
  const raw = await httpRequest<unknown>(
    `/api/v1/ai/conversations/${encodeURIComponent(conversationId)}/messages${buildQuery({ limit: 100 })}`,
    { method: 'GET' }
  );
  const page = readPage(raw);
  return { items: page.items.map(parseMessage).filter((item) => item.id), nextCursor: page.nextCursor };
}

export async function sendMessage(
  conversationId: string,
  content: string,
  clientMessageId: string
): Promise<AiRunHandle> {
  const raw = await httpRequest<unknown>(
    `/api/v1/ai/conversations/${encodeURIComponent(conversationId)}/messages`,
    { method: 'POST', data: { content, clientMessageId } }
  );
  const record = asRecord(raw);
  const runId = readString(record, 'runId');
  if (!runId) throw new Error('AI 发送响应缺少 runId');
  return { runId };
}

export function runEventsUrl(runId: string): string {
  return `/api/v1/ai/runs/${encodeURIComponent(runId)}/events`;
}

/** 把 SSE 帧解析为运行事件；心跳与脏数据返回 null。 */
export function parseRunEvent(frame: SseFrame): AiRunEvent | null {
  const type = frame.event && frame.event !== 'message' ? frame.event : undefined;
  if (type === 'heartbeat' || type === 'ping') return null;
  if (!frame.data) return null;
  let decoded: unknown;
  try {
    decoded = JSON.parse(frame.data);
  } catch {
    return null;
  }
  const record = asRecord(decoded);
  const payload = asRecord(record.payload);
  const eventType = readString(record, 'type') ?? type;
  if (!eventType) return null;
  if (eventType === 'heartbeat' || eventType === 'ping') return null;
  return {
    id: readString(record, 'id') ?? frame.id ?? '',
    type: eventType,
    conversationId: readString(record, 'conversationId'),
    runId: readString(record, 'runId'),
    occurredAt: readString(record, 'occurredAt'),
    traceId: readString(record, 'traceId'),
    payload
  };
}

export function isRunTerminal(event: AiRunEvent): boolean {
  return event.type === AI_EVENT_TYPE.STREAM_COMPLETED;
}

export function isRunFailure(event: AiRunEvent): boolean {
  return ERROR_EVENT_TYPES.includes(event.type);
}

export function deltaText(event: AiRunEvent): string {
  return readString(event.payload, 'text', 'delta', 'content') ?? '';
}

export function errorText(event: AiRunEvent): string {
  return (
    readString(event.payload, 'message', 'detail', 'text') ?? '米灵任务执行失败，请稍后重试'
  );
}

export function errorRetryable(event: AiRunEvent): boolean {
  const value = event.payload.retryable;
  if (typeof value === 'boolean') return value;
  if (value === 'true') return true;
  return false;
}
