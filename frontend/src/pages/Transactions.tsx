import { useMemo, useState, type FormEvent } from 'react';
import { Plus, Undo2 } from 'lucide-react';
import { ApiError, api, reversedOriginalId, type Account, type Category, type Transaction, type TxType } from '../api';
import { compareMoney } from '../money';
import { buildPayload, categoriesFor, relevantAccount, validateTransaction, type TxFormState } from '../txForm';
import { Badge, Empty, ErrorState, Field, Loading, Modal, PageHead, Spinner, errText, fmtDate, label, money, useIdempotency, useLoad, useSubmitLock, useToast, ymd } from '../ui';

export default function Transactions() {
  const { data, error, loading, reload } = useLoad(async () => {
    const [txs, accounts, categories] = await Promise.all([api.transactions(), api.accounts(), api.categories()]);
    return { txs, accounts, categories };
  });
  const [creating, setCreating] = useState(false);
  const [reversing, setReversing] = useState<Transaction | null>(null);
  const [f, setF] = useState({ q: '', type: '', account: '', from: '', to: '', sort: 'date-desc' });
  const [limit, setLimit] = useState(50);

  const reversed = useMemo(() => new Set((data?.txs ?? []).map((t) => reversedOriginalId(t)).filter(Boolean) as string[]), [data]);
  const byId = useMemo(() => new Map((data?.txs ?? []).map((t) => [t.id, t])), [data]);
  const rows = useMemo(() => {
    if (!data) return [];
    const q = f.q.trim().toLowerCase();
    return data.txs
      .filter((t) => (!q || t.description.toLowerCase().includes(q) || t.category.toLowerCase().includes(q))
        && (!f.type || t.transactionType === f.type)
        && (!f.account || t.sourceAccountId === f.account || t.destinationAccountId === f.account)
        && (!f.from || t.transactionDate >= f.from) && (!f.to || t.transactionDate <= f.to))
      .sort((a, b) => {
        switch (f.sort) {
          case 'date-asc': return a.transactionDate.localeCompare(b.transactionDate) || a.createdAt.localeCompare(b.createdAt);
          case 'amt-desc': return compareMoney(b.amount, a.amount);
          case 'amt-asc': return compareMoney(a.amount, b.amount);
          default: return b.transactionDate.localeCompare(a.transactionDate) || b.createdAt.localeCompare(a.createdAt);
        }
      });
  }, [data, f]);

  if (loading) return <Loading />;
  if (error || !data) return <ErrorState message={error ?? ''} onRetry={reload} />;
  const { accounts, categories } = data;
  const acct = (id: string | null) => accounts.find((a) => a.id === id)?.name;
  const badRange = !!f.from && !!f.to && f.from > f.to;

  return (
    <>
      <PageHead title="Transactions" sub="Every entry is posted to the double-entry ledger. Mistakes are reversed, never deleted.">
        <button className="btn primary" onClick={() => setCreating(true)} disabled={accounts.filter((a) => a.active).length === 0}><Plus size={16} /> New transaction</button>
      </PageHead>
      {accounts.length === 0 && <div className="banner warn">Create an account first — transactions need at least one.</div>}
      <div className="filters card" role="search" aria-label="Filter transactions">
        <input aria-label="Search description or category" placeholder="Search description or category" value={f.q} onChange={(e) => setF({ ...f, q: e.target.value })} />
        <select aria-label="Type" value={f.type} onChange={(e) => setF({ ...f, type: e.target.value })}><option value="">All types</option>{['INCOME', 'EXPENSE', 'TRANSFER', 'REVERSAL'].map((t) => <option key={t} value={t}>{label(t)}</option>)}</select>
        <select aria-label="Account" value={f.account} onChange={(e) => setF({ ...f, account: e.target.value })}><option value="">All accounts</option>{accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}</select>
        <input aria-label="From date" type="date" value={f.from} onChange={(e) => setF({ ...f, from: e.target.value })} />
        <input aria-label="To date" type="date" value={f.to} onChange={(e) => setF({ ...f, to: e.target.value })} />
        <select aria-label="Sort" value={f.sort} onChange={(e) => setF({ ...f, sort: e.target.value })}><option value="date-desc">Newest first</option><option value="date-asc">Oldest first</option><option value="amt-desc">Largest amount</option><option value="amt-asc">Smallest amount</option></select>
      </div>
      {badRange && <div className="banner warn" role="status">The “from” date is after the “to” date, so nothing can match.</div>}
      {data.txs.length === 0 ? <Empty title="No transactions yet">Record your first income, expense or transfer.</Empty>
        : rows.length === 0 ? <Empty title="Nothing matches those filters" /> : (
          <div className="card flush"><table className="table"><caption className="sr-only">Transactions</caption><thead><tr><th scope="col">Date</th><th scope="col">Description</th><th scope="col">Type</th><th scope="col">Accounts</th><th scope="col" className="end">Amount</th><th scope="col"><span className="sr-only">Actions</span></th></tr></thead><tbody>
            {rows.slice(0, limit).map((t) => {
              const isRev = t.transactionType === 'REVERSAL';
              const original = isRev ? byId.get(reversedOriginalId(t) ?? '') : undefined;
              return (
                <tr key={t.id}>
                  <td data-l="Date">{fmtDate(t.transactionDate)}</td>
                  <td data-l="Description"><b>{isRev ? 'Reversal' : t.description}</b><small>{isRev ? (original ? `Reverses “${original.description}” (${fmtDate(original.transactionDate)})` : t.description) : t.category}</small></td>
                  <td data-l="Type"><Badge tone={isRev ? 'warn' : t.transactionType === 'INCOME' ? 'pos' : t.transactionType === 'EXPENSE' ? 'neg' : 'muted'}>{label(t.transactionType)}</Badge>{reversed.has(t.id) && <Badge tone="warn">Reversed</Badge>}</td>
                  <td data-l="Accounts">{[acct(t.sourceAccountId), acct(t.destinationAccountId)].filter(Boolean).join(' → ') || '—'}</td>
                  <td data-l="Amount" className="end">{money(t.amount, t.currency)}</td>
                  <td className="end">{!isRev && !reversed.has(t.id) && <button className="btn ghost sm" onClick={() => setReversing(t)} aria-label={`Reverse ${t.description}`}><Undo2 size={14} /> Reverse</button>}</td>
                </tr>);
            })}</tbody></table>
            {rows.length > limit && <button className="btn ghost block" onClick={() => setLimit(limit + 50)}>Show more ({rows.length - limit} remaining)</button>}</div>)}
      {creating && <TxForm accounts={accounts} categories={categories} onClose={() => setCreating(false)} onDone={() => { setCreating(false); reload(); }} />}
      {reversing && <ReverseDialog tx={reversing} onClose={() => setReversing(null)} onDone={() => { setReversing(null); reload(); }} />}
    </>
  );
}

