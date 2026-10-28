import { useMemo, useRef, useState } from 'react';
import { Download, ShieldCheck, Upload } from 'lucide-react';
import { api, type ReconImport, type Transaction, type RowStatus, type StatementRow } from '../api';
import { isNegative } from '../money';
import { Badge, Empty, ErrorState, Field, Loading, PageHead, Spinner, errText, fmtDate, label, money, useLoad, useSubmitLock, useToast } from '../ui';

const MAX_BYTES = 1024 * 1024; // Spring's default multipart limit (not overridden in application.properties)
export const STATUS_HELP: Record<string, string> = {
  MATCHED: 'Exactly one FinTrack transaction has the same date, amount, currency and direction on this account.',
  UNMATCHED: 'No FinTrack transaction on this account matches the row. You may be missing a record.',
  DUPLICATE: 'The same date, description, amount, currency and reference already appeared earlier in this file.',
  MISMATCHED: 'Something is close but not right: several candidates, a different amount, or a currency different from the account.',
};
const TONE: Record<string, string> = { MATCHED: 'pos', UNMATCHED: 'neg', DUPLICATE: 'muted', MISMATCHED: 'warn' };
const TEMPLATE = 'date,description,amount,currency,reference\n2026-10-01,Salary credit,85000.00,INR,SAL-OCT\n2026-10-03,Grocery store,-1250.50,INR,POS-1182\n';

export default function Reconciliation() {
  const accounts = useLoad(api.accounts);
  const imports = useLoad(api.imports);
  const toast = useToast();
  const [accountId, setAccountId] = useState(''); const [file, setFile] = useState<File | null>(null);
  const [fileErr, setFileErr] = useState(''); const [formErr, setFormErr] = useState(''); const [busy, setBusy] = useState(false);
  const [selected, setSelected] = useState<ReconImport | null>(null);
  const input = useRef<HTMLInputElement>(null); const lock = useSubmitLock();

  function pick(f: File | undefined) {
    setFormErr('');
    if (!f) return;
    if (!f.name.toLowerCase().endsWith('.csv')) { setFile(null); return setFileErr('Only .csv files are supported.'); }
    if (f.size === 0) { setFile(null); return setFileErr('That file is empty.'); }
    if (f.size > MAX_BYTES) { setFile(null); return setFileErr('File is larger than 1 MB, the server’s default upload limit.'); }
    setFileErr(''); setFile(f);
  }
  async function upload() {
    if (!accountId) return setFormErr('Choose the account this statement belongs to.');
    if (!file) return setFileErr('Choose a CSV file first.');
    setBusy(true); setFormErr('');
    await lock.run(async () => {
      try {
        const res = await api.uploadStatement(accountId, file);
        toast('ok', `Imported ${res.totalRows} rows. No transactions were created and no balances changed.`); setFile(null); if (input.current) input.current.value = '';
        imports.reload(); setSelected(res);
      } catch (e) { setFormErr(errText(e, { 413: 'The file is larger than the server accepts (1 MB by default).' })); } finally { setBusy(false); }
    });
  }
  function template() {
    const url = URL.createObjectURL(new Blob([TEMPLATE], { type: 'text/csv' }));
    const a = document.createElement('a'); a.href = url; a.download = 'fintrack-statement-template.csv'; a.click(); URL.revokeObjectURL(url);
  }

  if (accounts.loading) return <Loading />;
  if (accounts.error || !accounts.data) return <ErrorState message={accounts.error ?? ''} onRetry={accounts.reload} />;
  const accName = (id: string) => accounts.data?.find((a) => a.id === id)?.name ?? 'Unknown account';

  return (
    <>
      <PageHead title="Reconciliation" sub="Compare a bank statement CSV with your FinTrack records.">
        <button className="btn ghost" onClick={template}><Download size={16} /> CSV template</button>
      </PageHead>
      <div className="banner info"><ShieldCheck size={18} /> Reconciliation only classifies statement rows. It never creates transactions, changes ledger entries or alters balances.</div>
      <section className="card">
        <h2>Upload a statement</h2>
        {accounts.data.length === 0 ? <p className="muted">Create an account first.</p> : (<>
          <Field id="r-acc" label="Account"><select id="r-acc" value={accountId} onChange={(e) => setAccountId(e.target.value)}><option value="">Select…</option>{accounts.data.map((a) => <option key={a.id} value={a.id}>{a.name} · {a.currency}</option>)}</select></Field>
          <div className={`drop${fileErr ? ' bad' : ''}`} onDragOver={(e) => e.preventDefault()} onDrop={(e) => { e.preventDefault(); pick(e.dataTransfer.files[0]); }}>
            <Upload size={22} /><p>{file ? <b>{file.name}</b> : 'Drag a .csv file here, or'} <button className="link" onClick={() => input.current?.click()}>browse</button></p>
            <input ref={input} type="file" accept=".csv,text/csv" hidden onChange={(e) => pick(e.target.files?.[0])} aria-label="CSV file" />
          </div>
          {fileErr && <small className="err">{fileErr}</small>}
          {formErr && <div className="banner err" role="alert">{formErr}</div>}
          <button className="btn primary" disabled={busy} onClick={upload}>{busy && <Spinner />} {busy ? 'Processing…' : 'Upload and reconcile'}</button>
        </>)}
        <details className="fmt"><summary>Required CSV format</summary>
          <ul><li>Header row with <code>date</code>, <code>description</code>, <code>amount</code>, <code>currency</code>; optional <code>reference</code>.</li>
            <li>Dates as <code>YYYY-MM-DD</code>. Currency as a three-letter code; it should match the account’s currency.</li>
            <li><b>Negative amounts are debits (money out); positive amounts are credits (money in).</b> Zero is not allowed; up to 4 decimals.</li>
            <li>Maximum 10,000 data rows. If any row is invalid the whole file is rejected with the row number.</li></ul></details>
      </section>

      <h2 className="sect">Status guide</h2>
      <div className="grid cols-2">{Object.entries(STATUS_HELP).map(([k, v]) => <div className="card" key={k}><Badge tone={TONE[k]}>{label(k)}</Badge><p>{v}</p></div>)}</div>

      <h2 className="sect">Import history</h2>
      {imports.loading ? <Loading /> : imports.error ? <ErrorState message={imports.error} onRetry={imports.reload} /> :
        !imports.data || imports.data.length === 0 ? <Empty title="No imports yet">Upload a statement to see results here.</Empty> : (
          <div className="card flush"><table className="table"><caption className="sr-only">Import history</caption><thead><tr><th>File</th><th>Account</th><th>Imported</th><th>Rows</th><th>Matched</th><th>Unmatched</th><th>Duplicate</th><th>Mismatched</th><th /></tr></thead><tbody>
            {imports.data.map((i) => (<tr key={i.id}><td data-l="File">{i.originalFilename}{i.status !== 'COMPLETED' && <Badge tone="warn">{label(i.status)}</Badge>}</td><td data-l="Account">{accName(i.accountId)}</td><td data-l="Imported">{fmtDate(i.createdAt)}</td>
              <td data-l="Rows">{i.totalRows}</td><td data-l="Matched">{i.matchedRows}</td><td data-l="Unmatched">{i.unmatchedRows}</td><td data-l="Duplicate">{i.duplicateRows}</td><td data-l="Mismatched">{i.mismatchedRows}</td>
              <td className="end"><button className="btn ghost sm" onClick={() => setSelected(i)}>View rows</button></td></tr>))}</tbody></table></div>)}
      {selected && <Rows imp={selected} />}
    </>
  );
}

