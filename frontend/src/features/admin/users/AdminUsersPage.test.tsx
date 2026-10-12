import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { CreateUserRequest, UpdateUserRequest } from '@/models/admin';
import { adminUserDetails, adminUsers, ALICE_ID, page } from '@/test/adminFixtures';
import { api, apiError } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';
import { signInAsAdmin } from '@/test/signInAsAdmin';

/** Serves the user list and records the query parameters of every request. */
function recordUserRequests(totalElements = adminUsers.length) {
  const requests: URLSearchParams[] = [];
  server.use(http.get(api('/admin/users'), ({ request }) => {
    const params = new URL(request.url).searchParams;
    requests.push(params);
    return HttpResponse.json(page(adminUsers, {
      number: Number(params.get('page') ?? 0), totalElements, totalPages: Math.ceil(totalElements / 20),
    }));
  }));
  return requests;
}

async function openUserActions(user: ReturnType<typeof renderApp>['user'], email: string) {
  await user.click(await screen.findByRole('button', { name: `Actions for ${email}` }));
  return screen.findByRole('menu');
}

describe('AdminUsersPage', () => {
  it('lists users with role, status and the signed-in administrator marked', async () => {
    signInAsAdmin();
    renderApp('/app/admin/users');

    const table = await screen.findByRole('table', { name: 'Users' });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(5);
    expect(rows[1]).toHaveTextContent('Ada Lovelace');
    expect(within(rows[1]).getByText('You')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Administrator')).toBeInTheDocument();
    expect(rows[2]).toHaveTextContent('alice@example.com');
    expect(within(rows[2]).getByText('Active')).toBeInTheDocument();
    expect(within(rows[3]).getByText('Disabled')).toBeInTheDocument();
    expect(within(rows[4]).getByText('Locked')).toBeInTheDocument();
    expect(rows[4]).toHaveTextContent('Password change pending');
    expect(within(rows[2]).getByRole('link', { name: 'alice@example.com' })).toHaveAttribute('href', `/app/admin/users/${ALICE_ID}`);
    expect(await screen.findByText(/128 users · 121 active · 6 disabled · 1 locked · 2 administrators/)).toBeInTheDocument();
  });

  it('sends the debounced search, filters and page to the backend', async () => {
    signInAsAdmin();
    const requests = recordUserRequests(45);
    const { user } = renderApp('/app/admin/users');

    await screen.findByRole('table', { name: 'Users' });
    expect(requests[0].toString()).toBe('page=0&size=20');

    await user.type(screen.getByRole('searchbox', { name: 'Search by name or email' }), 'alice');
    await waitFor(() => expect(requests.at(-1)?.get('query')).toBe('alice'));
    expect(requests.filter((params) => params.has('query'))).toHaveLength(1);

    await user.click(screen.getByRole('combobox', { name: /^Role/ }));
    await user.click(await screen.findByRole('option', { name: 'Administrator' }));
    await waitFor(() => expect(requests.at(-1)?.get('role')).toBe('ADMIN'));

    await user.click(screen.getByRole('combobox', { name: /^Status/ }));
    await user.click(await screen.findByRole('option', { name: 'Locked' }));
    await waitFor(() => expect(requests.at(-1)?.get('status')).toBe('LOCKED'));

    await user.type(screen.getByLabelText('Created from'), '2026-10-01');
    await waitFor(() => expect(requests.at(-1)?.get('createdFrom')).toBe('2026-10-01'));
    expect(requests.at(-1)?.get('query')).toBe('alice');

    await user.click(screen.getByRole('button', { name: 'Go to next page' }));
    await waitFor(() => expect(requests.at(-1)?.get('page')).toBe('1'));
    expect(requests.at(-1)?.get('role')).toBe('ADMIN');
  });

  it('offers to add the first user when there are none', async () => {
    signInAsAdmin();
    server.use(http.get(api('/admin/users'), () => HttpResponse.json(page([]))));
    renderApp('/app/admin/users');

    expect(await screen.findByText('No users yet')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Add user' })).toHaveLength(2);
  });

  it('explains when no user matches the filters', async () => {
    signInAsAdmin();
    server.use(http.get(api('/admin/users'), ({ request }) =>
      HttpResponse.json(page(new URL(request.url).searchParams.has('query') ? [] : adminUsers))));
    const { user } = renderApp('/app/admin/users?query=nobody');

    const empty = (await screen.findByText('No users match these filters')).closest('[role="status"]') as HTMLElement;
    await user.click(within(empty).getByRole('button', { name: 'Clear filters' }));

    expect(await screen.findByRole('table', { name: 'Users' })).toBeInTheDocument();
    expect(screen.getByRole('searchbox', { name: 'Search by name or email' })).toHaveValue('');
  });

  it('shows the backend message when users cannot be loaded', async () => {
    signInAsAdmin();
    server.use(http.get(api('/admin/users'), () => apiError(500, 'INTERNAL_ERROR', 'The user directory is unavailable.')));
    renderApp('/app/admin/users');

    expect(await screen.findByText('Unable to load users')).toBeInTheDocument();
    expect(screen.getByText('The user directory is unavailable.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });

  it('creates a user with a generated temporary password', async () => {
    signInAsAdmin();
    let body: CreateUserRequest | undefined;
    server.use(http.post(api('/admin/users'), async ({ request }) => {
      body = (await request.json()) as CreateUserRequest;
      return HttpResponse.json(adminUserDetails({ id: 'new-user', email: 'grace@example.com', displayName: 'Grace Hopper' }),
        { status: 201 });
    }));
    const { user } = renderApp('/app/admin/users');

    await user.click(await screen.findByRole('button', { name: 'Add user' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add user' });
    await user.type(within(dialog).getByLabelText('Display name'), 'Grace Hopper');
    await user.type(within(dialog).getByLabelText('Email'), 'grace@example.com');
    await user.click(within(dialog).getByRole('combobox', { name: /^Role/ }));
    await user.click(await screen.findByRole('option', { name: 'Administrator' }));
    await user.click(within(dialog).getByRole('button', { name: 'Generate secure password' }));
    const password = within(dialog).getByLabelText('Temporary password');
    const generated = (password as HTMLInputElement).value;
    expect(generated.length).toBeGreaterThanOrEqual(20);
    expect(password).toHaveAttribute('type', 'text');
    expect(within(dialog).getByRole('checkbox', { name: 'Require password change on first login' })).toBeChecked();

    await user.click(within(dialog).getByRole('button', { name: 'Create user' }));

    expect(await screen.findByText('Created grace@example.com. They must change the password at first sign-in.'))
      .toBeInTheDocument();
    expect(body).toEqual({
      displayName: 'Grace Hopper', email: 'grace@example.com', role: 'ADMIN', status: 'ACTIVE',
      temporaryPassword: generated, requirePasswordChange: true,
    });
  });

  it('validates the new user before sending it', async () => {
    signInAsAdmin();
    const { user } = renderApp('/app/admin/users');

    await user.click(await screen.findByRole('button', { name: 'Add user' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add user' });
    await user.type(within(dialog).getByLabelText('Email'), 'not-an-email');
    await user.type(within(dialog).getByLabelText('Temporary password'), 'too-short');
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }));

    expect(await within(dialog).findByText('Enter a display name')).toBeInTheDocument();
    expect(within(dialog).getByText('Enter a valid email address')).toBeInTheDocument();
    expect(within(dialog).getByText('Use at least 12 characters')).toBeInTheDocument();
  });

  it('shows an already registered email on the email field', async () => {
    signInAsAdmin();
    server.use(http.post(api('/admin/users'),
      () => apiError(409, 'EMAIL_ALREADY_REGISTERED', 'An account with this e-mail address already exists')));
    const { user } = renderApp('/app/admin/users');

    await user.click(await screen.findByRole('button', { name: 'Add user' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add user' });
    await user.type(within(dialog).getByLabelText('Display name'), 'Alice Again');
    await user.type(within(dialog).getByLabelText('Email'), 'alice@example.com');
    await user.type(within(dialog).getByLabelText('Temporary password'), 'correct-horse-battery');
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }));

    const email = within(dialog).getByLabelText('Email');
    await waitFor(() => expect(email).toHaveAccessibleDescription('An account with this e-mail address already exists'));
    expect(email).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('dialog', { name: 'Add user' })).toBeInTheDocument();
  });

  it('sends only the changed fields when editing a user', async () => {
    signInAsAdmin();
    let changes: UpdateUserRequest | undefined;
    server.use(http.patch(api('/admin/users/:id'), async ({ request }) => {
      changes = (await request.json()) as UpdateUserRequest;
      return HttpResponse.json(adminUserDetails({ displayName: 'Alice Johnson' }));
    }));
    const { user } = renderApp('/app/admin/users');

    await user.click(within(await openUserActions(user, 'alice@example.com')).getByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit user' });
    const name = within(dialog).getByLabelText('Display name');
    await user.clear(name);
    await user.type(name, 'Alice Johnson');
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('Saved changes to alice@example.com')).toBeInTheDocument();
    expect(changes).toEqual({ displayName: 'Alice Johnson' });
  });

  it('disables a user after confirmation', async () => {
    signInAsAdmin();
    let disabled: string | undefined;
    server.use(http.post(api('/admin/users/:id/disable'), ({ params }) => {
      disabled = String(params.id);
      return HttpResponse.json(adminUserDetails({ status: 'DISABLED' }));
    }));
    const { user } = renderApp('/app/admin/users');

    await user.click(within(await openUserActions(user, 'alice@example.com')).getByRole('menuitem', { name: 'Disable' }));
    const dialog = await screen.findByRole('dialog', { name: 'Disable alice@example.com?' });
    expect(disabled).toBeUndefined();
    await user.click(within(dialog).getByRole('button', { name: 'Disable user' }));

    expect(await screen.findByText('Disabled alice@example.com. They have been signed out everywhere.')).toBeInTheDocument();
    expect(disabled).toBe(ALICE_ID);
  });

  it('shows why the backend refused to disable a user', async () => {
    signInAsAdmin();
    server.use(http.post(api('/admin/users/:id/disable'),
      () => apiError(409, 'LAST_ADMINISTRATOR', 'At least one active administrator must remain.')));
    const { user } = renderApp('/app/admin/users');

    await user.click(within(await openUserActions(user, 'alice@example.com')).getByRole('menuitem', { name: 'Disable' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Disable user' }));

    expect(await screen.findByText('At least one active administrator must remain.')).toBeInTheDocument();
  });

  it('never offers to disable or delete the signed-in administrator', async () => {
    signInAsAdmin();
    const { user } = renderApp('/app/admin/users');

    const menu = await openUserActions(user, 'ada@example.com');
    expect(within(menu).getByRole('menuitem', { name: 'Edit' })).toBeInTheDocument();
    expect(within(menu).queryByRole('menuitem', { name: 'Disable' })).not.toBeInTheDocument();
    expect(within(menu).queryByRole('menuitem', { name: 'Delete' })).not.toBeInTheDocument();
  });

  it('enables a disabled user', async () => {
    signInAsAdmin();
    const { user } = renderApp('/app/admin/users');

    await user.click(within(await openUserActions(user, 'bob@example.com')).getByRole('menuitem', { name: 'Enable' }));

    expect(await screen.findByText('Enabled bob@example.com. They can sign in again.')).toBeInTheDocument();
  });

  it('deletes a user only after DELETE has been typed', async () => {
    signInAsAdmin();
    let deleted: string | undefined;
    server.use(http.delete(api('/admin/users/:id'), ({ params }) => {
      deleted = String(params.id);
      return new HttpResponse(null, { status: 204 });
    }));
    const { user } = renderApp('/app/admin/users');

    await user.click(within(await openUserActions(user, 'alice@example.com')).getByRole('menuitem', { name: 'Delete' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete user?' });
    expect(dialog).toHaveTextContent('alice@example.com');
    expect(dialog).toHaveTextContent('profile & settings, watchlists and sessions');
    expect(dialog).toHaveTextContent('cannot be undone');
    expect(dialog).toHaveTextContent('Consider disabling the account instead');
    const confirm = within(dialog).getByRole('button', { name: 'Delete user' });
    expect(confirm).toBeDisabled();

    const phrase = within(dialog).getByLabelText('Type DELETE to confirm');
    await user.type(phrase, 'delete');
    expect(confirm).toBeDisabled();
    await user.clear(phrase);
    await user.type(phrase, 'DELETE');
    expect(confirm).toBeEnabled();
    expect(deleted).toBeUndefined();

    await user.click(confirm);
    await waitFor(() => expect(deleted).toBe(ALICE_ID));
    expect(await screen.findByText('Deleted alice@example.com and all of their data')).toBeInTheDocument();
  });

  it('offers disabling instead of deleting', async () => {
    signInAsAdmin();
    const { user } = renderApp('/app/admin/users');

    await user.click(within(await openUserActions(user, 'alice@example.com')).getByRole('menuitem', { name: 'Delete' }));
    await user.click(within(await screen.findByRole('dialog', { name: 'Delete user?' })).getByRole('button', { name: 'Disable instead' }));

    expect(await screen.findByRole('dialog', { name: 'Disable alice@example.com?' })).toBeInTheDocument();
  });
});
