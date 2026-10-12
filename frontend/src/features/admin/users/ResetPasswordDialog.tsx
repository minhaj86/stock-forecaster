import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import { useState } from 'react';
import { Controller, useForm, type FieldPath } from 'react-hook-form';
import { useNotify } from '@/app/providers/notificationContext';
import type { AdminUser } from '@/models/admin';
import { useRetainedValue } from '../components/useRetainedValue';
import { useResetUserPassword } from './hooks';
import { resetPasswordSchema, type ResetPasswordFormValues } from './schemas';
import { TemporaryPasswordField } from './TemporaryPasswordField';
import { applyUserFormError } from './userFormErrors';

const FIELDS: FieldPath<ResetPasswordFormValues>[] = ['temporaryPassword'];

function ResetPasswordForm({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const resetPassword = useResetUserPassword();
  const notify = useNotify();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const { control, handleSubmit, setError, formState: { isSubmitting } } = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { temporaryPassword: '', requirePasswordChange: true },
    mode: 'onTouched',
  });

  const submit = async (values: ResetPasswordFormValues) => {
    setSubmitError(null);
    try {
      await resetPassword.mutateAsync({ id: user.id, request: values });
      notify({ message: `Password reset for ${user.email}. All of their sessions have been signed out.` });
      onClose();
    } catch (error) {
      setSubmitError(applyUserFormError(error, FIELDS, setError));
    }
  };

  return (
    <form noValidate onSubmit={handleSubmit(submit)} aria-label="Reset password form">
      <DialogTitle id="reset-password-title">Reset password</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5}>
          <DialogContentText>
            Set a temporary password for <strong>{user.email}</strong>. They are signed out on all devices.
          </DialogContentText>
          {submitError && <Alert severity="error" role="alert">{submitError}</Alert>}
          <Controller
            name="temporaryPassword"
            control={control}
            render={({ field, fieldState }) => (
              <TemporaryPasswordField value={field.value} onChange={field.onChange} onBlur={field.onBlur}
                inputRef={field.ref} error={fieldState.error?.message} />
            )}
          />
          <Controller
            name="requirePasswordChange"
            control={control}
            render={({ field }) => (
              <FormControlLabel
                control={<Checkbox checked={field.value} onChange={(event) => field.onChange(event.target.checked)} />}
                label="Require password change at next sign-in"
              />
            )}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" variant="contained" loading={isSubmitting}>Reset password</Button>
      </DialogActions>
    </form>
  );
}

export function ResetPasswordDialog({ user, onClose }: { user: AdminUser | null; onClose: () => void }) {
  const shown = useRetainedValue(user);
  return (
    <Dialog open={user !== null} onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="reset-password-title">
      {shown && <ResetPasswordForm key={shown.id} user={shown} onClose={onClose} />}
    </Dialog>
  );
}
