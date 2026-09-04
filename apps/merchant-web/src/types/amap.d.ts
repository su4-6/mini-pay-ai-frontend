/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * AMap is injected by the merchant portal's head script at runtime.
 * Keep this ambient declaration local so strict TypeScript checking does not
 * require a browser-only SDK during development or production builds.
 */
declare namespace AMap {
  type MapsEvent<T extends string = string, Target = unknown> = {
    type: T;
    target: Target;
    lnglat: { getLng(): number; getLat(): number };
  };

  type Map = {
    on<T extends string, Target = unknown>(event: T, handler: (event: MapsEvent<T, Target>) => void): void;
    off<T extends string, Target = unknown>(event: T, handler: (event: MapsEvent<T, Target>) => void): void;
    setCenter(position: [number, number]): void;
    setZoomAndCenter(zoom: number, position: [number, number]): void;
    destroy(): void;
  };

  type Marker = {
    setMap(map: Map | null): void;
    setPosition(position: [number, number]): void;
  };

  type Geocoder = {
    getAddress(position: [number, number], callback: (status: string, result: any) => void): void;
  };

  type PlaceSearch = {
    search(keyword: string, callback: (status: string, result: any) => void): void;
  };

  interface AMapStatic {
    Map: new (container: HTMLElement, options: any) => Map;
    Marker: new (options: any) => Marker;
    Geocoder: new (options: any) => Geocoder;
    PlaceSearch: new (options: any) => PlaceSearch;
    plugin(names: string[], callback: () => void): void;
  }
}

declare const AMap: AMap.AMapStatic;
