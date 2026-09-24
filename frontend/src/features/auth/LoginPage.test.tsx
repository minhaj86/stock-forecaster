import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { session } from '@/test/fixtures';
import { api, apiError } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

function signedOut() {
  server.use(http.post(api('/auth/refresh'), () => apiError(401, 'INVALID_REFRESH_TOKEN', 'The session is invalid')));
}

describe('LoginPage', () => {
  it('validates the form before calling the API', async () => {
    signedOut();
    const { user } = renderApp('/login');

    await user.click(await screen.findByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Enter your email address')).toBeInTheDocument();
    expect(screen.getByText('Enter your password')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Email'), 'not-an-email');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument();
  });

  it('signs in, sends the remember-me choice and opens the dashboard', async () => {
    signedOut();
    let body: unknown;
    server.use(http.post(api('/auth/login'), async ({ request }) => {
      body = await request.json();
      return HttpResponse.json(session());
    }));
    const { user } = renderApp('/login');

    await user.type(await screen.findByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery-staple');
    await user.click(screen.getByRole('checkbox', { name: 'Remember me' }));
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: /, Ada$/ })).toBeInTheDocument();
    expect(body).toEqual({ email: 'ada@example.com', password: 'correct-horse-battery-staple', rememberMe: false });
  });

  it('explains invalid credentials without revealing which field was wrong', async () => {
    signedOut();
    server.use(http.post(api('/auth/login'), () => apiError(401, 'INVALID_CREDENTIALS', 'Invalid e-mail address or password')));
    const { user } = renderApp('/login');

    await user.type(await screen.findByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password.');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
  });

  it('offers a password-reset placeholder', async () => {
    signedOut();
    const { user } = renderApp('/login');

    await user.click(await screen.findByRole('button', { name: 'Forgot password?' }));

    expect(await screen.findByRole('dialog', { name: 'Password reset' })).toHaveTextContent('not available yet');
  });

  it('sends signed-in users straight to the dashboard', async () => {
    renderApp('/login');

    await waitFor(() => expect(screen.getByRole('heading', { name: /, Ada$/ })).toBeInTheDocument());
  });
});
