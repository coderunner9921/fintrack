import { Link } from 'react-router-dom';
import { CheckCircle2, Circle } from 'lucide-react';
import { api, type Account, type AnalyticsSummary, type Transaction } from '../api';
import { sumMoney, compareMoney, type Money } from '../money';
import { Empty, ErrorState, Loading, PageHead, fmtDate, label, money, useLoad, ymd } from '../ui';

export default function Overview() {
  const { data, error, loading, reload } = useLoad(async () => {
    const now = new Date();
    const [accounts, txs, summary] = await Promise.all([
      api.accounts(), api.transactions(), api.analytics(ymd(new Date(now.getFullYear(), now.getMonth(), 1)), ymd(now)),
    ]);
    return { accounts, txs, summary };
  });
  if (loading) return <Loading />;
  if (error || !data) return <ErrorState message={error ?? ''} onRetry={reload} />;
  const { accounts, txs, summary } = data;
  const totals = totalsByCurrency(accounts);
  const steps = [
    { done: accounts.length > 0, text: 'Open your first account', to: '/app/accounts' },
    { done: txs.length > 0, text: 'Record a transaction', to: '/app/transactions' },
    { done: false, text: 'Reconcile a CSV statement (optional)', to: '/app/reconciliation' },
  ];
  return (
    <>
      <PageHead title="Overview" sub="Your balances and this month’s activity, straight from the ledger." />
      {accounts.length === 0 && (
        <section className="card"><h2>Welcome to FinTrack</h2><ul className="checklist">
          {steps.map((s) => <li key={s.text}>{s.done ? <CheckCircle2 size={18} className="pos" /> : <Circle size={18} />}<Link to={s.to}>{s.text}</Link></li>)}
        </ul><p className="muted">New to this? Read <Link to="/app/help">How to use FinTrack</Link>.</p></section>
      )}
      <div className="grid cols-3">
        {totals.length === 0 ? <div className="card"><h3>Total balance</h3><p className="muted">No active accounts yet.</p></div> :
          totals.map(([cur, sum]) => <div className="card stat" key={cur}><small>Total balance · {cur}</small><strong>{money(sum, cur)}</strong><span className="muted">Active accounts only, never mixed across currencies</span></div>)}
      </div>
      <h2 className="sect">This month</h2>
      <Summary summary={summary} />
      <h2 className="sect">Recent transactions</h2>
      <RecentTx txs={txs.slice(0, 6)} accounts={accounts} />
    </>
  );
}

// Display-only aggregation of the balances the backend already computed, per currency, exact decimal math.
export function totalsByCurrency(accounts: Account[]): [string, Money][] {
  const m = new Map<string, Money[]>();
  accounts.filter((a) => a.active).forEach((a) => m.set(a.currency, [...(m.get(a.currency) ?? []), a.currentBalance]));
  return [...m.entries()].map(([cur, vals]): [string, Money] => [cur, sumMoney(vals)]);
}

function Summary({ summary }: { summary: AnalyticsSummary }) {
  if (summary.currencies.length === 0) return <Empty title="No income or expenses this month">Record a transaction to see your summary.</Empty>;
  return <div className="grid cols-3">{summary.currencies.map((c) => (
    <div className="card" key={c.currency}>
      <small>{c.currency}</small>
      <dl className="kv"><dt>Income</dt><dd className="pos">{money(c.totalIncome, c.currency)}</dd><dt>Expenses</dt><dd>{money(c.totalExpenses, c.currency)}</dd><dt>Net cash flow</dt><dd className={compareMoney(c.netCashFlow, '0') < 0 ? 'neg' : 'pos'}>{money(c.netCashFlow, c.currency)}</dd></dl>
    </div>))}</div>;
}

function RecentTx({ txs, accounts }: { txs: Transaction[]; accounts: Account[] }) {
  if (txs.length === 0) return <Empty title="No transactions yet">Your latest activity will appear here.</Empty>;
  const name = (id: string | null) => accounts.find((a) => a.id === id)?.name ?? '—';
  return <div className="card flush"><ul className="list">{txs.map((t) => (
    <li key={t.id}><div><b>{t.description}</b><small>{fmtDate(t.transactionDate)} · {label(t.transactionType)} · {t.category} · {name(t.sourceAccountId ?? t.destinationAccountId)}</small></div>
      <span className={t.transactionType === 'INCOME' ? 'pos' : ''}>{money(t.amount, t.currency)}</span></li>))}</ul></div>;
}
