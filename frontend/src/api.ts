import { quoteMoneyNumbers, unquoteMoneyStrings, type Money } from './money';

// Typed API client. Every route/field below was taken from the Spring Boot controllers and DTOs.
// Base URL is '' by default, so requests go to /api/... and the Vite dev proxy forwards them.

export type AccountType = 'CASH' | 'SAVINGS' | 'CURRENT' | 'CREDIT_CARD' | 'INVESTMENT' | 'OTHER';
export const ACCOUNT_TYPES: AccountType[] = ['CASH', 'SAVINGS', 'CURRENT', 'CREDIT_CARD', 'INVESTMENT', 'OTHER'];
export type TxType = 'INCOME' | 'EXPENSE' | 'TRANSFER';
export type CategoryType = 'INCOME' | 'EXPENSE' | 'BOTH';
export type RowStatus = 'MATCHED' | 'UNMATCHED' | 'DUPLICATE' | 'MISMATCHED' | 'PENDING';

export interface Account {
  id: string; name: string; accountType: AccountType; currency: string;
  openingBalance: Money; active: boolean; createdAt: string; updatedAt: string; currentBalance: Money;
}
export interface CreateAccountBody { name: string; accountType: AccountType; currency: string; openingBalance: Money }
export interface Category { id: string; name: string; categoryType: CategoryType; system: boolean; createdAt: string }
export interface CategoryBody { name: string; categoryType: CategoryType }
export interface Transaction {
  id: string; transactionType: TxType | 'REVERSAL'; description: string; category: string;
  amount: Money; currency: string; transactionDate: string;
  sourceAccountId: string | null; destinationAccountId: string | null; createdAt: string;
  // Present once the backend exposes TransactionResponse.getReversalOfTransactionId() (added in this version).
  reversalOfTransactionId?: string | null;
}
export interface CreateTransactionBody {
  transactionType: TxType; description: string; category: string; amount: Money; currency: string;
  transactionDate: string; sourceAccountId?: string; destinationAccountId?: string;
}
export interface CategoryExpense { categoryName: string; total: Money }
export interface CurrencyAnalytics {
  currency: string; totalIncome: Money; totalExpenses: Money; netCashFlow: Money;
  incomeTransactionCount: number; expenseTransactionCount: number; expensesByCategory: CategoryExpense[];
}
export interface AnalyticsSummary { from: string; to: string; currencies: CurrencyAnalytics[] }
export interface ReconImport {
  id: string; accountId: string; originalFilename: string; status: 'PROCESSING' | 'COMPLETED' | 'FAILED';
  totalRows: number; matchedRows: number; unmatchedRows: number; duplicateRows: number; mismatchedRows: number;
  createdAt: string; completedAt: string | null;
}
export interface StatementRow {
  id: string; rowNumber: number; statementDate: string; description: string; amount: Money; currency: string;
  reference: string | null; status: RowStatus; matchedTransactionId: string | null; duplicateOfRowId: string | null;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

const BASE = ((import.meta.env?.VITE_API_BASE_URL as string | undefined) ?? '').replace(/\/$/, '');
const KEY = 'fintrack.session';

export interface Session { token: string; email: string; expiresAt: number; fullName?: string }
export const sessionStore = {
  read(): Session | null {
    try {
      const s = JSON.parse(sessionStorage.getItem(KEY) ?? 'null') as Session | null;
      return s && s.expiresAt > Date.now() ? s : null;
    } catch { return null; }
  },
  write(s: Session) { sessionStorage.setItem(KEY, JSON.stringify(s)); },
  clear() { sessionStorage.removeItem(KEY); },
};

let onUnauthorized: () => void = () => {};
export const setUnauthorizedHandler = (fn: () => void) => { onUnauthorized = fn; };

// The backend has no global exception handler, so most error bodies are Spring's default
// ({timestamp,status,error,path}) without a message. Only reconciliation returns {"error": "..."}.
// We use a server message when one exists and otherwise fall back to a status-based message.
function fallback(status: number): string {
  if (status === 400) return 'The server rejected the request. Please check the values and try again.';
  if (status === 401) return 'Your session is missing or has expired. Please sign in again.';
  if (status === 403) return 'You do not have permission to do that.';
  if (status === 404) return 'The requested item was not found.';
  if (status === 409) return 'This conflicts with existing data.';
  if (status === 413) return 'The file is too large for the server.';
  if (status >= 500) return 'The server had a problem. Please try again shortly.';
  return 'Something went wrong.';
}

async function request<T>(path: string, opts: { method?: string; body?: unknown; form?: FormData; headers?: Record<string, string>; auth?: boolean; timeoutMs?: number } = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...opts.headers };
  const session = sessionStore.read();
  if (opts.auth !== false && session) headers.Authorization = `Bearer ${session.token}`;
  let body: BodyInit | undefined;
  if (opts.form) body = opts.form; // browser sets the multipart boundary itself
  else if (opts.body !== undefined) { headers['Content-Type'] = 'application/json'; body = unquoteMoneyStrings(JSON.stringify(opts.body)); }

