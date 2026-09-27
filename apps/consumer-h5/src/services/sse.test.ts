import { describe, expect, it } from 'vitest';
import { parseSseFrames } from './sse';

describe('SSE 帧解析', () => {
  it('按空行切分完整事件，未完成部分留在缓冲区', () => {
    const buffer = 'id: 1\nevent: message.delta\ndata: {"text":"你"}\n\nid: 2\ndata: {"text":"好"}';
    const { frames, rest } = parseSseFrames(buffer);
    expect(frames).toHaveLength(1);
    expect(frames[0]).toEqual({ id: '1', event: 'message.delta', data: '{"text":"你"}' });
    expect(rest).toBe('id: 2\ndata: {"text":"好"}');
  });

  it('支持 CRLF 换行', () => {
    const { frames } = parseSseFrames('id: 7\r\ndata: hello\r\n\r\n');
    expect(frames).toEqual([{ id: '7', data: 'hello' }]);
  });

  it('多行 data 以换行拼接', () => {
    const { frames } = parseSseFrames('data: line1\ndata: line2\n\n');
    expect(frames[0].data).toBe('line1\nline2');
  });

  it('忽略注释行（心跳）', () => {
    const { frames } = parseSseFrames(': keep-alive\n\nid: 9\ndata: x\n\n');
    expect(frames).toHaveLength(1);
    expect(frames[0]).toEqual({ id: '9', data: 'x' });
  });

  it('一个数据块内可解析多个事件', () => {
    const { frames, rest } = parseSseFrames('id: 1\ndata: a\n\nid: 2\ndata: b\n\n');
    expect(frames.map((frame) => frame.id)).toEqual(['1', '2']);
    expect(rest).toBe('');
  });
});
