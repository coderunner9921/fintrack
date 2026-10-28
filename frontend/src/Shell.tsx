import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { BarChart3, FileCheck2, Home, LogOut, Menu, Receipt, Tags, Wallet, BookOpen, X } from 'lucide-react';
import { useAuth } from './auth';
import { Logo } from './ui';

const NAV = [
  { to: '/app', label: 'Overview', icon: Home, end: true },
  { to: '/app/accounts', label: 'Accounts', icon: Wallet },
  { to: '/app/transactions', label: 'Transactions', icon: Receipt },
  { to: '/app/categories', label: 'Categories', icon: Tags },
  { to: '/app/analytics', label: 'Analytics', icon: BarChart3 },
  { to: '/app/reconciliation', label: 'Reconciliation', icon: FileCheck2 },
  { to: '/app/help', label: 'How to use', icon: BookOpen },
];

export default function Shell() {
  const { session, logout } = useAuth();
  const nav = useNavigate();
  const [open, setOpen] = useState(false);
  const who = session?.fullName ?? session?.email ?? '';
  return (
    <div className="shell">
      <a className="skip" href="#content">Skip to main content</a>
      <aside className={`sidebar${open ? ' open' : ''}`}>
        <div className="side-top"><Logo light /><button className="icon-btn only-mobile" onClick={() => setOpen(false)} aria-label="Close menu"><X size={18} /></button></div>
        <nav aria-label="Main">
          {NAV.map(({ to, label, icon: I, end }) => (
            <NavLink key={to} to={to} end={end} onClick={() => setOpen(false)} className={({ isActive }) => `navlink${isActive ? ' active' : ''}`}>
              <I size={18} /> {label}
            </NavLink>
          ))}
        </nav>
      </aside>
      {open && <div className="scrim" onClick={() => setOpen(false)} />}
      <div className="main">
        <div className="topbar">
          <button className="icon-btn only-mobile" onClick={() => setOpen(true)} aria-label="Open menu"><Menu size={20} /></button>
          <div className="spacer" />
          <span className="who" title={session?.email}>{who}</span>
          <button className="btn ghost" onClick={() => { logout(); nav('/login'); }}><LogOut size={16} /> Sign out</button>
        </div>
        <main id="content" tabIndex={-1}><Outlet /></main>
      </div>
    </div>
  );
}
