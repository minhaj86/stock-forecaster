import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { session } from '@/test/fixtures';
import { api, apiError } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

function signedOut() {
  server.use(http.post(api('/auth/refresh'), () => apiError(401, 'INVALID_REFRESH_TOKEN', 'The session is invalid')));
}

describe('RegisterPage', () => {
  it('enforces the password policy and matching confirmation', async () => {
    signedOut();
    const { user } = renderApp('/register');

    await user.type(await screen.findByLabelText('Name'), 'Ada');
    await user.type(screen.getByLabelText('Email'), 'ada@example');
    await user.type(screen.getByLabelText('Password'), 'short');
    await user.type(screen.getByLabelText('Confirm password'), 'different');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument();
    expect(screen.getByText('Use at least 12 characters')).toBeInTheDocument();
    expect(screen.getByText('Passwords do not match')).toBeInTheDocument();
  });

  it('shows an already registered email on the email field', async () => {
    signedOut();
    server.use(http.post(api('/auth/register'),
      () => apiError(409, 'EMAIL_ALREADY_REGISTERED', 'An account with this e-mail address already exists')));
    const { user } = renderApp('/register');

    await user.type(await screen.findByLabelText('Name'), 'Ada');
    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery');
    await user.type(screen.getByLabelText('Confirm password'), 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('An account with this email already exists')).toBeInTheDocument();
  });

  it('registers, signs in automatically and opens the dashboard', async () => {
    signedOut();
    let body: unknown;
    server.use(http.post(api('/auth/register'), async ({ request }) => {
      body = await request.json();
      return HttpResponse.json(session(), { status: 201 });
    }));
    const { user } = renderApp('/register');

    await user.type(await screen.findByLabelText('Name'), '  Ada Lovelace ');
    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery');
    await user.type(screen.getByLabelText('Confirm password'), 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('heading', { name: /, Ada$/ })).toBeInTheDocument();
    expect(body).toEqual({ displayName: 'Ada Lovelace', email: 'ada@example.com', password: 'correct-horse-battery' });
  });
});
