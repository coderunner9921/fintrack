import { Fragment } from 'react';
import { Link } from 'react-router-dom';
import { STATUS_HELP } from './Reconciliation';
import { PageHead } from '../ui';

const STEPS = [
  { t: '1 · Create an account', d: 'Open Accounts → New account. Pick a type, a currency (fixed afterwards) and an opening balance. Balances are computed from the ledger.', to: '/app/accounts' },
  { t: '2 · Check categories', d: 'System categories are ready to use. Add custom ones if you want finer analysis. Transfers need a category of type Both.', to: '/app/categories' },
  { t: '3 · Record transactions', d: 'Income goes into an account, an expense leaves one, and a transfer moves money between two accounts of the same currency.', to: '/app/transactions' },
  { t: '4 · Read your analytics', d: 'Pick a date range (both end dates are included). Totals are per currency and exclude transfers and reversed transactions.', to: '/app/analytics' },
  { t: '5 · Reconcile a statement', d: 'Upload a CSV and see which rows match your records. Nothing is posted automatically.', to: '/app/reconciliation' },
];
const FAQ = [
  ['Is this connected to a real bank?', 'No. FinTrack is a simulation for learning and portfolio demonstration. No real banks, cards or payments are involved — please don’t enter real credentials.'],
  ['Why can’t I edit or delete a transaction?', 'The ledger is immutable. To undo a transaction, use Reverse: it posts a new, opposite entry dated today and keeps the history intact.'],
  ['Why was my transaction rejected?', 'Common causes: amount is zero or has more than 4 decimals; category type doesn’t fit (transfers need “Both”); the account lacks funds (credit cards are exempt); accounts use different currencies; or the transaction currency differs from the account’s.'],
  ['Why am I signed out after a short time?', 'Sessions last 15 minutes and there is no refresh mechanism. Sign in again to continue.'],
  ['Why do I see several totals?', 'Each currency is totalled separately. Amounts in different currencies are never added together.'],
  ['What does the CSV need?', 'Columns date, description, amount, currency (optional reference). Dates as YYYY-MM-DD. Negative = debit (money out), positive = credit (money in).'],
];

export default function Help() {
  return (
    <>
      <PageHead title="How to use FinTrack" sub="A quick tour of what’s available today." />
      <div className="banner info">FinTrack is a simulation. It does not connect to real bank accounts or process real payments. Budgets, recurring transactions and bank sync are not available.</div>
      <div className="grid cols-2">{STEPS.map((s) => <div className="card" key={s.t}><h3>{s.t}</h3><p>{s.d}</p><Link to={s.to}>Open →</Link></div>)}</div>
      <h2 className="sect">Reconciliation statuses</h2>
      <div className="card"><dl className="kv wide">{Object.entries(STATUS_HELP).map(([k, v]) => <Fragment key={k}><dt>{k[0] + k.slice(1).toLowerCase()}</dt><dd>{v}</dd></Fragment>)}</dl></div>
      <h2 className="sect">Good habits</h2>
      <ul className="card tips"><li>Record transactions on the date they actually happened.</li><li>Use specific descriptions; they make statement matching easier to review.</li><li>Reconcile regularly, and investigate unmatched and mismatched rows before assuming an error.</li><li>Keep one account per real-world balance you want to track.</li></ul>
      <h2 className="sect">Security note</h2>
      <p className="card">Your sign-in token is kept in this tab’s session storage and is cleared when you sign out or the tab closes. JavaScript-held tokens are less protected than HttpOnly cookies, so use FinTrack only on devices you trust.</p>
      <h2 className="sect">FAQ</h2>
      {FAQ.map(([q, a]) => <details className="card faq" key={q}><summary>{q}</summary><p>{a}</p></details>)}
    </>
  );
}
