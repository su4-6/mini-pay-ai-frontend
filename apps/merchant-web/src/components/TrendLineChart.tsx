import { Line } from '@ant-design/plots';
import type { ComponentProps } from 'react';

export default function TrendLineChart(props: ComponentProps<typeof Line>) {
  return <Line {...props} />;
}
