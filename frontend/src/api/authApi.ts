import type { AuthResponse, LoginRequest, RegisterRequest } from '@/models/auth';
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

  /** Revokes the session server-side; the local token is dropped even if the call fails. */
  async logout(): Promise<void> {
    try {
      await httpClient.post('/auth/logout');
    } finally {
      tokenStore.set(null);
    }
  },
};
