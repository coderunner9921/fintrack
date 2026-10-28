import { Link } from 'react-router-dom';
import { BarChart3, FileCheck2, Receipt, Wallet } from 'lucide-react';
import { Logo } from '../ui';

const FEATURES = [
  { i: Wallet, t: 'Accounts', d: 'Track cash, savings, current, credit-card and investment accounts with ledger-derived balances.' },
  { i: Receipt, t: 'Transactions', d: 'Record income, expenses and internal transfers. Mistakes are reversed, never erased.' },
  { i: BarChart3, t: 'Analytics', d: 'See income, expenses and spending by category for any date range, separated by currency.' },
  { i: FileCheck2, t: 'Reconciliation', d: 'Upload a CSV statement and classify every row as matched, unmatched, duplicate or mismatched.' },
];

export default function Landing() {
  return (
    <div className="landing">
      <header className="l-nav">
        <Logo light />
        <div><Link className="btn ghost light" to="/login">Sign in</Link> <Link className="btn primary" to="/register">Create account</Link></div>
      </header>
      <section className="hero">
        <div>
          <p className="eyebrow">Personal finance, double-entry accurate</p>
          <h1>See every rupee move. <span>Trust every balance.</span></h1>
          <p className="lead">FinTrack is a personal-finance platform built on a double-entry ledger. Manage accounts, record transactions, analyse spending and reconcile bank statements.</p>
          <div className="cta"><Link className="btn primary lg" to="/register">Get started</Link><Link className="btn ghost light lg" to="/login">I already have an account</Link></div>
        </div>
        <div className="preview" aria-label="Illustrative preview with sample data">
          <span className="sample">Illustrative sample data</span>
          <small>Total balance</small><strong>₹1,24,580.00</strong>
          <div className="bars" aria-hidden>{[40, 62, 48, 80, 66, 92, 74].map((h, i) => <i key={i} style={{ height: `${h}%` }} />)}</div>
          <ul><li><span>Salary</span><b className="pos">+₹85,000</b></li><li><span>Rent</span><b>−₹24,000</b></li><li><span>Groceries</span><b>−₹6,420</b></li></ul>
        </div>
      </section>
      <section className="features">
        {FEATURES.map(({ i: I, t, d }) => (<div className="feature" key={t}><I size={22} /><h3>{t}</h3><p>{d}</p></div>))}
      </section>
      <footer className="l-foot">
        <Logo compact /> <p><b>FinTrack is a financial simulation</b> created for learning and portfolio demonstration. It does not connect to real bank accounts or process real payments.</p>
        <nav><Link to="/login">Sign in</Link><Link to="/register">Register</Link></nav>
      </footer>
    </div>
  );
}
