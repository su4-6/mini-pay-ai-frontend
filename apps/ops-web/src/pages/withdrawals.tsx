import { ConsumerFundsOrders } from '../components/ConsumerFundsOrders';
import { getOpsWithdrawal, getOpsWithdrawals } from '../services/ops';

export default function WithdrawalsPage() {
  return <ConsumerFundsOrders kind="withdrawal" routeKey="withdrawals" list={getOpsWithdrawals} get={getOpsWithdrawal} />;
}
