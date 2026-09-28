import { describe, expect, it } from 'vitest';
import { parseMessage, parseRunEvent } from './ai';

describe('米灵结构化建议操作契约', () => {
  it('保留历史消息中的转账建议，供页面渲染确认卡片', () => {
    const message = parseMessage({
      id: 'message-1',
      role: 'ASSISTANT',
      content: '请确认后进入转账页。',
      suggestedAction: {
        type: 'TRANSFER',
        payeeIdentifier: '13800000000',
        amountFen: 100
      },
      createdAt: '2026-09-28T05:00:00Z'
    });

    expect(message.suggestedAction).toEqual({
      type: 'TRANSFER',
      payeeIdentifier: '13800000000',
      amountFen: 100
    });
  });

  it('SSE 完成事件仍能解析 suggestedAction，不把结构化数据当文本丢弃', () => {
    const event = parseRunEvent({
      event: 'stream.completed',
      data: JSON.stringify({
        id: '9',
        type: 'stream.completed',
        payload: {
          status: 'COMPLETED',
          suggestedAction: { type: 'TRANSFER', payeeIdentifier: '13900000000', amountFen: 1 }
        }
      })
    });

    expect(event?.payload.suggestedAction).toEqual({
      type: 'TRANSFER',
      payeeIdentifier: '13900000000',
      amountFen: 1
    });
  });

  it('拒绝没有 type 的伪操作对象', () => {
    const message = parseMessage({
      id: 'message-2',
      role: 'ASSISTANT',
      content: '普通回复',
      suggestedAction: { payeeIdentifier: '13800000000', amountFen: 100 },
      createdAt: '2026-09-28T05:00:00Z'
    });
    expect(message.suggestedAction).toBeUndefined();
  });
});
