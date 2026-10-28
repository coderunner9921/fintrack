import { describe, expect, it } from 'vitest';
import type { Account, Category } from './api';
import { buildPayload, categoriesFor, validateTransaction, type TxFormState } from './txForm';

const acct = (o: Partial<Account>): Account => ({ id: 'a1', name: 'Main', accountType: 'CURRENT', currency: 'INR', openingBalance: '0.00', active: true, createdAt: '', updatedAt: '', currentBalance: '100.00', ...o });
const cat = (name: string, categoryType: Category['categoryType']): Category => ({ id: name, name, categoryType, system: false, createdAt: '' });
const base: TxFormState = { type: 'EXPENSE', description: 'Lunch', category: 'Food', amount: '10.00', date: '2026-10-01', source: 'a1', dest: '' };

describe('categoriesFor (backend rule: BOTH or same type; transfers need BOTH)', () => {
  const cats = [cat('Food', 'BOTH'), cat('Rent', 'EXPENSE'), cat('Pay', 'INCOME')];
  it('filters by type', () => {
    expect(categoriesFor(cats, 'EXPENSE').map((c) => c.name)).toEqual(['Food', 'Rent']);
    expect(categoriesFor(cats, 'INCOME').map((c) => c.name)).toEqual(['Food', 'Pay']);
    expect(categoriesFor(cats, 'TRANSFER').map((c) => c.name)).toEqual(['Food']);
  });
});

describe('validateTransaction', () => {
  const accounts = [acct({}), acct({ id: 'a2', name: 'USD', currency: 'USD' }), acct({ id: 'a3', name: 'Card', accountType: 'CREDIT_CARD', currentBalance: '0.00' })];
  it('accepts a valid expense', () => { expect(validateTransaction(base, accounts)).toEqual({}); });
  it('rejects zero, malformed and over-precise amounts', () => {
    expect(validateTransaction({ ...base, amount: '0' }, accounts).amount).toMatch(/greater than zero/);
    expect(validateTransaction({ ...base, amount: '1.23456' }, accounts).amount).toMatch(/4 decimal/);
  });
  it('flags insufficient funds using exact comparison', () => {
    expect(validateTransaction({ ...base, amount: '100.01' }, accounts).amount).toMatch(/Insufficient funds/);
    expect(validateTransaction({ ...base, amount: '100.00' }, accounts).amount).toBeUndefined();
  });
  it('does not block credit-card spending', () => {
    expect(validateTransaction({ ...base, source: 'a3', amount: '500' }, accounts)).toEqual({});
  });
  it('requires the right accounts per type', () => {
    expect(validateTransaction({ ...base, source: '' }, accounts).source).toBeDefined();
    expect(validateTransaction({ ...base, type: 'INCOME', source: '', dest: '' }, accounts).dest).toBeDefined();
  });
  it('rejects same-account and cross-currency transfers', () => {
    const t: TxFormState = { ...base, type: 'TRANSFER', dest: 'a1' };
    expect(validateTransaction(t, accounts).dest).toMatch(/differ/);
    expect(validateTransaction({ ...t, dest: 'a2' }, accounts).dest).toMatch(/different currencies/);
  });
  it('requires description and category', () => {
    const e = validateTransaction({ ...base, description: '  ', category: '' }, accounts);
    expect(e.description).toBeDefined(); expect(e.category).toBeDefined();
  });
});

describe('buildPayload', () => {
  const accounts = [acct({}), acct({ id: 'a9', name: 'Savings', currency: 'INR' })];
  it('expense uses source only and the account currency', () => {
    expect(buildPayload(base, accounts)).toEqual({ transactionType: 'EXPENSE', description: 'Lunch', category: 'Food', amount: '10.00', currency: 'INR', transactionDate: '2026-10-01', sourceAccountId: 'a1' });
  });
  it('income uses destination only', () => {
    const p = buildPayload({ ...base, type: 'INCOME', source: '', dest: 'a9', amount: '5' }, accounts);
    expect(p?.destinationAccountId).toBe('a9'); expect(p?.sourceAccountId).toBeUndefined(); expect(p?.amount).toBe('5.00');
  });
  it('transfer uses both', () => {
    const p = buildPayload({ ...base, type: 'TRANSFER', dest: 'a9' }, accounts);
    expect(p?.sourceAccountId).toBe('a1'); expect(p?.destinationAccountId).toBe('a9');
  });
});
