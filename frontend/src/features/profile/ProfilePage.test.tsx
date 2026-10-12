import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { ChangePasswordRequest } from '@/models/auth';
import { session } from '@/test/fixtures';
import { api, apiError } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

describe('ProfilePage', () => {
  it('changes the password of the signed-in user', async () => {
    let body: ChangePasswordRequest | undefined;
    server.use(http.post(api('/auth/password'), async ({ request }) => {
      body = (await request.json()) as ChangePasswordRequest;
      return HttpResponse.json(session({ accessToken: 'access-token-2' }));
    }));
    const { user } = renderApp('/app/profile');

    const card = await screen.findByRole('region', { name: 'Change password' });
    await user.type(within(card).getByLabelText('Current password'), 'old-password-123');
    await user.type(within(card).getByLabelText('New password'), 'correct-horse-battery-staple');
    await user.type(within(card).getByLabelText('Confirm new password'), 'correct-horse-battery-staple');
    await user.click(within(card).getByRole('button', { name: 'Change password' }));

    expect(await screen.findByText('Password changed. Your other sessions have been signed out.')).toBeInTheDocument();
    expect(body).toEqual({ currentPassword: 'old-password-123', newPassword: 'correct-horse-battery-staple' });
    expect(within(card).getByLabelText('Current password')).toHaveValue('');
    expect(screen.getByText('Changing your email address is not available yet.')).toBeInTheDocument();
  });

  it('shows a wrong current password on its field', async () => {
    server.use(http.post(api('/auth/password'),
      () => apiError(400, 'INVALID_CURRENT_PASSWORD', 'The current password is incorrect')));
    const { user } = renderApp('/app/profile');

    const card = await screen.findByRole('region', { name: 'Change password' });
    await user.type(within(card).getByLabelText('Current password'), 'wrong-password-1');
    await user.type(within(card).getByLabelText('New password'), 'correct-horse-battery-staple');
    await user.type(within(card).getByLabelText('Confirm new password'), 'correct-horse-battery-staple');
    await user.click(within(card).getByRole('button', { name: 'Change password' }));

    expect(await within(card).findByText('The current password is incorrect')).toBeInTheDocument();
  });
});
