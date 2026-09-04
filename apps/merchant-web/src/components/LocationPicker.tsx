import { useEffect, useRef, useState } from 'react';
import { Button, Input, Space, Typography } from 'antd';
import { loadAMap } from '../utils/amap-loader';

export interface LocationValue {
  latitude: number;
  longitude: number;
}

interface LocationPickerProps {
  value?: LocationValue;
  onChange?: (location: LocationValue) => void;
  onAddressChange?: (address: string) => void;
  disabled?: boolean;
}

const DEFAULT_CENTER: [number, number] = [116.397428, 39.90923]; // 北京天安门

// 提取高德回调失败时的具体错误码（如 INVALID_USER_SCODE / DAILY_QUERY_OVER_LIMIT）
function errorDetail(status: string, result: unknown): string {
  if (typeof result === 'string' && result) {
    return result;
  }
  const detail = result as { info?: string; type?: string; message?: string } | undefined;
  if (detail?.info) {
    return `${status}（${detail.info}）`;
  }
  if (detail?.message) {
    return `${status}（${detail.message}）`;
  }
  return detail?.type ? `${status}（${detail.type}）` : String(status);
}

/**
 * 高德地图选点组件：点击地图/搜索地址定位，逆地理编码回填地址文本。
 * AMap JS API 只在该组件出现时异步加载，避免地图网络阻塞登录和普通管理页面。
 */
