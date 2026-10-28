import { useState, type FormEvent } from 'react';
import { Plus } from 'lucide-react';
import { ACCOUNT_TYPES, api, type AccountType } from '../api';
import { isNegative, normalizeMoney, validateAmount } from '../money';
import { Badge, Empty, ErrorState, Field, Loading, Modal, PageHead, Spinner, errText, fmtDate, label, money, useLoad, useSubmitLock, useToast } from '../ui';

export default function Accounts() {
  const { data, error, loading, reload } = useLoad(api.accounts);
  const [open, setOpen] = useState(false);
  if (loading) return <Loading />;
  if (error || !data) return <ErrorState message={error ?? ''} onRetry={reload} />;
  return (
    <>
      <PageHead title="Accounts" sub="Balance = opening balance + ledger debits − ledger credits.">
        <button className="btn primary" onClick={() => setOpen(true)}><Plus size={16} /> New account</button>
      </PageHead>
      {data.length === 0 ? <Empty title="No accounts yet">Create an account to start recording transactions.</Empty> : (
        <div className="grid cols-3">{data.map((a) => (
          <article className="card account" key={a.id}>
            <div className="row"><h3>{a.name}</h3>{!a.active && <Badge tone="muted">Inactive</Badge>}</div>
            <small>{label(a.accountType)} · {a.currency}</small>
            <strong className={isNegative(a.currentBalance) ? 'neg' : ''}>{money(a.currentBalance, a.currency)}</strong>
            <span className="muted">Opened with {money(a.openingBalance, a.currency)} on {fmtDate(a.createdAt)}</span>
          </article>))}</div>
      )}
      {open && <NewAccount onClose={() => setOpen(false)} onDone={() => { setOpen(false); reload(); }} />}
    </>
  );
}

function NewAccount({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const toast = useToast(); const lock = useSubmitLock();
  const [f, setF] = useState({ name: '', accountType: 'SAVINGS' as AccountType, currency: 'INR', openingBalance: '0' });
  const [errs, setErrs] = useState<Record<string, string>>({}); const [busy, setBusy] = useState(false); const [formErr, setFormErr] = useState('');
  async function submit(e: FormEvent) {
    e.preventDefault(); if (busy) return;
    const v: Record<string, string> = {};
    if (!f.name.trim()) v.name = 'Account name is required.'; else if (f.name.trim().length > 100) v.name = 'Account name cannot exceed 100 characters.';
    if (!/^[A-Za-z]{3}$/.test(f.currency)) v.currency = 'Use a three-letter code, such as INR.';
    const balErr = validateAmount(f.openingBalance, { allowZero: true });
    if (balErr) v.openingBalance = balErr;
    setErrs(v); setFormErr(''); if (Object.keys(v).length) return;
    setBusy(true);
    await lock.run(async () => {
      try {
        await api.createAccount({ name: f.name.trim(), accountType: f.accountType, currency: f.currency.toUpperCase(), openingBalance: normalizeMoney(f.openingBalance.trim()) });
        toast('ok', 'Account created.'); onDone();
      } catch (err) { setFormErr(errText(err)); } finally { setBusy(false); }
    });
  }
  return (
    <Modal title="New account" onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <Field id="a-name" label="Name" error={errs.name}><input id="a-name" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} autoFocus /></Field>
        <Field id="a-type" label="Type"><select id="a-type" value={f.accountType} onChange={(e) => setF({ ...f, accountType: e.target.value as AccountType })}>{ACCOUNT_TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}</select></Field>
        <div className="two">
          <Field id="a-cur" label="Currency" error={errs.currency} hint="Fixed after creation."><input id="a-cur" maxLength={3} value={f.currency} onChange={(e) => setF({ ...f, currency: e.target.value.toUpperCase() })} /></Field>
          <Field id="a-open" label="Opening balance" error={errs.openingBalance}><input id="a-open" inputMode="decimal" value={f.openingBalance} onChange={(e) => setF({ ...f, openingBalance: e.target.value })} /></Field>
        </div>
        {formErr && <div className="banner err" role="alert">{formErr}</div>}
        <div className="modal-foot"><button type="button" className="btn ghost" onClick={onClose}>Cancel</button><button className="btn primary" disabled={busy}>{busy && <Spinner />} Create account</button></div>
      </form>
    </Modal>
  );
}
