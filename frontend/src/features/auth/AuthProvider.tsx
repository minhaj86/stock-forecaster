import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { toApiError } from '@/api/apiError';
import { authApi } from '@/api/authApi';
import { onSessionEvent } from '@/api/httpClient';
import type { ChangePasswordRequest, LoginRequest, RegisterRequest, UserSummary } from '@/models/auth';
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

  // Same identity, so cached server state stays; the guards react to the cleared `passwordChangeRequired` flag.
  const changePassword = useCallback(async (request: ChangePasswordRequest) => {
    try {
      const session = await authApi.changePassword(request);
      setState({ status: 'authenticated', user: session.user, sessionExpired: false });
    } catch (error) {
      const apiError = toApiError(error);
      if (apiError.status === 401) {
        // The refresh session behind this tab has ended: the user has to sign in again.
        queryClient.clear();
        setState({ status: 'unauthenticated', user: null, sessionExpired: true });
      }
      throw apiError;
    }
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

  const value = useMemo<AuthContextValue>(() => ({ ...state, login, register, changePassword, logout }),
    [state, login, register, changePassword, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
