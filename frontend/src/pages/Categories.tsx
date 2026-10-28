import { useState, type FormEvent } from 'react';
import { Lock, Pencil, Plus, Trash2 } from 'lucide-react';
import { api, type Category, type CategoryType } from '../api';
import { Badge, Empty, ErrorState, Field, Loading, Modal, PageHead, Spinner, errText, label, useLoad, useSubmitLock, useToast } from '../ui';

export default function Categories() {
  const { data, error, loading, reload } = useLoad(api.categories);
  const toast = useToast();
  const [editing, setEditing] = useState<Category | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Category | null>(null);
  const [busy, setBusy] = useState(false); const lock = useSubmitLock();
  if (loading) return <Loading />;
  if (error || !data) return <ErrorState message={error ?? ''} onRetry={reload} />;

  async function remove() {
    if (!deleting) return; setBusy(true);
    await lock.run(async () => {
      try { await api.deleteCategory(deleting.id); toast('ok', 'Category deleted.'); setDeleting(null); reload(); }
      catch (e) { toast('err', errText(e, { 409: 'This category can’t be deleted because transactions use it.', 403: 'System categories can’t be changed.' })); setDeleting(null); }
      finally { setBusy(false); }
    });
  }
  return (
    <>
      <PageHead title="Categories" sub="System categories are fixed. Add your own for finer analysis.">
        <button className="btn primary" onClick={() => setEditing('new')}><Plus size={16} /> New category</button>
      </PageHead>
      {data.length === 0 ? <Empty title="No categories" /> : (
        <div className="card flush"><table className="table"><thead><tr><th>Name</th><th>Type</th><th>Origin</th><th /></tr></thead><tbody>
          {data.map((c) => (
            <tr key={c.id}><td data-l="Name">{c.name}</td><td data-l="Type"><Badge tone={c.categoryType === 'INCOME' ? 'pos' : c.categoryType === 'EXPENSE' ? 'neg' : 'muted'}>{label(c.categoryType)}</Badge></td>
              <td data-l="Origin">{c.system ? <span className="muted"><Lock size={14} /> System</span> : 'Custom'}</td>
              <td className="end">{!c.system && <><button className="icon-btn" aria-label={`Edit ${c.name}`} onClick={() => setEditing(c)}><Pencil size={16} /></button><button className="icon-btn" aria-label={`Delete ${c.name}`} onClick={() => setDeleting(c)}><Trash2 size={16} /></button></>}</td></tr>))}
        </tbody></table></div>)}
      {editing && <CategoryForm initial={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onDone={() => { setEditing(null); reload(); }} />}
      {deleting && <Modal title="Delete category?" onClose={() => setDeleting(null)}>
        <p>“{deleting.name}” will be removed. Categories used by transactions can’t be deleted.</p>
        <div className="modal-foot"><button className="btn ghost" onClick={() => setDeleting(null)}>Cancel</button><button className="btn danger" disabled={busy} onClick={remove}>{busy && <Spinner />} Delete</button></div></Modal>}
    </>
  );
}

function CategoryForm({ initial, onClose, onDone }: { initial: Category | null; onClose: () => void; onDone: () => void }) {
  const toast = useToast(); const lock = useSubmitLock();
  const [name, setName] = useState(initial?.name ?? ''); const [type, setType] = useState<CategoryType>(initial?.categoryType ?? 'EXPENSE');
  const [err, setErr] = useState(''); const [formErr, setFormErr] = useState(''); const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) return setErr('Category name is required.');
    if (name.trim().length > 80) return setErr('Category name cannot exceed 80 characters.');
    setErr(''); setFormErr(''); setBusy(true);
    await lock.run(async () => {
      try {
        const body = { name: name.trim(), categoryType: type };
        if (initial) await api.updateCategory(initial.id, body); else await api.createCategory(body);
        toast('ok', initial ? 'Category updated.' : 'Category created.'); onDone();
      } catch (x) {
        setFormErr(errText(x, { 409: 'That name already exists, or the type can’t change because transactions use this category.' }));
      } finally { setBusy(false); }
    });
  }
  return (
    <Modal title={initial ? 'Edit category' : 'New category'} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <Field id="c-name" label="Name" error={err}><input id="c-name" value={name} onChange={(e) => setName(e.target.value)} autoFocus /></Field>
        <Field id="c-type" label="Type" hint="Transfers can only use categories of type Both."><select id="c-type" value={type} onChange={(e) => setType(e.target.value as CategoryType)}><option value="EXPENSE">Expense</option><option value="INCOME">Income</option><option value="BOTH">Both</option></select></Field>
        {formErr && <div className="banner err" role="alert">{formErr}</div>}
        <div className="modal-foot"><button type="button" className="btn ghost" onClick={onClose}>Cancel</button><button className="btn primary" disabled={busy}>{busy && <Spinner />} Save</button></div>
      </form>
    </Modal>
  );
}
