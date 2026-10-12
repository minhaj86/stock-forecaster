import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import { useMemo, useState } from 'react';
import { useForm, type FieldPath } from 'react-hook-form';
import { toApiError } from '@/api/apiError';
import { FormTextField } from '@/components/forms/FormTextField';
import { useAuth } from './authContext';
import { createChangePasswordSchema, type ChangePasswordFormValues } from './schemas';

const FIELDS: FieldPath<ChangePasswordFormValues>[] = ['currentPassword', 'newPassword', 'confirmPassword'];

interface ChangePasswordFormProps {
  /** "Current password" on the profile, "Temporary password" when an administrator issued one. */
  currentPasswordLabel: string;
  submitLabel: string;
  /** Called once the password has been changed (the auth state already holds the new session). */
  onChanged?: () => void;
  autoFocus?: boolean;
  fullWidthSubmit?: boolean;
}

/**
 * Replaces the signed-in user's password. It never navigates: where the user goes next is up to the route guards
 * (the forced change) or the hosting page.
 */
export function ChangePasswordForm(props: ChangePasswordFormProps) {
  const { currentPasswordLabel, submitLabel, onChanged, autoFocus, fullWidthSubmit } = props;
  const auth = useAuth();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const schema = useMemo(
    () => createChangePasswordSchema(`Enter your ${currentPasswordLabel.toLowerCase()}`),
    [currentPasswordLabel],
  );
  const { control, handleSubmit, reset, setError, formState: { isSubmitting } } = useForm<ChangePasswordFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
    mode: 'onTouched',
  });

  const onSubmit = async (values: ChangePasswordFormValues) => {
    setSubmitError(null);
    try {
      await auth.changePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword });
      reset();
      onChanged?.();
    } catch (error) {
      const apiError = toApiError(error);
      // An ended session signs the user out; the guards take over from here.
      if (apiError.status === 401) return;
      if (apiError.code === 'INVALID_CURRENT_PASSWORD') {
        setError('currentPassword', { message: apiError.message }, { shouldFocus: true });
      } else if (apiError.code === 'PASSWORD_POLICY_VIOLATION' || apiError.code === 'PASSWORD_UNCHANGED') {
        setError('newPassword', { message: apiError.message }, { shouldFocus: true });
      } else if (apiError.fieldErrors.some(({ field }) => FIELDS.includes(field as FieldPath<ChangePasswordFormValues>))) {
        apiError.fieldErrors.forEach(({ field, message }) => {
          const target = FIELDS.find((name) => name === field);
          if (target) setError(target, { message });
        });
      } else {
        setSubmitError(apiError.message);
      }
    }
  };

  return (
    <Stack component="form" spacing={2.5} noValidate onSubmit={handleSubmit(onSubmit)} aria-label="Change password form">
      {submitError && <Alert severity="error" role="alert">{submitError}</Alert>}
      <FormTextField
        control={control}
        name="currentPassword"
        label={currentPasswordLabel}
        type="password"
        autoComplete="current-password"
        autoFocus={autoFocus}
      />
      <FormTextField
        control={control}
        name="newPassword"
        label="New password"
        type="password"
        autoComplete="new-password"
        helperText="At least 12 characters. A memorable passphrase works well."
      />
      <FormTextField control={control} name="confirmPassword" label="Confirm new password" type="password" autoComplete="new-password" />
      <Stack direction="row">
        <Button type="submit" variant="contained" loading={isSubmitting} fullWidth={fullWidthSubmit} size={fullWidthSubmit ? 'large' : 'medium'}>
          {submitLabel}
        </Button>
      </Stack>
    </Stack>
  );
}
