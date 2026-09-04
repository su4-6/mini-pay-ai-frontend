import { ConsumerFundsOrders } from '../components/ConsumerFundsOrders';
import { getOpsRecharge, getOpsRecharges } from '../services/ops';

export default function RechargesPage() {
  return <ConsumerFundsOrders kind="recharge" routeKey="recharges" list={getOpsRecharges} get={getOpsRecharge} />;
}
