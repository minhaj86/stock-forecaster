import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import { useState } from 'react';
import { useNotify } from '@/app/providers/notificationContext';
import type { AdminUser } from '@/models/admin';
import { ConfirmPhraseField } from '../components/ConfirmPhraseField';
import { useRetainedValue } from '../components/useRetainedValue';
import { errorMessage } from '../errors';
import { useDeleteUser } from './hooks';

const PHRASE = 'DELETE';

interface DeleteUserContentProps {
  user: AdminUser;
  onClose: () => void;
  onDeleted?: (user: AdminUser) => void;
  /** Offered as the reversible alternative while the account is not disabled yet. */
  onDisableInstead?: (user: AdminUser) => void;
}

function DeleteUserContent({ user, onClose, onDeleted, onDisableInstead }: DeleteUserContentProps) {
  const deleteUser = useDeleteUser();
  const notify = useNotify();
  const [phrase, setPhrase] = useState('');
  const [error, setError] = useState<string | null>(null);
  const confirmed = phrase === PHRASE;

  const remove = async () => {
    setError(null);
    try {
      await deleteUser.mutateAsync(user.id);
      notify({ message: `Deleted ${user.email} and all of their data` });
      onDeleted?.(user);
      onClose();
    } catch (failure) {
      setError(errorMessage(failure));
    }
  };

  return (
    <>
      <DialogTitle id="delete-user-title">Delete user?</DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          <DialogContentText component="div">
            <strong>{user.email}</strong>
          </DialogContentText>
          <DialogContentText>
            This permanently removes the account, its profile &amp; settings, watchlists and sessions. This cannot be
            undone.
          </DialogContentText>
          <Alert severity="info" variant="outlined"
            action={onDisableInstead && user.status !== 'DISABLED' ? (
              <Button color="inherit" size="small" onClick={() => onDisableInstead(user)}>Disable instead</Button>
            ) : undefined}>
            Consider disabling the account instead: a disabled user keeps their data and can be enabled again.
          </Alert>
          {error && <Alert severity="error" role="alert">{error}</Alert>}
          <ConfirmPhraseField phrase={PHRASE} value={phrase} onChange={setPhrase} disabled={deleteUser.isPending} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={deleteUser.isPending}>Cancel</Button>
        <Button variant="contained" color="error" disabled={!confirmed} loading={deleteUser.isPending} onClick={() => void remove()}>
          Delete user
        </Button>
      </DialogActions>
    </>
  );
}

interface DeleteUserDialogProps extends Omit<DeleteUserContentProps, 'user'> {
  user: AdminUser | null;
}

/** Deletion needs the user to type DELETE, so it can never happen with a single click. */
export function DeleteUserDialog({ user, onClose, ...props }: DeleteUserDialogProps) {
  const shown = useRetainedValue(user);
  return (
    <Dialog open={user !== null} onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="delete-user-title">
      {shown && <DeleteUserContent key={shown.id} user={shown} onClose={onClose} {...props} />}
    </Dialog>
  );
}
