import { useNotify } from '@/app/providers/notificationContext';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import type { AdminUser } from '@/models/admin';
import { useRetainedValue } from '../components/useRetainedValue';
import { errorMessage } from '../errors';
import { DeleteUserDialog } from './DeleteUserDialog';
import { EditUserDialog } from './EditUserDialog';
import { useDisableUser } from './hooks';
import { ResetPasswordDialog } from './ResetPasswordDialog';

export type UserActionKind = 'edit' | 'reset' | 'disable' | 'delete';
export interface UserAction {
  kind: UserActionKind;
  user: AdminUser;
}

interface UserActionDialogsProps {
  action: UserAction | null;
  onChange: (action: UserAction | null) => void;
  onDeleted?: (user: AdminUser) => void;
}

/** Dialogs behind the user actions of the list and the details page (one open at a time). */
export function UserActionDialogs({ action, onChange, onDeleted }: UserActionDialogsProps) {
  const disableUser = useDisableUser();
  const notify = useNotify();
  const target = (kind: UserActionKind) => (action?.kind === kind ? action.user : null);
  const disabling = useRetainedValue(target('disable'));
  const close = () => onChange(null);

  const confirmDisable = async () => {
    if (!disabling) return;
    try {
      await disableUser.mutateAsync(disabling.id);
      notify({ message: `Disabled ${disabling.email}. They have been signed out everywhere.` });
    } catch (error) {
      notify({ message: errorMessage(error), severity: 'error' });
    } finally {
      close();
    }
  };

  return (
    <>
      <EditUserDialog user={target('edit')} onClose={close} />
      <ResetPasswordDialog user={target('reset')} onClose={close} />
      <DeleteUserDialog
        user={target('delete')}
        onClose={close}
        onDeleted={onDeleted}
        onDisableInstead={(user) => onChange({ kind: 'disable', user })}
      />
      <ConfirmDialog
        open={action?.kind === 'disable'}
        title={`Disable ${disabling?.email ?? 'user'}?`}
        description="They are signed out everywhere and cannot sign in until the account is enabled again. Their data is kept."
        confirmLabel="Disable user"
        destructive
        pending={disableUser.isPending}
        onConfirm={() => void confirmDisable()}
        onClose={close}
      />
    </>
  );
}
