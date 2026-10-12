import { createContext, useContext } from 'react';
import type { ChangePasswordRequest, LoginRequest, RegisterRequest, UserSummary } from '@/models/auth';

export type AuthStatus = 'initializing' | 'authenticated' | 'unauthenticated';

export interface AuthContextValue {
  status: AuthStatus;
  user: UserSummary | null;
  /** The previous session ended unexpectedly (expired or revoked), so the login page can explain why. */
  sessionExpired: boolean;
  login: (request: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  /** Replaces the current (or administrator-issued temporary) password; the session continues with a new token. */
  changePassword: (request: ChangePasswordRequest) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
