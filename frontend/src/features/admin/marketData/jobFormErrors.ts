import type { FieldPath, FieldValues, UseFormSetError } from 'react-hook-form';
import { toApiError } from '@/api/apiError';
import { friendlyError } from '../errors';

/** Backend codes of job requests that belong to a form field. */
const CODE_FIELDS: Record<string, string> = {
  INVALID_DATE_RANGE: 'toDate',
  UNSUPPORTED_MARKET_DATA_INTERVAL: 'interval',
};

/** Puts a failed job request on the matching field; otherwise returns the message for an alert. */
export function applyJobFormError<T extends FieldValues>(
  error: unknown, fields: readonly FieldPath<T>[], setError: UseFormSetError<T>,
): string | null {
  const apiError = toApiError(error);
  const codeField = fields.find((field) => field === CODE_FIELDS[apiError.code]);
  if (codeField) {
    setError(codeField, { message: apiError.message }, { shouldFocus: true });
    return null;
  }
  const violations = apiError.fieldErrors.flatMap(({ field, message }) => {
    const target = fields.find((name) => name === field);
    return target ? [{ target, message }] : [];
  });
  violations.forEach(({ target, message }) => setError(target, { message }));
  return violations.length > 0 ? null : friendlyError(apiError).message;
}