function ReverseDialog({ tx, onClose, onDone }: { tx: Transaction; onClose: () => void; onDone: () => void }) {
  const toast = useToast(); const lock = useSubmitLock(); const [busy, setBusy] = useState(false);
  async function go() {
    setBusy(true);
    await lock.run(async () => {
      try { await api.reverseTransaction(tx.id); toast('ok', 'Transaction reversed.'); onDone(); }
      catch (e) { toast('err', errText(e, { 400: 'This can’t be reversed — it may already be reversed, or the account no longer holds enough funds to undo it.' })); onClose(); }
      finally { setBusy(false); }
    });
  }
  return (
    <Modal title="Reverse this transaction?" onClose={onClose}>
      <p><b>{tx.description}</b> · {money(tx.amount, tx.currency)}</p>
      <p>A new <b>reversal</b> entry dated today will post the opposite ledger entries. The original stays in your history and can’t be reversed twice. Income and transfers can only be reversed if the receiving account still holds enough funds.</p>
      <div className="modal-foot"><button className="btn ghost" onClick={onClose}>Cancel</button><button className="btn danger" disabled={busy} onClick={go}>{busy && <Spinner />} Reverse</button></div>
    </Modal>
  );
}

function TxForm({ accounts, categories, onClose, onDone }: { accounts: Account[]; categories: Category[]; onClose: () => void; onDone: () => void }) {
  const toast = useToast(); const idem = useIdempotency(); const lock = useSubmitLock();
  const active = accounts.filter((a) => a.active);
  const [f, setF] = useState<TxFormState>({ type: 'EXPENSE', description: '', category: '', amount: '', date: ymd(new Date()), source: '', dest: '' });
  const [errs, setErrs] = useState<Record<string, string>>({}); const [formErr, setFormErr] = useState(''); const [busy, setBusy] = useState(false);

  const cats = categoriesFor(categories, f.type);
  const src = active.find((a) => a.id === f.source);
  const rel = relevantAccount(f, active);
  const destOptions = f.type === 'TRANSFER' ? active.filter((a) => a.id !== f.source && (!src || a.currency === src.currency)) : active;
  function set<K extends keyof TxFormState>(k: K, v: TxFormState[K]) { setF((p) => ({ ...p, [k]: v })); }

  async function submit(e: FormEvent) {
    e.preventDefault();
    const v = validateTransaction(f, active);
    setErrs(v); setFormErr(''); if (Object.keys(v).length) return;
    const payload = buildPayload(f, active); if (!payload) return;
    setBusy(true);
    await lock.run(async () => {
      try {
        // Same payload on retry => same Idempotency-Key => the backend replays instead of double-posting.
        await api.createTransaction(payload, idem.keyFor(payload));
        idem.reset(); toast('ok', 'Transaction recorded.'); onDone();
      } catch (x) {
        setFormErr(errText(x, { 400: 'The server rejected this transaction. Check that the amount is available in the account, the currency matches the account, and the category fits the type.' })
          + (x instanceof ApiError && x.status === 0 ? ' If you retry the same transaction it will not be recorded twice.' : ''));
      } finally { setBusy(false); }
    });
  }

  return (
    <Modal title="New transaction" onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <div className="seg" role="radiogroup" aria-label="Transaction type">
          {(['EXPENSE', 'INCOME', 'TRANSFER'] as TxType[]).map((t) => (
            <button type="button" key={t} role="radio" aria-checked={f.type === t} className={f.type === t ? 'on' : ''}
              onClick={() => setF((p) => ({ ...p, type: t, category: '', dest: t === 'EXPENSE' ? '' : p.dest, source: t === 'INCOME' ? '' : p.source }))}>{label(t)}</button>))}
        </div>
        {f.type !== 'INCOME' && <Field id="t-src" label="From account" error={errs.source}><select id="t-src" value={f.source} onChange={(e) => setF((p) => ({ ...p, source: e.target.value, dest: p.dest === e.target.value ? '' : p.dest }))}><option value="">Select…</option>{active.map((a) => <option key={a.id} value={a.id}>{a.name} · {money(a.currentBalance, a.currency)}</option>)}</select></Field>}
        {f.type !== 'EXPENSE' && <Field id="t-dst" label={f.type === 'INCOME' ? 'Deposit to account' : 'To account'} error={errs.dest} hint={f.type === 'TRANSFER' ? 'Transfers must stay within one currency.' : undefined}><select id="t-dst" value={f.dest} onChange={(e) => set('dest', e.target.value)}><option value="">Select…</option>{destOptions.map((a) => <option key={a.id} value={a.id}>{a.name} · {a.currency}</option>)}</select></Field>}
        <Field id="t-desc" label="Description" error={errs.description}><input id="t-desc" value={f.description} onChange={(e) => set('description', e.target.value)} /></Field>
        <div className="two">
          <Field id="t-amt" label={`Amount${rel ? ` (${rel.currency})` : ''}`} error={errs.amount}><input id="t-amt" inputMode="decimal" value={f.amount} onChange={(e) => set('amount', e.target.value)} /></Field>
          <Field id="t-date" label="Date" error={errs.date}><input id="t-date" type="date" value={f.date} onChange={(e) => set('date', e.target.value)} /></Field>
        </div>
        <Field id="t-cat" label="Category" error={errs.category} hint={f.type === 'TRANSFER' ? 'Only categories of type Both can be used for transfers.' : undefined}>
          <select id="t-cat" value={f.category} onChange={(e) => set('category', e.target.value)}><option value="">Select…</option>{cats.map((c) => <option key={c.id} value={c.name}>{c.name}</option>)}</select></Field>
        {cats.length === 0 && <div className="banner warn">No category fits this transaction type. Create a custom category of type {f.type === 'TRANSFER' ? 'Both' : label(f.type)} first.</div>}
        {formErr && <div className="banner err" role="alert">{formErr}</div>}
        <div className="modal-foot"><button type="button" className="btn ghost" onClick={onClose}>Cancel</button><button className="btn primary" disabled={busy}>{busy && <Spinner />} Record</button></div>
      </form>
    </Modal>
  );
}
