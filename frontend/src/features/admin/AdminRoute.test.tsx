import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';
import { signInAsAdmin } from '@/test/signInAsAdmin';

/** Records every admin API request, so tests can prove that none was made. */
function recordAdminRequests(): string[] {
  const requests: string[] = [];
  server.events.on('request:start', ({ request }) => {
    if (new URL(request.url).pathname.includes('/api/v1/admin/')) requests.push(request.url);
  });
  return requests;
}

async function openNavigation(user: ReturnType<typeof renderApp>['user']) {
  await user.click(await screen.findByRole('button', { name: 'Open navigation' }));
  return screen.findByRole('navigation', { name: 'Main navigation' });
}

describe('admin route protection', () => {
  afterEach(() => server.events.removeAllListeners());

  it.each(['/app/admin', '/app/admin/users', '/app/admin/sync-jobs/some-job'])(
    'shows a regular user a 403 state instead of %s', async (path) => {
      const adminRequests = recordAdminRequests();
      const { user } = renderApp(path);

      expect(await screen.findByRole('heading', { name: /403 – You do not have access to the admin console/ })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Back to dashboard' })).toHaveAttribute('href', '/app/dashboard');

      const navigation = await openNavigation(user);
      expect(within(navigation).queryByRole('list', { name: 'Administration' })).not.toBeInTheDocument();
      expect(within(navigation).queryByText('Admin')).not.toBeInTheDocument();
      expect(adminRequests).toEqual([]);
    },
  );

  it('shows administrators the admin section in the navigation and the overview', async () => {
    signInAsAdmin();
    const { user } = renderApp('/app/admin');

    expect(await screen.findByRole('heading', { name: 'Administration', level: 1 })).toBeInTheDocument();
    const navigation = await openNavigation(user);
    const adminSection = within(navigation).getByRole('list', { name: 'Administration' });
    expect(within(adminSection).getAllByRole('link').map((link) => link.textContent)).toEqual([
      'Overview', 'Users', 'Stocks', 'Market Data', 'Sync Jobs', 'Providers', 'Audit Log',
    ]);
    expect(within(adminSection).getByRole('link', { name: 'Overview' })).toHaveAttribute('aria-current', 'page');
  });

  it('marks only the open admin section as the current page', async () => {
    signInAsAdmin();
    const { user } = renderApp('/app/admin/users');

    expect(await screen.findByRole('heading', { name: 'Users', level: 1 })).toBeInTheDocument();
    const adminSection = within(await openNavigation(user)).getByRole('list', { name: 'Administration' });
    expect(within(adminSection).getByRole('link', { name: 'Users' })).toHaveAttribute('aria-current', 'page');
    expect(within(adminSection).getByRole('link', { name: 'Overview' })).not.toHaveAttribute('aria-current');
  });

  it('keeps the admin console reachable from the navigation', async () => {
    signInAsAdmin();
    const { user, router } = renderApp('/app/dashboard');

    const adminSection = within(await openNavigation(user)).getByRole('list', { name: 'Administration' });
    await user.click(within(adminSection).getByRole('link', { name: 'Audit Log' }));

    expect(await screen.findByRole('heading', { name: 'Audit log', level: 1 })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/app/admin/audit-log');
  });
});
