import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import { useState } from 'react';
import { Controller, useForm, type FieldPath } from 'react-hook-form';
import { useNavigate } from 'react-router';
import { useNotify } from '@/app/providers/notificationContext';
import { FormTextField } from '@/components/forms/FormTextField';
import { ACCOUNT_STATUSES, ACCOUNT_STATUS_LABELS, ROLES, ROLE_LABELS } from '../labels';
import { adminPaths } from '../paths';
import { useCreateUser } from './hooks';
import { createUserSchema, type CreateUserFormValues } from './schemas';
import { TemporaryPasswordField } from './TemporaryPasswordField';
import { applyUserFormError } from './userFormErrors';

const FIELDS: FieldPath<CreateUserFormValues>[] = ['displayName', 'email', 'role', 'status', 'temporaryPassword'];

const DEFAULTS: CreateUserFormValues = {
  displayName: '', email: '', role: 'USER', status: 'ACTIVE', temporaryPassword: '', requirePasswordChange: true,
};

function CreateUserForm({ onClose }: { onClose: () => void }) {
  const createUser = useCreateUser();
  const notify = useNotify();
  const navigate = useNavigate();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const { control, handleSubmit, setError, formState: { isSubmitting } } = useForm<CreateUserFormValues>({
    resolver: zodResolver(createUserSchema),
    defaultValues: DEFAULTS,
    mode: 'onTouched',
  });

  const submit = async (values: CreateUserFormValues) => {
    setSubmitError(null);
    try {
      const user = await createUser.mutateAsync(values);
      notify({
        message: `Created ${user.email}${values.requirePasswordChange ? '. They must change the password at first sign-in.' : ''}`,
        action: { label: 'View', onClick: () => navigate(adminPaths.user(user.id)) },
      });
      onClose();
    } catch (error) {
      setSubmitError(applyUserFormError(error, FIELDS, setError));
    }
  };

  return (
    <form noValidate onSubmit={handleSubmit(submit)} aria-label="Add user form">
      <DialogTitle id="create-user-title">Add user</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ pt: 1 }}>
          {submitError && <Alert severity="error" role="alert">{submitError}</Alert>}
          <FormTextField control={control} name="displayName" label="Display name" autoComplete="off" autoFocus />
          <FormTextField control={control} name="email" label="Email" type="email" autoComplete="off" />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <FormTextField control={control} name="role" label="Role" select>
              {ROLES.map((role) => <MenuItem key={role} value={role}>{ROLE_LABELS[role]}</MenuItem>)}
            </FormTextField>
            <FormTextField control={control} name="status" label="Status" select>
              {ACCOUNT_STATUSES.map((status) => <MenuItem key={status} value={status}>{ACCOUNT_STATUS_LABELS[status]}</MenuItem>)}
            </FormTextField>
          </Stack>
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
                label="Require password change on first login"
              />
            )}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" variant="contained" loading={isSubmitting}>Create user</Button>
      </DialogActions>
    </form>
  );
}

/** The form mounts with the dialog, so every opening starts empty (no password lingers in memory). */
export function CreateUserDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="create-user-title">
      <CreateUserForm onClose={onClose} />
    </Dialog>
  );
}
