// Pure validation/payload logic for the transaction form, mirroring the backend rules in TransactionService.
import type { Account, Category, CreateTransactionBody, TxType } from './api';
import { compareMoney, formatMoney, normalizeMoney, validateAmount } from './money';

export interface TxFormState {
  type: TxType; description: string; category: string; amount: string; date: string; source: string; dest: string;
}

/** Backend: category type must be BOTH or equal the transaction type; a TRANSFER therefore needs BOTH. */
export const categoriesFor = (categories: Category[], type: TxType): Category[] =>
  categories.filter((c) => c.categoryType === 'BOTH' || c.categoryType === type);

/** The account whose currency the transaction must use: destination for income, otherwise the source. */
export const relevantAccount = (f: TxFormState, accounts: Account[]): Account | undefined =>
  accounts.find((a) => a.id === (f.type === 'INCOME' ? f.dest : f.source));

export function validateTransaction(f: TxFormState, accounts: Account[]): Record<string, string> {
  const e: Record<string, string> = {};
  const src = accounts.find((a) => a.id === f.source);
  const dst = accounts.find((a) => a.id === f.dest);
  if (!f.description.trim()) e.description = 'Description is required.';
  else if (f.description.trim().length > 255) e.description = 'Description cannot exceed 255 characters.';
  if (!f.category) e.category = 'Choose a category.';
  const amountErr = validateAmount(f.amount);
  if (amountErr) e.amount = amountErr;
  if (!f.date) e.date = 'Date is required.';
  if (f.type !== 'INCOME' && !f.source) e.source = 'Choose the source account.';
  if (f.type !== 'EXPENSE' && !f.dest) e.dest = 'Choose the destination account.';
  if (f.type === 'TRANSFER' && f.source && f.source === f.dest) e.dest = 'Source and destination must differ.';
  if (f.type === 'TRANSFER' && src && dst && src.currency !== dst.currency) e.dest = 'Transfers between different currencies are not supported.';
  // Friendly pre-check only; the backend re-checks under a lock. Credit cards may go negative.
  if (!e.amount && src && f.type !== 'INCOME' && src.accountType !== 'CREDIT_CARD' && compareMoney(src.currentBalance, f.amount.trim()) < 0)
    e.amount = `Insufficient funds. Available: ${formatMoney(src.currentBalance, src.currency)}.`;
  return e;
}

export function buildPayload(f: TxFormState, accounts: Account[]): CreateTransactionBody | null {
  const rel = relevantAccount(f, accounts);
  if (!rel) return null;
  return {
    transactionType: f.type, description: f.description.trim(), category: f.category, amount: normalizeMoney(f.amount.trim()),
    currency: rel.currency, transactionDate: f.date,
    ...(f.type !== 'INCOME' ? { sourceAccountId: f.source } : {}),
    ...(f.type !== 'EXPENSE' ? { destinationAccountId: f.dest } : {}),
  };
}
