import { http, HttpResponse } from 'msw';
import { adminSession } from './adminFixtures';
import { profile } from './fixtures';
import { api } from './http';
import { server } from './server';

/** Restores the session as an administrator (the default MSW session is a regular user). */
export function signInAsAdmin(): void {
  server.use(
    http.post(api('/auth/refresh'), () => HttpResponse.json(adminSession())),
    http.get(api('/users/me'), () => HttpResponse.json({ ...profile, role: 'ADMIN' })),
  );
}
