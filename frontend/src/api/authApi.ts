import type { AuthResponse, ChangePasswordRequest, LoginRequest, RegisterRequest } from '@/models/auth';
import { toApiError } from './apiError';
import { httpClient, refreshSession, tokenStore } from './httpClient';

export const authApi = {
  async login(request: LoginRequest): Promise<AuthResponse> {
    const { data } = await httpClient.post<AuthResponse>('/auth/login', request);
    tokenStore.set(data.accessToken);
    return data;
  },

  async register(request: RegisterRequest): Promise<AuthResponse> {
    const { data } = await httpClient.post<AuthResponse>('/auth/register', request);
    tokenStore.set(data.accessToken);
    return data;
  },

  refresh: refreshSession,

  /**
   * Authenticated by the refresh cookie (no bearer token). Returns a new session (the cookie is rotated and all other
   * sessions are signed out); a 401 means this session has ended as well.
   */
  async changePassword(request: ChangePasswordRequest): Promise<AuthResponse> {
    try {
      const { data } = await httpClient.post<AuthResponse>('/auth/password', request);
      tokenStore.set(data.accessToken);
      return data;
    } catch (error) {
      const apiError = toApiError(error);
      if (apiError.status === 401) tokenStore.set(null);
      throw apiError;
    }
  },

  /** Revokes the session server-side; the local token is dropped even if the call fails. */
  async logout(): Promise<void> {
    try {
      await httpClient.post('/auth/logout');
    } finally {
      tokenStore.set(null);
    }
  },
};
