import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, sessionStore, setUnauthorizedHandler, type Session } from './api';

interface AuthValue {
  session: Session | null;
  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  expired: boolean;
}
const Ctx = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => sessionStore.read());
  const [expired, setExpired] = useState(false);
  const timer = useRef<number>();

  // A deliberate logout is not an expiry, so it must also clear the "session expired" notice.
  const logout = useCallback(() => { sessionStore.clear(); setExpired(false); setSession(null); }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => { setExpired(true); setSession(null); });
  }, []);

  // JWT lifetime is fixed at 15 minutes and there is no refresh endpoint: sign out at expiry.
  useEffect(() => {
    window.clearTimeout(timer.current);
    if (session) {
      timer.current = window.setTimeout(() => { logout(); setExpired(true); }, Math.max(0, session.expiresAt - Date.now()));
    }
    // Browsers throttle timers in sleeping/background tabs: re-check the clock when the tab becomes visible again.
    const onVisible = () => {
      if (document.visibilityState === 'visible' && session && !sessionStore.read()) { setExpired(true); setSession(null); }
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => { window.clearTimeout(timer.current); document.removeEventListener('visibilitychange', onVisible); };
  }, [session, logout]);

  const login = useCallback(async (email: string, password: string, fullName?: string) => {
    const res = await api.login({ email, password });
    const s: Session = { token: res.token, email, expiresAt: Date.now() + res.expiresIn, fullName };
    sessionStore.write(s); setExpired(false); setSession(s);
  }, []);

  const register = useCallback(async (fullName: string, email: string, password: string) => {
    await api.register({ fullName, email, password });
    await login(email, password, fullName);
  }, [login]);

  const value = useMemo(() => ({ session, login: (e: string, p: string) => login(e, p), register, logout, expired }), [session, login, register, logout, expired]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthValue {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth must be used inside AuthProvider');
  return v;
}
