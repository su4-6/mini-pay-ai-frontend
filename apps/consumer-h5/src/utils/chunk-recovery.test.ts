import { describe, expect, it } from 'vitest';
import { isChunkLoadFailure } from './chunk-recovery';

describe('chunk recovery', () => {
  it.each([
    new Error('Loading chunk 364 failed.'),
    new Error('Loading CSS chunk 364 failed.'),
    new Error('Failed to fetch dynamically imported module'),
    'ChunkLoadError: missing page chunk'
  ])('识别页面分包加载失败', (error) => {
    expect(isChunkLoadFailure(error)).toBe(true);
  });

  it('不把普通业务错误当成分包故障', () => {
    expect(isChunkLoadFailure(new Error('payment password invalid'))).toBe(false);
  });
});
