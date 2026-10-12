import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { ResetPasswordRequest } from '@/models/admin';
import { adminUserDetails, ALICE_ID, auditEvent, page } from '@/test/adminFixtures';
import { USER_ID } from '@/test/fixtures';
import { api } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';
import { signInAsAdmin } from '@/test/signInAsAdmin';

const valueOf = (region: HTMLElement, label: string) => within(region).getByText(label).nextElementSibling?.textContent;

describe('AdminUserDetailsPage', () => {
  it('shows the profile and the recent activity of the user', async () => {
    signInAsAdmin();
    let auditQuery: URLSearchParams | undefined;
    server.use(http.get(api('/admin/audit-log'), ({ request }) => {
      auditQuery = new URL(request.url).searchParams;
      return HttpResponse.json(page([auditEvent()]));
    }));
    renderApp(`/app/admin/users/${ALICE_ID}`);

    expect(await screen.findByRole('heading', { name: 'Alice Smith', level: 1 })).toBeInTheDocument();
    const profile = screen.getByRole('region', { name: 'Profile' });
    expect(valueOf(profile, 'Email')).toBe('alice@example.com');
    expect(valueOf(profile, 'Role')).toBe('User');
    expect(valueOf(profile, 'Status')).toBe('Active');
    expect(valueOf(profile, 'Time zone')).toBe('Europe/Berlin');
    expect(valueOf(profile, 'Watchlists')).toBe('2');
    expect(valueOf(profile, 'Last sign-in')).toBe('Never');
    expect(valueOf(profile, 'Password change pending')).toBe('No');

    const activity = await screen.findByRole('list', { name: 'Recent activity' });
    expect(within(activity).getByText('User disabled: Disabled alice@example.com')).toBeInTheDocument();
    expect(auditQuery?.get('entityType')).toBe('USER');
    expect(auditQuery?.get('entityId')).toBe(ALICE_ID);
    expect(auditQuery?.get('size')).toBe('10');
  });

  it('explains when the user has no recorded activity', async () => {
    signInAsAdmin();
    server.use(http.get(api('/admin/audit-log'), () => HttpResponse.json(page([]))));
    renderApp(`/app/admin/users/${ALICE_ID}`);

    expect(await screen.findByText('No recorded activity')).toBeInTheDocument();
  });

  it('resets the password with a temporary one', async () => {
    signInAsAdmin();
    let body: ResetPasswordRequest | undefined;
    server.use(http.post(api('/admin/users/:id/password-reset'), async ({ request }) => {
      body = (await request.json()) as ResetPasswordRequest;
      return HttpResponse.json(adminUserDetails({ passwordChangeRequired: true }));
    }));
    const { user } = renderApp(`/app/admin/users/${ALICE_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Reset password' }));
    const dialog = await screen.findByRole('dialog', { name: 'Reset password' });
    await user.click(within(dialog).getByRole('button', { name: 'Generate secure password' }));
    const generated = (within(dialog).getByLabelText('Temporary password') as HTMLInputElement).value;
    expect(within(dialog).getByRole('checkbox', { name: 'Require password change at next sign-in' })).toBeChecked();
    await user.click(within(dialog).getByRole('button', { name: 'Reset password' }));

    expect(await screen.findByText('Password reset for alice@example.com. All of their sessions have been signed out.'))
      .toBeInTheDocument();
    expect(body).toEqual({ temporaryPassword: generated, requirePasswordChange: true });
  });

  it('returns to the user list after deleting the user', async () => {
    signInAsAdmin();
    const { user, router } = renderApp(`/app/admin/users/${ALICE_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Delete' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete user?' });
    await user.type(within(dialog).getByLabelText('Type DELETE to confirm'), 'DELETE');
    await user.click(within(dialog).getByRole('button', { name: 'Delete user' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/app/admin/users'));
    expect(await screen.findByText('Deleted alice@example.com and all of their data')).toBeInTheDocument();
  });

  it('does not offer to disable or delete the own account', async () => {
    signInAsAdmin();
    renderApp(`/app/admin/users/${USER_ID}`);

    expect(await screen.findByRole('heading', { name: 'Ada Lovelace', level: 1 })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Disable' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
  });

  it('explains unknown users', async () => {
    signInAsAdmin();
    renderApp('/app/admin/users/unknown-user');

    expect(await screen.findByText('User not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to users' })).toHaveAttribute('href', '/app/admin/users');
  });
});
