import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import { useState } from 'react';
import { useForm, type FieldPath } from 'react-hook-form';
import { useNotify } from '@/app/providers/notificationContext';
import { FormTextField } from '@/components/forms/FormTextField';
import { useAuth } from '@/features/auth/authContext';
import type { AdminUser, UpdateUserRequest } from '@/models/admin';
import { useRetainedValue } from '../components/useRetainedValue';
import { ACCOUNT_STATUSES, ACCOUNT_STATUS_LABELS, ROLES, ROLE_LABELS } from '../labels';
import { useUpdateUser } from './hooks';
import { editUserSchema, type EditUserFormValues } from './schemas';
import { applyUserFormError } from './userFormErrors';

const FIELDS: FieldPath<EditUserFormValues>[] = ['displayName', 'email', 'role', 'status'];

/** Only changed fields are sent, so editing a name never touches role or status. */
function changesOf(user: AdminUser, values: EditUserFormValues): UpdateUserRequest {
  const changes: UpdateUserRequest = {};
  if (values.displayName !== (user.displayName ?? '')) changes.displayName = values.displayName;
  if (values.email !== user.email) changes.email = values.email;
  if (values.role !== user.role) changes.role = values.role;
  if (values.status !== user.status) changes.status = values.status;
  return changes;
}

function EditUserForm({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const auth = useAuth();
  const updateUser = useUpdateUser();
  const notify = useNotify();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const self = auth.user?.id === user.id;
  const { control, handleSubmit, setError, formState: { isSubmitting, isDirty } } = useForm<EditUserFormValues>({
    resolver: zodResolver(editUserSchema),
    defaultValues: { displayName: user.displayName ?? '', email: user.email, role: user.role, status: user.status },
    mode: 'onTouched',
  });

  const submit = async (values: EditUserFormValues) => {
    setSubmitError(null);
    const changes = changesOf(user, values);
    if (Object.keys(changes).length === 0) {
      onClose();
      return;
    }
    try {
      const saved = await updateUser.mutateAsync({ id: user.id, request: changes });
      notify({ message: `Saved changes to ${saved.email}` });
      onClose();
    } catch (error) {
      setSubmitError(applyUserFormError(error, FIELDS, setError));
    }
  };

  return (
    <form noValidate onSubmit={handleSubmit(submit)} aria-label="Edit user form">
      <DialogTitle id="edit-user-title">Edit user</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ pt: 1 }}>
          {submitError && <Alert severity="error" role="alert">{submitError}</Alert>}
          <FormTextField control={control} name="displayName" label="Display name" autoComplete="off" autoFocus />
          <FormTextField control={control} name="email" label="Email" type="email" autoComplete="off" />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <FormTextField control={control} name="role" label="Role" select disabled={self}>
              {ROLES.map((role) => <MenuItem key={role} value={role}>{ROLE_LABELS[role]}</MenuItem>)}
            </FormTextField>
            <FormTextField control={control} name="status" label="Status" select disabled={self}>
              {ACCOUNT_STATUSES.map((status) => <MenuItem key={status} value={status}>{ACCOUNT_STATUS_LABELS[status]}</MenuItem>)}
            </FormTextField>
          </Stack>
          {self && (
            <Alert severity="info" variant="outlined">
              You cannot change the role or status of your own account. Ask another administrator if needed.
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" variant="contained" loading={isSubmitting} disabled={!isDirty}>Save changes</Button>
      </DialogActions>
    </form>
  );
}

export function EditUserDialog({ user, onClose }: { user: AdminUser | null; onClose: () => void }) {
  const shown = useRetainedValue(user);
  return (
    <Dialog open={user !== null} onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="edit-user-title">
      {shown && <EditUserForm key={shown.id} user={shown} onClose={onClose} />}
    </Dialog>
  );
}
