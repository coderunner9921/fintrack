import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError, api } from '../api';
import { renderAt, signIn } from './helpers';

beforeEach(() => sessionStorage.clear());

describe('login form', () => {
  it('shows field errors and does not call the API when empty or invalid', async () => {
    const login = vi.spyOn(api, 'login');
    renderAt('/login');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    expect(await screen.findByText('Email is required.')).toBeTruthy();
    expect(screen.getByText('Password is required.')).toBeTruthy();
    await userEvent.type(screen.getByLabelText('Email'), 'not-an-email');
    await userEvent.type(screen.getByLabelText('Password'), 'x');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    expect(await screen.findByText('Enter a valid email address.')).toBeTruthy();
    expect(login).not.toHaveBeenCalled();
  });

  it('shows a clear message for wrong credentials and stores no session', async () => {
    vi.spyOn(api, 'login').mockRejectedValue(new ApiError(401, 'x'));
    renderAt('/login');
    await userEvent.type(screen.getByLabelText('Email'), 'a@b.co');
    await userEvent.type(screen.getByLabelText('Password'), 'wrongpass');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    expect(await screen.findByText('Invalid email or password.')).toBeTruthy();
    expect(sessionStorage.getItem('fintrack.session')).toBeNull();
  });

  it('signs in and opens the app on success', async () => {
    vi.spyOn(api, 'login').mockResolvedValue({ token: 't', tokenType: 'Bearer', expiresIn: 900000 });
    vi.spyOn(api, 'accounts').mockResolvedValue([]);
    vi.spyOn(api, 'transactions').mockResolvedValue([]);
    vi.spyOn(api, 'analytics').mockResolvedValue({ from: '', to: '', currencies: [] });
    renderAt('/login');
    await userEvent.type(screen.getByLabelText('Email'), 'a@b.co');
    await userEvent.type(screen.getByLabelText('Password'), 'correct-pass');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Overview' })).toBeTruthy());
  });
});

describe('registration form', () => {
  it('validates name, email and password length', async () => {
    const register = vi.spyOn(api, 'register');
    renderAt('/register');
    await userEvent.click(screen.getByRole('button', { name: /create account/i }));
    expect(await screen.findByText('Full name is required.')).toBeTruthy();
    expect(screen.getByText('Email is required.')).toBeTruthy();
    expect(screen.getByText('Password is required.')).toBeTruthy();
    await userEvent.type(screen.getByLabelText('Password'), 'short');
    await userEvent.click(screen.getByRole('button', { name: /create account/i }));
    expect(await screen.findByText('Password must contain between 8 and 72 characters.')).toBeTruthy();
    expect(register).not.toHaveBeenCalled();
  });
});

describe('route protection', () => {
  it('redirects a logged-out visitor from a protected page to login', async () => {
    renderAt('/app/transactions');
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeTruthy();
  });
  it('lets a signed-in user reach a protected page', async () => {
    signIn();
    vi.spyOn(api, 'accounts').mockResolvedValue([]);
    renderAt('/app/accounts');
    expect(await screen.findByText('No accounts yet')).toBeTruthy();
  });
});
