import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError, api, type Category, type Transaction } from '../api';
import { account, renderAt, signIn } from './helpers';

const food: Category = { id: 'c1', name: 'Food', categoryType: 'BOTH', system: true, createdAt: '' };
const created: Transaction = { id: 't1', transactionType: 'EXPENSE', description: 'Lunch', category: 'Food', amount: '10.00', currency: 'INR', transactionDate: '2026-10-01', sourceAccountId: 'a1', destinationAccountId: null, createdAt: '' };

beforeEach(() => {
  sessionStorage.clear(); signIn();
  vi.spyOn(api, 'accounts').mockResolvedValue([account()]);
  vi.spyOn(api, 'categories').mockResolvedValue([food]);
  vi.spyOn(api, 'transactions').mockResolvedValue([]);
});

async function openFormAndFill(amount: string) {
  renderAt('/app/transactions');
  await userEvent.click(await screen.findByRole('button', { name: /new transaction/i }));
  const dialog = screen.getByRole('dialog');
  await userEvent.selectOptions(within(dialog).getByLabelText('From account'), 'a1');
  await userEvent.type(within(dialog).getByLabelText('Description'), 'Lunch');
  await userEvent.type(within(dialog).getByLabelText(/Amount/), amount);
  await userEvent.selectOptions(within(dialog).getByLabelText('Category'), 'Food');
  return dialog;
}

describe('transaction form', () => {
  it('submits an expense with the exact payload and an Idempotency-Key', async () => {
    const create = vi.spyOn(api, 'createTransaction').mockResolvedValue(created);
    const dialog = await openFormAndFill('10');
    await userEvent.click(within(dialog).getByRole('button', { name: /record/i }));
    await vi.waitFor(() => expect(create).toHaveBeenCalledTimes(1));
    const [body, key] = create.mock.calls[0];
    expect(body).toMatchObject({ transactionType: 'EXPENSE', amount: '10.00', currency: 'INR', sourceAccountId: 'a1', category: 'Food' });
    expect(key).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('reuses the same key when the user retries the same request after a lost response', async () => {
    const create = vi.spyOn(api, 'createTransaction')
      .mockRejectedValueOnce(new ApiError(0, 'Cannot reach the FinTrack server.'))
      .mockResolvedValueOnce(created);
    const dialog = await openFormAndFill('10');
    await userEvent.click(within(dialog).getByRole('button', { name: /record/i }));
    expect(await screen.findByText(/will not be recorded twice/)).toBeTruthy();
    await userEvent.click(within(dialog).getByRole('button', { name: /record/i }));
    await vi.waitFor(() => expect(create).toHaveBeenCalledTimes(2));
    expect(create.mock.calls[0][1]).toBe(create.mock.calls[1][1]);
  });

  it('uses a different key when the amount changed between attempts', async () => {
    const create = vi.spyOn(api, 'createTransaction').mockRejectedValue(new ApiError(0, 'down'));
    const dialog = await openFormAndFill('10');
    await userEvent.click(within(dialog).getByRole('button', { name: /record/i }));
    await vi.waitFor(() => expect(create).toHaveBeenCalledTimes(1));
    await userEvent.type(within(dialog).getByLabelText(/Amount/), '5');
    await userEvent.click(within(dialog).getByRole('button', { name: /record/i }));
    await vi.waitFor(() => expect(create).toHaveBeenCalledTimes(2));
    expect(create.mock.calls[0][1]).not.toBe(create.mock.calls[1][1]);
  });

  it('blocks insufficient funds and zero amounts without calling the API', async () => {
    const create = vi.spyOn(api, 'createTransaction');
    const dialog = await openFormAndFill('5000');
    await userEvent.click(within(dialog).getByRole('button', { name: /record/i }));
    expect(await screen.findByText(/Insufficient funds/)).toBeTruthy();
    expect(create).not.toHaveBeenCalled();
  });
});

describe('transaction list', () => {
  it('shows an empty state and marks reversed originals without offering a second reversal', async () => {
    const original: Transaction = { ...created, id: 'o1' };
    const reversal: Transaction = { ...created, id: 'r1', transactionType: 'REVERSAL', description: 'Reversal of transaction o1', reversalOfTransactionId: 'o1' };
    vi.spyOn(api, 'transactions').mockResolvedValue([original, reversal]);
    renderAt('/app/transactions');
    expect(await screen.findByText('Reversed')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Reverse Lunch/ })).toBeNull();
  });
});
