/* eslint-disable @typescript-eslint/no-explicit-any */
declare namespace AMap {
  type Geocoder = {
    getAddress(position: [number, number], callback: (status: string, result: any) => void): void;
  };

  interface AMapStatic {
    Geocoder: new (options: any) => Geocoder;
    plugin(names: string[], callback: () => void): void;
  }
}

declare const AMap: AMap.AMapStatic;
