import { describe, expect, it, vi } from 'vitest';
import { reverseGeocode } from './amap-loader';

describe('reverseGeocode', () => {
  it('returns the formatted address from AMap', async () => {
    const getAddress = vi.fn((_position, callback) => {
      callback('complete', { regeocode: { formattedAddress: '河南省洛阳市洛龙区测试路 1 号' } });
    });
    const amap = {
      Geocoder: vi.fn(() => ({ getAddress })),
      plugin: vi.fn((_names, callback) => callback())
    } as unknown as typeof AMap;

    await expect(reverseGeocode(112.36536, 34.66486, amap)).resolves.toBe('河南省洛阳市洛龙区测试路 1 号');
    expect(getAddress).toHaveBeenCalledWith([112.36536, 34.66486], expect.any(Function));
  });

  it('rejects when AMap cannot resolve an address', async () => {
    const amap = {
      Geocoder: vi.fn(() => ({
        getAddress: (_position: unknown, callback: (status: string, result: unknown) => void) => callback('error', { info: 'INVALID_USER_SCODE' })
      })),
      plugin: vi.fn((_names: unknown, callback: () => void) => callback())
    } as unknown as typeof AMap;

    await expect(reverseGeocode(112, 34, amap)).rejects.toThrow('INVALID_USER_SCODE');
  });
});
