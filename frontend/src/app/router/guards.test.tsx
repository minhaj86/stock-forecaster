import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { session } from '@/test/fixtures';
import { api, apiError } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

describe('route protection', () => {
  it('redirects anonymous visitors to the login page and returns them afterwards', async () => {
    let signedIn = false;
    server.use(
      http.post(api('/auth/refresh'), () => (signedIn ? HttpResponse.json(session())
        : apiError(401, 'INVALID_REFRESH_TOKEN', 'The session is invalid'))),
      http.post(api('/auth/login'), () => {
        signedIn = true;
        return HttpResponse.json(session());
      }),
    );
    const { user, router } = renderApp('/app/watchlists');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');

    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery-staple');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'My Watchlists' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/app/watchlists');
  });

  it('restores the session from the refresh cookie on page load', async () => {
    renderApp('/app/dashboard');

    expect(await screen.findByRole('heading', { name: /, Ada$/ })).toBeInTheDocument();
  });

  it('signs the user out with an explanation when the session can no longer be refreshed', async () => {
    let refreshes = 0;
    server.use(
      http.post(api('/auth/refresh'), () => (refreshes++ === 0 ? HttpResponse.json(session())
        : apiError(401, 'INVALID_REFRESH_TOKEN', 'The session is invalid'))),
      http.get(api('/dashboard'), () => apiError(401, 'INVALID_ACCESS_TOKEN', 'The access token is invalid or has expired')),
    );
    renderApp('/app/dashboard');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByText('Your session has ended. Please sign in again.')).toBeInTheDocument();
  });

  it('signs out from the account menu', async () => {
    let loggedOut = false;
    server.use(http.post(api('/auth/logout'), () => {
      loggedOut = true;
      return new HttpResponse(null, { status: 204 });
    }));
    const { user } = renderApp('/app/dashboard');

    await user.click(await screen.findByRole('button', { name: 'Open account menu' }));
    await user.click(within(await screen.findByRole('menu')).getByRole('menuitem', { name: 'Sign out' }));

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(loggedOut).toBe(true);
  });
});