function Rows({ imp }: { imp: ReconImport }) {
  const { data, error, loading, reload } = useLoad(() => api.importRows(imp.id), [imp.id]);
  // Optional enrichment: show the matched transaction's description instead of a bare id. Failure is non-fatal.
  const txs = useLoad(() => api.transactions().catch(() => [] as Transaction[]), [imp.id]);
  const txById = useMemo(() => new Map((txs.data ?? []).map((t) => [t.id, t])), [txs.data]);
  const [status, setStatus] = useState<RowStatus | ''>('');
  const rows = useMemo(() => (data ?? []).filter((r) => !status || r.status === status), [data, status]);
  const byId = useMemo(() => new Map((data ?? []).map((r) => [r.id, r])), [data]);
  const dupOf = (r: StatementRow) => (r.duplicateOfRowId ? `Duplicate of row ${byId.get(r.duplicateOfRowId)?.rowNumber ?? '?'}` : '');
  return (
    <section className="card" aria-live="polite">
      <div className="row"><h2>{imp.originalFilename}</h2>
        <select aria-label="Filter by status" value={status} onChange={(e) => setStatus(e.target.value as RowStatus | '')}>
          <option value="">All statuses</option>{(['MATCHED', 'UNMATCHED', 'DUPLICATE', 'MISMATCHED'] as RowStatus[]).map((s) => <option key={s} value={s}>{label(s)}</option>)}</select></div>
      {loading ? <Loading /> : error ? <ErrorState message={error} onRetry={reload} /> : rows.length === 0 ? <Empty title="No rows with this status" /> : (
        <div className="scroll"><table className="table"><thead><tr><th>#</th><th>Date</th><th>Description</th><th>Reference</th><th className="end">Amount</th><th>Status</th><th>Detail</th></tr></thead><tbody>
          {rows.map((r) => (<tr key={r.id}><td data-l="#">{r.rowNumber}</td><td data-l="Date">{fmtDate(r.statementDate)}</td><td data-l="Description">{r.description}</td><td data-l="Reference">{r.reference ?? '—'}</td>
            <td data-l="Amount" className={`end ${isNegative(r.amount) ? 'neg' : 'pos'}`}>{money(r.amount, r.currency)}</td><td data-l="Status"><Badge tone={TONE[r.status] ?? 'muted'}>{label(r.status)}</Badge></td>
            <td data-l="Detail">{r.matchedTransactionId ? `Matched: ${txById.get(r.matchedTransactionId)?.description ?? `transaction ${r.matchedTransactionId.slice(0, 8)}…`}` : dupOf(r) || '—'}</td></tr>))}</tbody></table></div>)}
    </section>
  );
}
