import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { AuthResponse } from '@/models/auth';
import { toApiError } from './apiError';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

const AUTH_ENDPOINT = /^\/?auth\//;
const REFRESH_LOCK_NAME = 'stocklens-session-refresh';
const TIMEOUT_MS = 20_000;

/**
 * The access token lives only in memory (never localStorage), so injected scripts cannot read a persisted copy.
 * After a reload the session is restored from the HttpOnly refresh cookie.
 */
let accessToken: string | null = null;

export const tokenStore = {
  get: (): string | null => accessToken,
  set: (token: string | null): void => {
    accessToken = token;
  },
};

export type SessionEvent = { type: 'refreshed'; session: AuthResponse } | { type: 'expired' };
type SessionListener = (event: SessionEvent) => void;
const listeners = new Set<SessionListener>();

/** Lets the auth layer react to background refreshes and expired sessions. */
export function onSessionEvent(listener: SessionListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function emit(event: SessionEvent): void {
  listeners.forEach((listener) => listener(event));
}

function isAuthEndpoint(url: string | undefined): boolean {
  return url !== undefined && AUTH_ENDPOINT.test(url);
}

export const httpClient = axios.create({
  baseURL: API_BASE_URL,
  // Required for the refresh cookie when the API is on another origin; harmless when same-origin.
  withCredentials: true,
  timeout: TIMEOUT_MS,
  headers: { Accept: 'application/json' },
});

/** Bare client for the refresh call itself, so it never triggers the 401 retry logic recursively. */
const refreshClient = axios.create({ baseURL: API_BASE_URL, withCredentials: true, timeout: TIMEOUT_MS });

let refreshInFlight: Promise<AuthResponse> | null = null;

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers share one request (single flight); across
 * browser tabs the Web Locks API serialises refreshes, because every refresh rotates the cookie.
 */
export function refreshSession(): Promise<AuthResponse> {
  if (!refreshInFlight) {
    refreshInFlight = withCrossTabLock(async () => (await refreshClient.post<AuthResponse>('/auth/refresh')).data)
      .then((session) => {
        accessToken = session.accessToken;
        emit({ type: 'refreshed', session });
        return session;
      })
      .catch((error: unknown) => {
        throw toApiError(error);
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

function withCrossTabLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = typeof navigator === 'undefined' ? undefined : navigator.locks;
  return locks ? locks.request(REFRESH_LOCK_NAME, task) : task();
}

httpClient.interceptors.request.use((config) => {
  if (accessToken && !isAuthEndpoint(config.url)) {
    config.headers.set('Authorization', `Bearer ${accessToken}`);
  }
  return config;
});

interface RetriableRequest extends InternalAxiosRequestConfig {
  _retried?: boolean;
}

httpClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const request = error.config as RetriableRequest | undefined;
    if (error.response?.status === 401 && request && !request._retried && !isAuthEndpoint(request.url)) {
      request._retried = true;
      try {
        const session = await refreshSession();
        request.headers.set('Authorization', `Bearer ${session.accessToken}`);
        return await httpClient(request);
      } catch (retryError) {
        const apiError = toApiError(retryError);
        // Only an authentication failure ends the session; a network blip during refresh must not sign the user out.
        if (apiError.status === 401) {
          accessToken = null;
          emit({ type: 'expired' });
        }
        return Promise.reject(apiError);
      }
    }
    return Promise.reject(toApiError(error));
  },
);
