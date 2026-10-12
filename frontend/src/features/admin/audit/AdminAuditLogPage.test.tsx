import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { ALICE_ID, auditEvents, JOB_ID, page } from '@/test/adminFixtures';
import { api } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';
import { signInAsAdmin } from '@/test/signInAsAdmin';

function recordAuditRequests() {
  const requests: URLSearchParams[] = [];
  server.use(http.get(api('/admin/audit-log'), ({ request }) => {
    requests.push(new URL(request.url).searchParams);
    return HttpResponse.json(page(auditEvents));
  }));
  return requests;
}

describe('AdminAuditLogPage', () => {
  it('lists audit events and shows their details on demand', async () => {
    signInAsAdmin();
    const { user } = renderApp('/app/admin/audit-log');

    const table = await screen.findByRole('table', { name: 'Audit events' });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(4);
    expect(rows[1]).toHaveTextContent('ada@example.com');
    expect(rows[1]).toHaveTextContent('User disabled');
    expect(rows[1]).toHaveTextContent('Disabled alice@example.com');
    expect(within(rows[1]).getByRole('link', { name: '#0199d1c4' })).toHaveAttribute('href', `/app/admin/users/${ALICE_ID}`);
    expect(within(rows[3]).getByRole('link', { name: '#f82d1a2b' })).toHaveAttribute('href', `/app/admin/sync-jobs/${JOB_ID}`);

    const toggle = within(rows[1]).getByRole('button', { name: 'Details: Disabled alice@example.com' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await user.click(toggle);

    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    const details = document.getElementById(toggle.getAttribute('aria-controls') ?? '') as HTMLElement;
    expect(within(details).getByText('Previous status').nextElementSibling).toHaveTextContent('ACTIVE');
    expect(within(details).getByText('New status').nextElementSibling).toHaveTextContent('DISABLED');
    expect(details).toHaveTextContent('correlation c0ffee00-1234-5678-9abc-def012345678');
  });

  it('sends the selected filters', async () => {
    signInAsAdmin();
    const requests = recordAuditRequests();
    const { user } = renderApp('/app/admin/audit-log');

    await screen.findByRole('table', { name: 'Audit events' });
    expect(requests[0].toString()).toBe('page=0&size=20');

    await user.click(screen.getByRole('combobox', { name: /^Action/ }));
    await user.click(await screen.findByRole('option', { name: 'User disabled' }));
    await waitFor(() => expect(requests.at(-1)?.get('action')).toBe('USER_DISABLED'));

    await user.click(screen.getByRole('combobox', { name: /^Entity type/ }));
    await user.click(await screen.findByRole('option', { name: 'User' }));
    await waitFor(() => expect(requests.at(-1)?.get('entityType')).toBe('USER'));

    await user.type(screen.getByRole('searchbox', { name: 'Actor email' }), 'ada@');
    await waitFor(() => expect(requests.at(-1)?.get('actor')).toBe('ada@'));

    await user.type(screen.getByLabelText('From'), '2026-10-01');
    await waitFor(() => expect(requests.at(-1)?.get('from')).toBe('2026-10-01'));
    expect(requests.at(-1)?.get('action')).toBe('USER_DISABLED');
  });

  it('opens with the entity filter of a details page link', async () => {
    signInAsAdmin();
    const requests = recordAuditRequests();
    const { user } = renderApp(`/app/admin/audit-log?entityType=USER&entityId=${ALICE_ID}`);

    await screen.findByRole('table', { name: 'Audit events' });
    expect(requests[0].get('entityId')).toBe(ALICE_ID);
    expect(requests[0].get('entityType')).toBe('USER');

    await user.click(screen.getByRole('button', { name: 'Remove entity filter' }));
    await waitFor(() => expect(requests.at(-1)?.has('entityId')).toBe(false));
  });

  it('explains when there is no activity yet', async () => {
    signInAsAdmin();
    server.use(http.get(api('/admin/audit-log'), () => HttpResponse.json(page([]))));
    renderApp('/app/admin/audit-log');

    expect(await screen.findByText('No administrative activity yet')).toBeInTheDocument();
  });
});
