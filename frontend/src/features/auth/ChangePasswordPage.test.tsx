import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { AuthResponse, ChangePasswordRequest } from '@/models/auth';
import { session } from '@/test/fixtures';
import { api, apiError } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

/** A session whose administrator-issued temporary password must be replaced. */
function temporarySession(): AuthResponse {
  const base = session();
  return { ...base, user: { ...base.user, passwordChangeRequired: true } };
}

function signedOut() {
  server.use(http.post(api('/auth/refresh'), () => apiError(401, 'INVALID_REFRESH_TOKEN', 'The session is invalid')));
}

async function changePassword(user: ReturnType<typeof renderApp>['user'], current: string, next: string) {
  await user.type(await screen.findByLabelText('Temporary password'), current);
  await user.type(screen.getByLabelText('New password'), next);
  await user.type(screen.getByLabelText('Confirm new password'), next);
  await user.click(screen.getByRole('button', { name: 'Change password' }));
}

describe('forced password change', () => {
  it('sends a user with a temporary password to the password change after login, then to the dashboard', async () => {
    signedOut();
    let body: ChangePasswordRequest | undefined;
    server.use(
      http.post(api('/auth/login'), () => HttpResponse.json(temporarySession())),
      http.post(api('/auth/password'), async ({ request }) => {
        body = (await request.json()) as ChangePasswordRequest;
        return HttpResponse.json(session({ accessToken: 'access-token-2' }));
      }),
    );
    const { user, router } = renderApp('/login');

    await user.type(await screen.findByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'Temporary-Password-1');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Choose a new password' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/change-password');
    expect(screen.getByText('ada@example.com')).toBeInTheDocument();

    await changePassword(user, 'Temporary-Password-1', 'correct-horse-battery-staple');

    expect(await screen.findByRole('heading', { name: /, Ada$/ })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/app/dashboard');
    expect(body).toEqual({ currentPassword: 'Temporary-Password-1', newPassword: 'correct-horse-battery-staple' });
  });

  it('brings a restored session back to the requested page once the password has been changed', async () => {
    server.use(http.post(api('/auth/refresh'), () => HttpResponse.json(temporarySession())));
    const { user, router } = renderApp('/app/watchlists');

    expect(await screen.findByRole('heading', { name: 'Choose a new password' })).toBeInTheDocument();
    await changePassword(user, 'Temporary-Password-1', 'correct-horse-battery-staple');

    expect(await screen.findByRole('heading', { name: 'My Watchlists' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/app/watchlists');
  });

  it('shows a wrong temporary password on its field', async () => {
    server.use(
      http.post(api('/auth/refresh'), () => HttpResponse.json(temporarySession())),
      http.post(api('/auth/password'), () => apiError(400, 'INVALID_CURRENT_PASSWORD', 'The current password is incorrect',
        { errors: [{ field: 'currentPassword', message: 'The current password is incorrect' }] })),
    );
    const { user, router } = renderApp('/app/dashboard');

    await changePassword(user, 'Wrong-Password-1', 'correct-horse-battery-staple');

    const current = screen.getByLabelText('Temporary password');
    await waitFor(() => expect(current).toHaveAccessibleDescription('The current password is incorrect'));
    expect(current).toHaveAttribute('aria-invalid', 'true');
    expect(router.state.location.pathname).toBe('/change-password');
  });

  it('shows a rejected new password on its field', async () => {
    server.use(
      http.post(api('/auth/refresh'), () => HttpResponse.json(temporarySession())),
      http.post(api('/auth/password'),
        () => apiError(400, 'PASSWORD_POLICY_VIOLATION', 'This password appears in a list of breached passwords')),
    );
    const { user } = renderApp('/app/dashboard');

    await changePassword(user, 'Temporary-Password-1', 'password1234');

    await waitFor(() => expect(screen.getByLabelText('New password'))
      .toHaveAccessibleDescription('This password appears in a list of breached passwords'));
  });

  it('requires a password that differs from the temporary one', async () => {
    server.use(http.post(api('/auth/refresh'), () => HttpResponse.json(temporarySession())));
    const { user } = renderApp('/app/dashboard');

    await changePassword(user, 'Temporary-Password-1', 'Temporary-Password-1');

    expect(await screen.findByText('Choose a password that differs from the current one')).toBeInTheDocument();
  });

  it('signs the user out when the session ended in the meantime', async () => {
    server.use(
      http.post(api('/auth/refresh'), () => HttpResponse.json(temporarySession())),
      http.post(api('/auth/password'), () => apiError(401, 'INVALID_REFRESH_TOKEN', 'The session is invalid')),
    );
    const { user } = renderApp('/app/dashboard');

    await changePassword(user, 'Temporary-Password-1', 'correct-horse-battery-staple');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByText('Your session has ended. Please sign in again.')).toBeInTheDocument();
  });

  it('lets the user sign out instead', async () => {
    server.use(http.post(api('/auth/refresh'), () => HttpResponse.json(temporarySession())));
    const { user } = renderApp('/change-password');

    await user.click(await screen.findByRole('button', { name: 'Sign out' }));

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('is only for signed-in users who must change their password', async () => {
    const { router } = renderApp('/change-password');
    expect(await screen.findByRole('heading', { name: /, Ada$/ })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/app/dashboard');
  });

  it('sends anonymous visitors to the login page', async () => {
    signedOut();
    const { router } = renderApp('/change-password');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });
});
