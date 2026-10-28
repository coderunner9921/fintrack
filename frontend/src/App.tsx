import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth';
import Shell from './Shell';
import Landing from './pages/Landing';
import { Login, Register } from './pages/AuthPages';
import Overview from './pages/Overview';
import Accounts from './pages/Accounts';
import Transactions from './pages/Transactions';
import Categories from './pages/Categories';
import Analytics from './pages/Analytics';
import Reconciliation from './pages/Reconciliation';
import Help from './pages/Help';

function Protected() {
  const { session } = useAuth();
  const loc = useLocation();
  if (!session) return <Navigate to="/login" replace state={{ from: loc.pathname }} />;
  return <Shell />;
}

function GuestOnly({ children }: { children: JSX.Element }) {
  const { session } = useAuth();
  return session ? <Navigate to="/app" replace /> : children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<GuestOnly><Login /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><Register /></GuestOnly>} />
      <Route path="/app" element={<Protected />}>
        <Route index element={<Overview />} />
        <Route path="accounts" element={<Accounts />} />
        <Route path="transactions" element={<Transactions />} />
        <Route path="categories" element={<Categories />} />
        <Route path="analytics" element={<Analytics />} />
        <Route path="reconciliation" element={<Reconciliation />} />
        <Route path="help" element={<Help />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
