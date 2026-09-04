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
  // AMap Web JS keys must use securityJsCode in the browser. Only fall back to
  // serviceHost when no security code is configured, because the proxy targets
  // the separate REST Web Service key platform.
  browserWindow._AMapSecurityConfig = securityCode
    ? { securityJsCode: securityCode }
    : { serviceHost };

  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.async = true;
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}&plugin=AMap.Geocoder,AMap.PlaceSearch`;
    script.onload = () => resolve(browserWindow.AMap);
    script.onerror = () => reject(new Error('高德地图服务加载失败，请稍后重试'));
    document.head.appendChild(script);
  });
  return loading;
}
