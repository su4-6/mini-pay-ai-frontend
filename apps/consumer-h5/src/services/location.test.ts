import { beforeEach, describe, expect, it, vi } from 'vitest';
import { reverseGeocode } from './location';

const httpRequestMock = vi.hoisted(() => vi.fn());
vi.mock('./http', async () => {
  const actual = await vi.importActual<typeof import('./http')>('./http');
  return { ...actual, httpRequest: httpRequestMock };
});

beforeEach(() => httpRequestMock.mockReset());

describe('经营地址逆地理解析', () => {
  it('通过同源 BFF 获取格式化地址，不在浏览器暴露高德服务 Key', async () => {
    httpRequestMock.mockResolvedValueOnce({ formattedAddress: '河南省洛阳市洛龙区开元大道 1 号' });
    await expect(reverseGeocode(112.36537, 34.66486)).resolves.toBe('河南省洛阳市洛龙区开元大道 1 号');
    expect(httpRequestMock).toHaveBeenCalledWith(
      '/api/v1/locations/reverse-geocode?longitude=112.36537&latitude=34.66486',
      { method: 'GET' }
    );
  });
});
