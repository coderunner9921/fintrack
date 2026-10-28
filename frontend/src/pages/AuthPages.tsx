import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { Field, Logo, Spinner, errText } from '../ui';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function Frame({ title, sub, children }: { title: string; sub: string; children: React.ReactNode }) {
  return (
    <div className="authwrap"><div className="authcard">
      <Link to="/" aria-label="FinTrack home"><Logo /></Link>
      <h1>{title}</h1><p className="sub">{sub}</p>{children}
    </div></div>
  );
}

export function Login() {
  const { login, expired } = useAuth();
  const nav = useNavigate();
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/app';
  const [email, setEmail] = useState(''); const [password, setPassword] = useState('');
  const [errs, setErrs] = useState<Record<string, string>>({}); const [formErr, setFormErr] = useState(''); const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault(); if (busy) return;
    const v: Record<string, string> = {};
    if (!email.trim()) v.email = 'Email is required.'; else if (!EMAIL.test(email.trim())) v.email = 'Enter a valid email address.';
    if (!password) v.password = 'Password is required.';
    setErrs(v); setFormErr(''); if (Object.keys(v).length) return;
    setBusy(true);
    try { await login(email.trim(), password); nav(from, { replace: true }); }
    catch (err) { setFormErr(errText(err, { 401: 'Invalid email or password.' })); }
    finally { setBusy(false); }
  }
  return (
    <Frame title="Welcome back" sub="Sign in to your FinTrack workspace.">
      {expired && <div className="banner warn" role="status">Your session expired. Sessions last 15 minutes — please sign in again.</div>}
      <form onSubmit={submit} noValidate>
        <Field id="email" label="Email" error={errs.email}><input id="email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} aria-invalid={!!errs.email} /></Field>
        <Field id="password" label="Password" error={errs.password}><input id="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} aria-invalid={!!errs.password} /></Field>
        {formErr && <div className="banner err" role="alert">{formErr}</div>}
        <button className="btn primary block" disabled={busy}>{busy && <Spinner />} Sign in</button>
      </form>
      <p className="alt">New here? <Link to="/register">Create an account</Link></p>
    </Frame>
  );
}

export function Register() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [fullName, setName] = useState(''); const [email, setEmail] = useState(''); const [password, setPassword] = useState('');
  const [errs, setErrs] = useState<Record<string, string>>({}); const [formErr, setFormErr] = useState(''); const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault(); if (busy) return;
    const v: Record<string, string> = {};
    if (!fullName.trim()) v.fullName = 'Full name is required.'; else if (fullName.trim().length > 100) v.fullName = 'Full name cannot exceed 100 characters.';
    if (!email.trim()) v.email = 'Email is required.'; else if (!EMAIL.test(email.trim())) v.email = 'Enter a valid email address.'; else if (email.length > 255) v.email = 'Email cannot exceed 255 characters.';
    if (!password) v.password = 'Password is required.'; else if (password.length < 8 || password.length > 72) v.password = 'Password must contain between 8 and 72 characters.';
    setErrs(v); setFormErr(''); if (Object.keys(v).length) return;
    setBusy(true);
    try { await register(fullName.trim(), email.trim(), password); nav('/app', { replace: true }); }
    catch (err) { setFormErr(errText(err, { 400: 'We couldn’t create that account. The email may already be registered, or a field is invalid.' })); }
    finally { setBusy(false); }
  }
  return (
    <Frame title="Create your account" sub="Start tracking in under a minute.">
      <form onSubmit={submit} noValidate>
        <Field id="fullName" label="Full name" error={errs.fullName}><input id="fullName" autoComplete="name" value={fullName} onChange={(e) => setName(e.target.value)} aria-invalid={!!errs.fullName} /></Field>
        <Field id="email" label="Email" error={errs.email}><input id="email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} aria-invalid={!!errs.email} /></Field>
        <Field id="password" label="Password" hint="8–72 characters." error={errs.password}><input id="password" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} aria-invalid={!!errs.password} /></Field>
        {formErr && <div className="banner err" role="alert">{formErr}</div>}
        <button className="btn primary block" disabled={busy}>{busy && <Spinner />} Create account</button>
      </form>
      <p className="alt">Already registered? <Link to="/login">Sign in</Link></p>
    </Frame>
  );
}
