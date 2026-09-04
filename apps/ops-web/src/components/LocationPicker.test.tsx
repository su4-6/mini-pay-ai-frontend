import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LocationPicker, { type LocationValue } from './LocationPicker';

function stubAmap() {
  const handlers = new Map<string, (event: unknown) => void>();
  const map = {
    on: vi.fn((name: string, handler: (event: unknown) => void) => handlers.set(name, handler)),
    off: vi.fn((name: string) => handlers.delete(name)),
    setCenter: vi.fn(),
    setZoomAndCenter: vi.fn(),
    destroy: vi.fn()
  };
  const marker = { setMap: vi.fn(), setPosition: vi.fn() };
  const geocoder = {
    getAddress: vi.fn((_pos: number[], callback: (status: string, result: unknown) => void) => {
      callback('complete', { regeocode: { formattedAddress: '上海市浦东新区世纪大道100号' } });
    })
  };
  const placeSearch = {
    search: vi.fn((_keyword: string, callback: (status: string, result: unknown) => void) => {
      callback('complete', {
        poiList: { pois: [{ name: '星河便利店', address: '世纪大道100号', location: { getLng: () => 121.5, getLat: () => 31.2 } }] }
      });
    })
  };
  (window as unknown as Record<string, unknown>).AMap = {
    Map: vi.fn(() => map),
    Marker: vi.fn(() => marker),
    Geocoder: vi.fn(() => geocoder),
    PlaceSearch: vi.fn(() => placeSearch),
    plugin: vi.fn((_names: string | string[], callback: () => void) => callback())
  };
  return { map, marker, geocoder, placeSearch };
}

function clearAmap() {
  delete (window as unknown as Record<string, unknown>).AMap;
}

describe('LocationPicker', () => {
  afterEach(clearAmap);

  it('degrades to a hint when the AMap SDK is not loaded', () => {
    render(<LocationPicker />);
    expect(screen.getByText('地图服务未加载（未配置 AMAP_KEY），可直接填写文字地址。')).toBeTruthy();
  });

  it('picks a location on map click and reverses geocodes the address', async () => {
    const { map, geocoder } = stubAmap();
    const onAddressChange = vi.fn();
    function Controlled() {
      const [location, setLocation] = useState<LocationValue>();
      return (
        <LocationPicker
          value={location}
          onChange={setLocation}
          onAddressChange={onAddressChange}
        />
      );
    }
    render(<Controlled />);

    expect(screen.getByPlaceholderText('搜索地址定位')).toBeTruthy();
    const clickHandler = map.on.mock.calls.find(([name]) => name === 'click')?.[1];
    clickHandler?.({ lnglat: { getLng: () => 121.5, getLat: () => 31.2 } });

    expect(geocoder.getAddress).toHaveBeenCalled();
    expect(onAddressChange).toHaveBeenCalledWith('上海市浦东新区世纪大道100号');
    expect(await screen.findByText('经纬度：31.200000, 121.500000')).toBeTruthy();
  });

  it('searches an address and moves the marker to the result', async () => {
    const { placeSearch, map } = stubAmap();
    const onChange = vi.fn();
    const onAddressChange = vi.fn();
    render(<LocationPicker onChange={onChange} onAddressChange={onAddressChange} />);

    const user = userEvent.setup();
    await user.type(screen.getByPlaceholderText('搜索地址定位'), '星河便利店');
    await user.click(screen.getByRole('button', { name: /搜\s*索/ }));

    expect(placeSearch.search).toHaveBeenCalledWith('星河便利店', expect.any(Function));
    expect(onChange).toHaveBeenCalledWith({ latitude: 31.2, longitude: 121.5 });
    expect(onAddressChange).toHaveBeenCalledWith('星河便利店（世纪大道100号）');
    expect(map.setZoomAndCenter).toHaveBeenCalledWith(16, [121.5, 31.2]);
  });

  it('renders the selected coordinates read-only', async () => {
    stubAmap();
    render(<LocationPicker value={{ latitude: 31.2304, longitude: 121.4737 }} />);
    expect(screen.getByText('经纬度：31.230400, 121.473700')).toBeTruthy();
  });
});
