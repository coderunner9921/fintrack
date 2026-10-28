import { cloneElement, createContext, useCallback, useContext, useEffect, useId, useRef, useState, type ReactElement, type ReactNode } from 'react';
import { AlertTriangle, CheckCircle2, Loader2, X } from 'lucide-react';
import { ApiError } from './api';
import { createKeyStore } from './idempotency';
import { formatMoney, type Money } from './money';

export function Logo({ compact = false, light = false }: { compact?: boolean; light?: boolean }) {
  return (
    <span className="logo" aria-label="FinTrack">
      <img src="/favicon.svg" alt="" width={34} height={34} />
      {!compact && <span className={light ? 'wordmark light' : 'wordmark'}>Fin<b>Track</b></span>}
    </span>
  );
}

// ---------- formatting ----------
// Exact, currency-code formatted (see money.ts). Amounts are decimal strings.
export const money = (amount: Money, currency: string): string => formatMoney(amount, currency);
// Local calendar date as YYYY-MM-DD (avoids the UTC shift of toISOString).
export function ymd(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
export function fmtDate(iso: string): string {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}
export const label = (s: string) => s.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
export function errText(e: unknown, byStatus: Record<number, string> = {}): string {
  if (e instanceof ApiError) return byStatus[e.status] ?? e.message;
  return 'Unexpected error. Please try again.';
}

// ---------- toasts ----------
type Toast = { id: number; kind: 'ok' | 'err'; text: string };
const ToastCtx = createContext<(kind: Toast['kind'], text: string) => void>(() => {});
export const useToast = () => useContext(ToastCtx);
export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Toast[]>([]);
  const next = useRef(1);
  const push = useCallback((kind: Toast['kind'], text: string) => {
    const id = next.current++;
    setItems((x) => [...x, { id, kind, text }]);
    window.setTimeout(() => setItems((x) => x.filter((t) => t.id !== id)), 5000);
  }, []);
  return (
    <ToastCtx.Provider value={push}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {items.map((t) => (
          <div key={t.id} className={`toast ${t.kind}`}>
            {t.kind === 'ok' ? <CheckCircle2 size={18} /> : <AlertTriangle size={18} />}<span>{t.text}</span>
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  );
}

// ---------- small components ----------
export const Spinner = () => <Loader2 className="spin" size={18} aria-hidden />;
export function Loading({ text = 'Loading…' }: { text?: string }) {
  return <div className="state" role="status"><Spinner /> {text}</div>;
}
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="state error" role="alert"><AlertTriangle size={20} /><div><b>Couldn’t load this.</b> {message}</div>
      {onRetry && <button className="btn ghost" onClick={onRetry}>Retry</button>}</div>
  );
}
export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return <div className="empty"><h3>{title}</h3><p>{children}</p></div>;
}
export function PageHead({ title, sub, children }: { title: string; sub?: string; children?: ReactNode }) {
  return <header className="pagehead"><div><h1>{title}</h1>{sub && <p>{sub}</p>}</div><div className="actions">{children}</div></header>;
}
export function Field({ id, label: l, error, hint, children }: { id: string; label: string; error?: string; hint?: string; children: ReactElement }) {
  const describedBy = error ? `${id}-err` : hint ? `${id}-hint` : undefined;
  return (
    <div className={`field${error ? ' has-error' : ''}`}>
      <label htmlFor={id}>{l}</label>
      {cloneElement(children, { 'aria-invalid': error ? true : undefined, 'aria-describedby': describedBy })}
      {hint && !error && <small id={`${id}-hint`}>{hint}</small>}
      {error && <small className="err" id={`${id}-err`} role="alert">{error}</small>}
    </div>
  );
}
const FOCUSABLE = 'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])';
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const box = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  const titleId = useId();
  // Move focus into the dialog, keep Tab inside it, close on Escape, and restore focus afterwards.
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const el = box.current;
    if (el && !el.contains(document.activeElement)) el.querySelector<HTMLElement>(FOCUSABLE)?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { closeRef.current(); return; }
      if (e.key !== 'Tab' || !el) return;
      const items = Array.from(el.querySelectorAll<HTMLElement>(FOCUSABLE));
      if (items.length === 0) return;
      const first = items[0], last = items[items.length - 1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('keydown', onKey); previous?.focus?.(); };
  }, []);
  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby={titleId} ref={box}>
        <div className="modal-head"><h2 id={titleId}>{title}</h2><button type="button" className="icon-btn" onClick={onClose} aria-label="Close dialog"><X size={18} /></button></div>
        {children}
      </div>
    </div>
  );
}
export function Badge({ tone, children }: { tone: string; children: ReactNode }) { return <span className={`badge ${tone}`}>{children}</span>; }

// Simple async loader hook
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let live = true; setLoading(true); setError(null);
    fn().then((d) => live && setData(d)).catch((e) => live && setError(errText(e))).finally(() => live && setLoading(false));
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);
  return { data, error, loading, reload: () => setTick((t) => t + 1) };
}

// One Idempotency-Key per logical request (see idempotency.ts for the rules).
export function useIdempotency() {
  const store = useRef(createKeyStore());
  return store.current;
}

// Synchronous double-submit guard: React state updates are async, so a fast double click could otherwise
// start two requests before `busy` re-renders.
export function useSubmitLock() {
  const locked = useRef(false);
  return {
    async run(fn: () => Promise<void>): Promise<void> {
      if (locked.current) return;
      locked.current = true;
      try { await fn(); } finally { locked.current = false; }
    },
  };
}
