declare const AMAP_WEB_KEY: string;
declare const AMAP_SECURITY_CODE: string;
declare const AMAP_SERVICE_HOST: string;

let loading: Promise<typeof AMap | undefined> | undefined;

export function loadAMap(): Promise<typeof AMap | undefined> {
  const browserWindow = window as typeof window & {
    AMap?: typeof AMap;
    _AMapSecurityConfig?: { securityJsCode?: string; serviceHost?: string };
  };
  if (browserWindow.AMap) return Promise.resolve(browserWindow.AMap);

  const key = typeof AMAP_WEB_KEY === 'undefined' ? '' : AMAP_WEB_KEY;
  if (!key) return Promise.resolve(undefined);
  if (loading) return loading;

  const securityCode = typeof AMAP_SECURITY_CODE === 'undefined' ? '' : AMAP_SECURITY_CODE;
  const serviceHost = typeof AMAP_SERVICE_HOST === 'undefined' ? '' : AMAP_SERVICE_HOST;
  browserWindow._AMapSecurityConfig = securityCode ? { securityJsCode: securityCode } : { serviceHost };

  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.async = true;
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}&plugin=AMap.Geocoder`;
    script.onload = () => resolve(browserWindow.AMap);
    script.onerror = () => reject(new Error('高德地图服务加载失败'));
    document.head.appendChild(script);
  });
  return loading;
}

type RegeocodeResult = {
  regeocode?: { formattedAddress?: unknown };
  info?: unknown;
};

export async function reverseGeocode(longitude: number, latitude: number, amapOverride?: typeof AMap): Promise<string> {
  const amap = amapOverride ?? await loadAMap();
  if (!amap) throw new Error('地图服务未配置');

  return new Promise((resolve, reject) => {
    amap.plugin(['AMap.Geocoder'], () => {
      if (!amap.Geocoder) {
        reject(new Error('地址解析服务未加载'));
        return;
      }
      const geocoder = new amap.Geocoder({ city: '全国' });
      geocoder.getAddress([longitude, latitude], (status, rawResult) => {
        const result = rawResult as RegeocodeResult;
        const formatted = result?.regeocode?.formattedAddress;
        if (status === 'complete' && typeof formatted === 'string' && formatted.trim()) {
          resolve(formatted.trim());
          return;
        }
        reject(new Error(typeof result?.info === 'string' ? result.info : '未解析到详细地址'));
      });
    });
  });
}
