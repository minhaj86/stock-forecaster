import { http, HttpResponse } from 'msw';
import { afterAll, afterEach, describe, expect, it } from 'vitest';
import { session } from '@/test/fixtures';
import { api, apiError } from '@/test/handlers';
import { server } from '@/test/server';
import { ApiError, toApiError } from './apiError';
import { httpClient, onSessionEvent, tokenStore, type SessionEvent } from './httpClient';

describe('httpClient', () => {
  const events: SessionEvent[] = [];
  const unsubscribe = onSessionEvent((event) => events.push(event));
  afterEach(() => {
    events.length = 0;
  });
  afterAll(() => unsubscribe());

  it('attaches the access token', async () => {
    let authorization: string | null = null;
    server.use(http.get(api('/users/me'), ({ request }) => {
      authorization = request.headers.get('Authorization');
      return HttpResponse.json({});
    }));
    tokenStore.set('token-a');

    await httpClient.get('/users/me');

    expect(authorization).toBe('Bearer token-a');
  });

  it('refreshes an expired access token once and retries the request', async () => {
    const seen: (string | null)[] = [];
    server.use(
      http.get(api('/watchlists'), ({ request }) => {
        const authorization = request.headers.get('Authorization');
        seen.push(authorization);
        return authorization === 'Bearer fresh-token' ? HttpResponse.json([])
          : apiError(401, 'INVALID_ACCESS_TOKEN', 'The access token is invalid or has expired');
      }),
      http.post(api('/auth/refresh'), () => HttpResponse.json(session({ accessToken: 'fresh-token' }))),
    );
    tokenStore.set('stale-token');

    const response = await httpClient.get('/watchlists');

    expect(response.status).toBe(200);
    expect(seen).toEqual(['Bearer stale-token', 'Bearer fresh-token']);
    expect(tokenStore.get()).toBe('fresh-token');
    expect(events).toEqual([{ type: 'refreshed', session: session({ accessToken: 'fresh-token' }) }]);
  });

  it('shares one refresh between concurrent requests', async () => {
    let refreshes = 0;
    server.use(
      http.get(api('/stocks/sectors'), ({ request }) => (request.headers.get('Authorization') === 'Bearer fresh-token'
        ? HttpResponse.json([]) : apiError(401, 'INVALID_ACCESS_TOKEN', 'expired'))),
      http.post(api('/auth/refresh'), async () => {
        refreshes++;
        await new Promise((resolve) => setTimeout(resolve, 20));
        return HttpResponse.json(session({ accessToken: 'fresh-token' }));
      }),
    );
    tokenStore.set('stale-token');

    await Promise.all([httpClient.get('/stocks/sectors'), httpClient.get('/stocks/sectors'), httpClient.get('/stocks/sectors')]);

    expect(refreshes).toBe(1);
  });

  it('reports an expired session when the refresh is rejected', async () => {
    server.use(
      http.get(api('/dashboard'), () => apiError(401, 'INVALID_ACCESS_TOKEN', 'expired')),
      http.post(api('/auth/refresh'), () => apiError(401, 'INVALID_REFRESH_TOKEN', 'The session is invalid')),
    );
    tokenStore.set('stale-token');

    const error = await httpClient.get('/dashboard').catch((failure: unknown) => failure);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(401);
    expect(tokenStore.get()).toBeNull();
    expect(events).toEqual([{ type: 'expired' }]);
  });

  it('never retries authentication endpoints', async () => {
    let refreshes = 0;
    server.use(
      http.post(api('/auth/login'), () => apiError(401, 'INVALID_CREDENTIALS', 'Invalid e-mail address or password')),
      http.post(api('/auth/refresh'), () => {
        refreshes++;
        return HttpResponse.json(session());
      }),
    );

    const error = await httpClient.post('/auth/login', {}).catch((failure: unknown) => failure);

    expect((error as ApiError).code).toBe('INVALID_CREDENTIALS');
    expect(refreshes).toBe(0);
  });

  it('turns transport failures into actionable errors', async () => {
    server.use(http.get(api('/watchlists'), () => HttpResponse.error()));

    const error = await httpClient.get('/watchlists').catch((failure: unknown) => failure);

    expect(error).toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
    expect((error as ApiError).message).toMatch(/Unable to reach the server/);
    expect(toApiError(new Error('boom')).code).toBe('UNEXPECTED');
  });
});
