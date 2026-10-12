import { useNotify } from '@/app/providers/notificationContext';
import type { AdminUser } from '@/models/admin';
import { errorMessage } from '../errors';
import { useEnableUser } from './hooks';

/** Enabling is reversible and needs no confirmation. */
export function useEnableUserAction() {
  const enableUser = useEnableUser();
  const notify = useNotify();
  const enable = async (user: AdminUser) => {
    try {
      await enableUser.mutateAsync(user.id);
      notify({ message: `Enabled ${user.email}. They can sign in again.` });
    } catch (error) {
      notify({ message: errorMessage(error), severity: 'error' });
    }
  };
  return { enable, pendingUserId: enableUser.isPending ? enableUser.variables : undefined };
}
