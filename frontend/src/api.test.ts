import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ApiError, api, reversedOriginalId, sessionStore, setUnauthorizedHandler, type Transaction } from './api';

type Call = { url: string; init: RequestInit };
let calls: Call[] = [];
const realFetch = globalThis.fetch;
function mockFetch(status: number, body: string | null, contentType = 'application/json') {
  calls = [];
  globalThis.fetch = (async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    return new Response(body, { status, headers: { 'Content-Type': contentType } });
  }) as typeof fetch;
}
const memory = new Map<string, string>();
beforeEach(() => {
  if (typeof globalThis.sessionStorage === 'undefined') {
    (globalThis as { sessionStorage: Storage }).sessionStorage = { getItem: (k) => memory.get(k) ?? null, setItem: (k, v) => void memory.set(k, v), removeItem: (k) => void memory.delete(k), clear: () => memory.clear(), key: () => null, length: 0 } as Storage;
  }
  sessionStorage.clear(); setUnauthorizedHandler(() => {});
});
afterEach(() => { globalThis.fetch = realFetch; });
const signIn = () => sessionStore.write({ token: 'tok123', email: 'a@b.co', expiresAt: Date.now() + 60000 });
const hdr = (c: Call) => c.init.headers as Record<string, string>;

describe('api client', () => {
  it('attaches the bearer token to authenticated requests only', async () => {
    signIn(); mockFetch(200, '[]');
    await api.accounts();
    expect(hdr(calls[0]).Authorization).toBe('Bearer tok123');
    mockFetch(200, '{"token":"t","tokenType":"Bearer","expiresIn":900000}');
    await api.login({ email: 'a@b.co', password: 'x' });
    expect(hdr(calls[0]).Authorization).toBeUndefined();
  });
  it('keeps money exact when parsing responses', async () => {
    signIn(); mockFetch(200, '[{"id":"1","currentBalance":0.10,"openingBalance":12345678901234567.8901}]');
    const [a] = await api.accounts();
    expect(a.currentBalance).toBe('0.10');
    expect(a.openingBalance).toBe('12345678901234567.8901');
  });
  it('sends the Idempotency-Key header and amount as a JSON number', async () => {
    signIn(); mockFetch(201, '{"id":"t1","amount":10.00}');
    await api.createTransaction({ transactionType: 'EXPENSE', description: 'x', category: 'Food', amount: '10.00', currency: 'INR', transactionDate: '2026-10-01', sourceAccountId: 's' }, 'key-abc');
    expect(hdr(calls[0])['Idempotency-Key']).toBe('key-abc');
    expect(calls[0].url).toBe('/api/transactions');
    expect(String(calls[0].init.body)).toContain('"amount":10.00,');
  });
  it('401 on an authenticated call clears the session and notifies the app', async () => {
    signIn(); let notified = 0; setUnauthorizedHandler(() => { notified++; });
    mockFetch(401, '{"status":401}');
    await expect(api.accounts()).rejects.toThrow(/sign in again/i);
    expect(sessionStore.read()).toBeNull(); expect(notified).toBe(1);
  });
  it('401 on login (wrong password) does not trigger the session-expired flow', async () => {
    let notified = 0; setUnauthorizedHandler(() => { notified++; });
    mockFetch(401, '{"status":401}');
    await expect(api.login({ email: 'a@b.co', password: 'bad' })).rejects.toBeInstanceOf(ApiError);
    expect(notified).toBe(0);
  });
  it('403 keeps the session and says permission is missing', async () => {
    signIn(); let notified = 0; setUnauthorizedHandler(() => { notified++; });
    mockFetch(403, '{"status":403}');
    await expect(api.accounts()).rejects.toThrow(/permission/i);
    expect(sessionStore.read()).not.toBeNull(); expect(notified).toBe(0);
  });
  it('prefers a server-provided error message (reconciliation returns {"error": ...})', async () => {
    signIn(); mockFetch(400, '{"error":"Row 3: amount must not be zero"}');
    await expect(api.imports()).rejects.toThrow('Row 3: amount must not be zero');
  });
  it('does not expose generic reason phrases or 5xx bodies', async () => {
    signIn(); mockFetch(400, '{"error":"Bad Request","status":400}');
    await expect(api.accounts()).rejects.toThrow(/check the values/i);
    mockFetch(500, '{"message":"java.lang.NullPointerException at com.fintrack..."}');
    await expect(api.accounts()).rejects.toThrow(/server had a problem/i);
  });
  it('maps a network failure to status 0', async () => {
    signIn(); globalThis.fetch = (async () => { throw new TypeError('Failed to fetch'); }) as typeof fetch;
    const err = await api.accounts().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError); expect(err.status).toBe(0); expect(err.message).toMatch(/Cannot reach/);
  });
  it('uploads multipart without a manual Content-Type', async () => {
    signIn(); mockFetch(201, '{"id":"i1"}');
    await api.uploadStatement('acc-1', new File(['date,description,amount,currency\n'], 's.csv', { type: 'text/csv' }));
    expect(calls[0].url).toBe('/api/reconciliation/imports?accountId=acc-1');
    expect(hdr(calls[0])['Content-Type']).toBeUndefined();
    expect(calls[0].init.body instanceof FormData).toBe(true);
  });
});

describe('reversedOriginalId', () => {
  const tx = (o: Partial<Transaction>): Transaction => ({ id: 'r', transactionType: 'REVERSAL', description: '', category: 'Food', amount: '1.00', currency: 'INR', transactionDate: '2026-10-01', sourceAccountId: null, destinationAccountId: null, createdAt: '', ...o });
  const id = '123e4567-e89b-12d3-a456-426614174000';
  it('prefers the exact API field', () => { expect(reversedOriginalId(tx({ reversalOfTransactionId: id, description: 'anything' }))).toBe(id); });
  it('falls back to the generated description for older backends', () => { expect(reversedOriginalId(tx({ description: `Reversal of transaction ${id}` }))).toBe(id); });
  it('is null for ordinary transactions', () => { expect(reversedOriginalId(tx({ transactionType: 'EXPENSE' }))).toBeNull(); });
});
