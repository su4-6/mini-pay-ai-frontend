import { buildQuery, httpRequest } from './http';
import { asRecord, readString } from './parsers';

export async function reverseGeocode(longitude: number, latitude: number): Promise<string> {
  const record = asRecord(await httpRequest<unknown>(
    `/api/v1/locations/reverse-geocode${buildQuery({ longitude, latitude })}`,
    { method: 'GET' }
  ));
  const address = readString(record, 'formattedAddress');
  if (!address) throw new Error('未解析到详细地址');
  return address;
}
