import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ReactElement } from 'react';
import App from '../App';
import { AuthProvider } from '../auth';
import { sessionStore, type Account } from '../api';
import { ToastProvider } from '../ui';

export function renderAt(path: string, ui: ReactElement = <App />) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider><ToastProvider>{ui}</ToastProvider></AuthProvider>
    </MemoryRouter>,
  );
}
export const signIn = () => sessionStore.write({ token: 'test-token', email: 'tester@example.com', expiresAt: Date.now() + 15 * 60_000 });
export const account = (o: Partial<Account> = {}): Account => ({
  id: 'a1', name: 'Main', accountType: 'CURRENT', currency: 'INR', openingBalance: '0.00', active: true,
  createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z', currentBalance: '1000.00', ...o,
});
