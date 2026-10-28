import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError, api } from '../api';
import { account, renderAt, signIn } from './helpers';

beforeEach(() => { sessionStorage.clear(); signIn(); });

describe('Accounts page', () => {
  it('shows an error state with retry when loading fails', async () => {
    vi.spyOn(api, 'accounts').mockRejectedValue(new ApiError(500, 'The server had a problem. Please try again shortly.'));
    renderAt('/app/accounts');
    expect(await screen.findByText(/Couldn’t load this/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeTruthy();
  });

  it('shows exact, currency-labelled balances', async () => {
    vi.spyOn(api, 'accounts').mockResolvedValue([account({ currentBalance: '1250.50' }), account({ id: 'a2', name: 'Travel', currency: 'USD', currentBalance: '0.10' })]);
    renderAt('/app/accounts');
    expect(await screen.findByText(/1.?250\.50 INR/)).toBeTruthy();
    expect(screen.getByText(/0\.10 USD/)).toBeTruthy();
  });

  it('validates the new-account form before calling the API', async () => {
    vi.spyOn(api, 'accounts').mockResolvedValue([]);
    const create = vi.spyOn(api, 'createAccount');
    renderAt('/app/accounts');
    await userEvent.click(await screen.findByRole('button', { name: /new account/i }));
    await userEvent.click(screen.getByRole('button', { name: /create account/i }));
    expect(await screen.findByText('Account name is required.')).toBeTruthy();
    await userEvent.type(screen.getByLabelText('Name'), 'Wallet');
    await userEvent.clear(screen.getByLabelText('Opening balance'));
    await userEvent.type(screen.getByLabelText('Opening balance'), '-5');
    await userEvent.click(screen.getByRole('button', { name: /create account/i }));
    expect(await screen.findByText(/digits and an optional decimal point/)).toBeTruthy();
    expect(create).not.toHaveBeenCalled();
  });
});
