import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi } from '@/api/authApi';
import { onSessionEvent } from '@/api/httpClient';
import type { LoginRequest, RegisterRequest, UserSummary } from '@/models/auth';
import { AuthContext, type AuthContextValue, type AuthStatus } from './authContext';

interface AuthState {
  status: AuthStatus;
  user: UserSummary | null;
  sessionExpired: boolean;
}

/**
 * Owns the authentication state. On start-up the session is restored from the HttpOnly refresh cookie; afterwards
 * the HTTP client keeps tokens fresh and reports expired sessions here. Server state of the previous user is
 * discarded on every identity change.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<AuthState>({ status: 'initializing', user: null, sessionExpired: false });

  useEffect(() => {
    let active = true;
    const unsubscribe = onSessionEvent((event) => {
      if (event.type === 'refreshed') {
        setState({ status: 'authenticated', user: event.session.user, sessionExpired: false });
      } else {
        queryClient.clear();
        setState({ status: 'unauthenticated', user: null, sessionExpired: true });
      }
    });
    authApi.refresh().catch(() => {
      if (active) {
        setState((current) => (current.status === 'initializing'
          ? { status: 'unauthenticated', user: null, sessionExpired: false } : current));
      }
    });
    return () => {
      active = false;
      unsubscribe();
    };
  }, [queryClient]);

  const login = useCallback(async (request: LoginRequest) => {
    const session = await authApi.login(request);
    queryClient.clear();
    setState({ status: 'authenticated', user: session.user, sessionExpired: false });
  }, [queryClient]);

  const register = useCallback(async (request: RegisterRequest) => {
    const session = await authApi.register(request);
    queryClient.clear();
    setState({ status: 'authenticated', user: session.user, sessionExpired: false });
  }, [queryClient]);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // The session is ended locally regardless; the server-side token expires on its own.
    }
    queryClient.clear();
    setState({ status: 'unauthenticated', user: null, sessionExpired: false });
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(() => ({ ...state, login, register, logout }),
    [state, login, register, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