export default function LocationPicker({
  value,
  onChange,
  onAddressChange,
  disabled
}: LocationPickerProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [searchText, setSearchText] = useState('');
  const [ready, setReady] = useState(
    () => Boolean((window as unknown as { AMap?: typeof AMap }).AMap)
  );
  const [map, setMap] = useState<AMap.Map>();
  const [marker, setMarker] = useState<AMap.Marker>();
  const [serviceError, setServiceError] = useState('');
  const onChangeRef = useRef(onChange);
  const onAddressChangeRef = useRef(onAddressChange);
  onChangeRef.current = onChange;
  onAddressChangeRef.current = onAddressChange;

  // 加载高德插件（Geocoder/PlaceSearch）。5 秒未就绪说明插件加载失败
  // （常见原因：新 Key 缺少安全密钥 securityJsCode），给出可见提示而非静默无反应。
  const ensurePlugin = (
    name: 'AMap.Geocoder' | 'AMap.PlaceSearch',
    property: 'Geocoder' | 'PlaceSearch',
    callback: () => void
  ) => {
    const amap = (window as unknown as { AMap?: typeof AMap }).AMap;
    if (!amap) {
      return;
    }
    const timer = window.setTimeout(() => {
      if (!amap[property]) {
        setServiceError('地图服务插件未加载，请检查 AMAP_KEY 与安全密钥（jscode）配置');
      }
    }, 5000);
    amap.plugin([name], () => {
      window.clearTimeout(timer);
      setServiceError('');
      callback();
    });
  };

  useEffect(() => {
    let active = true;
    void loadAMap()
      .then((amap) => {
        if (active) setReady(Boolean(amap));
      })
      .catch((error: Error) => {
        if (active) setServiceError(error.message);
      });
    return () => {
      active = false;
    };
  }, []);

  // 初始化地图
  useEffect(() => {
    if (!ready || !containerRef.current) {
      return;
    }
    const amap = (window as unknown as { AMap: typeof AMap }).AMap;
    const instance = new amap.Map(containerRef.current, {
      zoom: 14,
      center: value ? [value.longitude, value.latitude] : DEFAULT_CENTER
    });
    setMap(instance);
    return () => {
      instance.destroy();
      setMap(undefined);
      setMarker(undefined);
    };
    // value 只在初始化时作为地图中心使用
  }, [ready]);

  // value 变化时同步标记和视野
  useEffect(() => {
    if (!map) {
      return;
    }
    const amap = (window as unknown as { AMap: typeof AMap }).AMap;
    if (!value) {
      if (marker) {
        marker.setMap(null);
        setMarker(undefined);
      }
      return;
    }
    const position: [number, number] = [value.longitude, value.latitude];
    if (!marker) {
      const instance = new amap.Marker({ position });
      instance.setMap(map);
      setMarker(instance);
    } else {
      marker.setPosition(position);
    }
    map.setCenter(position);
  }, [map, marker, value]);

  // 点击地图选点 + 逆地理编码
  useEffect(() => {
    if (!map || disabled) {
      return;
    }
    const onClick = (event: AMap.MapsEvent<'click', AMap.Marker>) => {
      const lnglat = event.lnglat;
      const lng = lnglat.getLng();
      const lat = lnglat.getLat();
      onChangeRef.current?.({ latitude: lat, longitude: lng });
      const amap = (window as unknown as { AMap: typeof AMap }).AMap;
      // Geocoder 是高德 v2.0 插件，使用前先加载
      ensurePlugin('AMap.Geocoder', 'Geocoder', () => {
        const geocoder = new amap.Geocoder({ city: '全国' });
        geocoder.getAddress([lng, lat], (status, result) => {
          if (status === 'complete' && typeof result !== 'string' && result?.regeocode) {
            setServiceError('');
            onAddressChangeRef.current?.(result.regeocode.formattedAddress);
          } else {
            console.warn('[LocationPicker] 逆地理编码失败:', status, result);
            setServiceError(
              `位置已选中，但地址自动回填失败（${errorDetail(status, result)}）。可手动填写地址，并检查高德 Key 的域名白名单`
            );
          }
        });
      });
    };
    map.on('click', onClick);
    return () => {
      map.off('click', onClick);
    };
  }, [map, disabled]);

  // 搜索地址定位
  const search = () => {
    if (!map || !searchText.trim()) {
      return;
    }
    const amap = (window as unknown as { AMap: typeof AMap }).AMap;
    // PlaceSearch 是高德 v2.0 插件，使用前先加载
    ensurePlugin('AMap.PlaceSearch', 'PlaceSearch', () => {
      const placeSearch = new amap.PlaceSearch({ pageSize: 1 });
      placeSearch.search(searchText.trim(), (status, result) => {
        const poi = typeof result === 'string' ? undefined : result?.poiList?.pois?.[0];
        if (status === 'complete' && poi && poi.location) {
          setServiceError('');
          const lng = poi.location.getLng();
          const lat = poi.location.getLat();
          onChangeRef.current?.({ latitude: lat, longitude: lng });
          onAddressChangeRef.current?.(
            poi.name + (poi.address ? `（${poi.address}）` : '')
          );
          map.setZoomAndCenter(16, [lng, lat]);
        } else {
          console.warn('[LocationPicker] 地址搜索失败:', status, result);
          setServiceError(
            `地址搜索失败（${errorDetail(status, result)}），请检查高德 Key 与域名白名单配置`
          );
        }
      });
    });
  };

  if (!ready) {
    return (
      <Typography.Text type="secondary">
        地图服务未加载（未配置 AMAP_KEY），可直接填写文字地址。
      </Typography.Text>
    );
  }

  return (
    <div>
      <Space.Compact style={{ width: '100%', marginBottom: 8 }}>
        <Input
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          onPressEnter={search}
          placeholder="搜索地址定位"
          disabled={disabled}
        />
        <Button onClick={search} disabled={disabled || !searchText.trim()}>搜索</Button>
      </Space.Compact>
      <div ref={containerRef} style={{ height: 240, borderRadius: 8, overflow: 'hidden' }} />
      <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
        {value
          ? `经纬度：${value.latitude.toFixed(6)}, ${value.longitude.toFixed(6)}`
          : '点击地图选择经营位置'}
      </Typography.Paragraph>
      {serviceError ? (
        <Typography.Paragraph type="danger" style={{ marginTop: 8, marginBottom: 0 }}>
          {serviceError}
        </Typography.Paragraph>
      ) : null}
    </div>
  );
}
