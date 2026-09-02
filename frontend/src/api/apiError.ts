import { isAxiosError } from 'axios';
import type { ApiErrorBody, FieldViolation } from '@/models/api';

/**
 * Normalised error for every failed API call: backend envelopes keep their code/message, transport failures get
 * actionable messages. TanStack Query surfaces these instances to components.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldViolation[];
  readonly correlationId?: string;
  readonly details: Record<string, unknown>;

  constructor(status: number, code: string, message: string, fieldErrors: FieldViolation[] = [], correlationId?: string,
    details: Record<string, unknown> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.correlationId = correlationId;
    this.details = details;
  }

  get isNetworkError(): boolean {
    return this.status === 0;
  }
}

function isApiErrorBody(data: unknown): data is ApiErrorBody {
  return typeof data === 'object' && data !== null && 'code' in data && 'message' in data;
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  if (isAxiosError(error)) {
    const response = error.response;
    if (response && isApiErrorBody(response.data)) {
      const body = response.data;
      return new ApiError(response.status, body.code, body.message, body.errors ?? [], body.correlationId, body.details ?? {});
    }
    if (response) {
      return new ApiError(response.status, `HTTP_${response.status}`,
        response.status >= 500 ? 'The server could not complete the request. Please try again shortly.'
          : 'The request could not be completed.',
        [], response.headers?.['x-correlation-id'] as string | undefined);
    }
    if (error.code === 'ECONNABORTED') {
      return new ApiError(0, 'TIMEOUT', 'The server took too long to respond. Please try again.');
    }
    return new ApiError(0, 'NETWORK_ERROR', 'Unable to reach the server. Check your connection and try again.');
  }
  return new ApiError(0, 'UNEXPECTED', error instanceof Error ? error.message : 'Unexpected error');
}
