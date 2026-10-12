import type { FieldPath, FieldValues, UseFormSetError } from 'react-hook-form';
import { toApiError } from '@/api/apiError';

/** Backend codes that belong to one form field. */
const CODE_FIELDS: Record<string, string> = {
  EMAIL_ALREADY_REGISTERED: 'email',
  PASSWORD_POLICY_VIOLATION: 'temporaryPassword',
  INVALID_DISPLAY_NAME: 'displayName',
};

/**
 * Shows a failed user mutation on the matching form field(s). Returns the backend message when the error concerns no
 * field of this form (e.g. `LAST_ADMINISTRATOR`, `SELF_ADMINISTRATION_NOT_ALLOWED`), so it can be shown as an alert.
 */
export function applyUserFormError<T extends FieldValues>(
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
  return violations.length > 0 ? null : apiError.message;
}
