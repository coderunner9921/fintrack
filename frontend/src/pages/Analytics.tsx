import { useState } from 'react';
import { api, type CurrencyAnalytics } from '../api';
import { compareMoney, ratio, type Money } from '../money';
import { Empty, ErrorState, Loading, PageHead, fmtDate, money, useLoad, ymd } from '../ui';

const today = () => new Date();
const PRESETS: { k: string; l: string; range: () => [string, string] }[] = [
  { k: 'month', l: 'This month', range: () => [ymd(new Date(today().getFullYear(), today().getMonth(), 1)), ymd(today())] },
  { k: '30', l: 'Last 30 days', range: () => [ymd(new Date(Date.now() - 29 * 864e5)), ymd(today())] },
  { k: '90', l: 'Last 90 days', range: () => [ymd(new Date(Date.now() - 89 * 864e5)), ymd(today())] },
  { k: 'ytd', l: 'Year to date', range: () => [ymd(new Date(today().getFullYear(), 0, 1)), ymd(today())] },
];

export default function Analytics() {
  const [[from, to], setRange] = useState<[string, string]>(PRESETS[0].range());
  const valid = !!from && !!to && from <= to;
  const { data, error, loading, reload } = useLoad(() => (valid ? api.analytics(from, to) : Promise.resolve(null)), [from, to]);
  return (
    <>
      <PageHead title="Analytics" sub="Income and expenses only. Transfers and reversed transactions are excluded, and currencies are never mixed." />
      <div className="filters card">
        {PRESETS.map((p) => <button key={p.k} className="btn ghost sm" onClick={() => setRange(p.range())}>{p.l}</button>)}
        <input aria-label="From date" type="date" value={from} onChange={(e) => setRange([e.target.value, to])} />
        <input aria-label="To date" type="date" value={to} onChange={(e) => setRange([from, e.target.value])} />
      </div>
      {!valid && <div className="banner warn">The start date must be on or before the end date.</div>}
      {valid && loading && <Loading />}
      {valid && error && <ErrorState message={error} onRetry={reload} />}
      {valid && data && (data.currencies.length === 0
        ? <Empty title="No income or expenses in this period">{fmtDate(data.from)} – {fmtDate(data.to)} (both dates included).</Empty>
        : data.currencies.map((c) => <CurrencyBlock key={c.currency} c={c} />))}
    </>
  );
}

function CurrencyBlock({ c }: { c: CurrencyAnalytics }) {
  const max: Money = compareMoney(c.totalIncome, c.totalExpenses) >= 0 ? c.totalIncome : c.totalExpenses;
  const catMax: Money = c.expensesByCategory.reduce((m, x) => (compareMoney(x.total, m) > 0 ? x.total : m), '0');
  return (
    <section className="card block-sect">
      <h2>{c.currency}</h2>
      <div className="grid cols-3">
        <div className="stat"><small>Income · {c.incomeTransactionCount} txns</small><strong className="pos">{money(c.totalIncome, c.currency)}</strong></div>
        <div className="stat"><small>Expenses · {c.expenseTransactionCount} txns</small><strong>{money(c.totalExpenses, c.currency)}</strong></div>
        <div className="stat"><small>Net cash flow</small><strong className={compareMoney(c.netCashFlow, '0') < 0 ? 'neg' : 'pos'}>{money(c.netCashFlow, c.currency)}</strong></div>
      </div>
      <h3>Income vs expenses</h3>
      <div className="hbars" role="group" aria-label={`Income versus expenses in ${c.currency}`}>
        <div><span>Income</span><i className="bar pos" style={{ width: `${ratio(c.totalIncome, max) * 100}%` }} /><b>{money(c.totalIncome, c.currency)}</b></div>
        <div><span>Expenses</span><i className="bar neg" style={{ width: `${ratio(c.totalExpenses, max) * 100}%` }} /><b>{money(c.totalExpenses, c.currency)}</b></div>
      </div>
      <h3>Spending by category</h3>
      {c.expensesByCategory.length === 0 ? <p className="muted">No expenses in this period.</p> : (
        <ul className="hbars cat">{c.expensesByCategory.map((x) => (
          <li key={x.categoryName}><span>{x.categoryName}</span><i className="bar" style={{ width: `${ratio(x.total, catMax) * 100}%` }} /><b>{money(x.total, c.currency)}</b></li>))}</ul>)}
    </section>
  );
}
