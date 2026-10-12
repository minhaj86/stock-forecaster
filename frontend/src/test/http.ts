import { HttpResponse } from 'msw';

/** Absolute pattern for an API path, matching any origin. */
export const api = (path: string) => `*/api/v1${path}`;

export function apiError(status: number, code: string, message: string, extra: Record<string, unknown> = {}) {
  return HttpResponse.json({ timestamp: '2026-10-11T13:00:00Z', status, code, message, correlationId: 'corr-123', ...extra }, { status });
}