  let res: Response;
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), opts.timeoutMs ?? 30000);
  try {
    res = await fetch(`${BASE}${path}`, { method: opts.method ?? 'GET', headers, body, signal: ctrl.signal });
  } catch (e) {
    // status 0 = no response received. For writes the outcome is unknown (the server may have processed it).
    if (ctrl.signal.aborted) throw new ApiError(0, 'The server took too long to respond.');
    throw new ApiError(0, 'Cannot reach the FinTrack server. Is the backend running on port 8083?');
  } finally { clearTimeout(timer); }
  if (res.status === 401 && opts.auth !== false) { sessionStore.clear(); onUnauthorized(); }
  if (!res.ok) {
    let msg = '';
    try {
      const data = (await res.json()) as { error?: unknown; message?: unknown };
      if (res.status < 500) {
        if (typeof data.error === 'string' && !/^(Bad Request|Unauthorized|Forbidden|Not Found|Conflict)$/.test(data.error)) msg = data.error;
        else if (typeof data.message === 'string') msg = data.message;
      }
    } catch { /* body not JSON */ }
    throw new ApiError(res.status, msg || fallback(res.status));
  }
  if (res.status === 204) return undefined as T;
  // Money fields are quoted before parsing so they stay exact decimal strings (see money.ts).
  return JSON.parse(quoteMoneyNumbers(await res.text())) as T;
}

export const api = {
  register: (b: { fullName: string; email: string; password: string }) =>
    request<{ id: string; fullName: string; email: string; createdAt: string }>('/api/auth/register', { method: 'POST', body: b, auth: false }),
  login: (b: { email: string; password: string }) =>
    request<{ token: string; tokenType: string; expiresIn: number }>('/api/auth/login', { method: 'POST', body: b, auth: false }),
  accounts: () => request<Account[]>('/api/accounts'),
  createAccount: (b: CreateAccountBody) => request<Account>('/api/accounts', { method: 'POST', body: b }),
  categories: () => request<Category[]>('/api/categories'),
  createCategory: (b: CategoryBody) => request<Category>('/api/categories', { method: 'POST', body: b }),
  updateCategory: (id: string, b: CategoryBody) => request<Category>(`/api/categories/${id}`, { method: 'PUT', body: b }),
  deleteCategory: (id: string) => request<void>(`/api/categories/${id}`, { method: 'DELETE' }),
  transactions: () => request<Transaction[]>('/api/transactions'),
  // Idempotency-Key header is mandatory on this endpoint.
  createTransaction: (b: CreateTransactionBody, idempotencyKey: string) =>
    request<Transaction>('/api/transactions', { method: 'POST', body: b, headers: { 'Idempotency-Key': idempotencyKey } }),
  reverseTransaction: (id: string) => request<Transaction>(`/api/transactions/${id}/reverse`, { method: 'POST' }),
  analytics: (from: string, to: string) =>
    request<AnalyticsSummary>(`/api/analytics/summary?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  imports: () => request<ReconImport[]>('/api/reconciliation/imports'),
  importRows: (id: string) => request<StatementRow[]>(`/api/reconciliation/imports/${id}/rows`),
  uploadStatement: (accountId: string, file: File) => {
    const form = new FormData();
    form.append('file', file); // multipart field names: "file" (part) and "accountId" (query param)
    return request<ReconImport>(`/api/reconciliation/imports?accountId=${encodeURIComponent(accountId)}`, { method: 'POST', form, timeoutMs: 120000 });
  },
};

// Preferred: reversalOfTransactionId from the API. Fallback for older backends: the generated description
// "Reversal of transaction <uuid>".
export function reversedOriginalId(t: Transaction): string | null {
  if (t.transactionType !== 'REVERSAL') return null;
  if (t.reversalOfTransactionId) return t.reversalOfTransactionId; // exact, when the backend provides it
  return /^Reversal of transaction ([0-9a-f-]{36})$/i.exec(t.description)?.[1] ?? null;
}
