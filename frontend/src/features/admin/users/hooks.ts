import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminUsersApi } from '@/api/admin/adminUsersApi';
import { queryKeys } from '@/api/queryKeys';
import type {
  AdminUserDetails, AdminUserFilters, CreateUserRequest, ResetPasswordRequest, UpdateUserRequest,
} from '@/models/admin';
import { invalidateAdminUsers } from '../invalidation';

export function useAdminUsers(filters: AdminUserFilters) {
  return useQuery({
    queryKey: queryKeys.admin.users(filters),
    queryFn: () => adminUsersApi.list(filters),
    placeholderData: keepPreviousData,
  });
}

export function useAdminUser(id: string) {
  return useQuery({
    queryKey: queryKeys.admin.user(id),
    queryFn: () => adminUsersApi.get(id),
    enabled: id.length > 0,
  });
}

export function useUserStatistics() {
  return useQuery({ queryKey: queryKeys.admin.userStatistics, queryFn: adminUsersApi.statistics });
}

/** Stores the returned user and refetches lists, statistics, the overview and the audit log. */
function useUserMutation<Variables>(mutationFn: (variables: Variables) => Promise<AdminUserDetails>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (user) => {
      queryClient.setQueryData(queryKeys.admin.user(user.id), user);
      return invalidateAdminUsers(queryClient);
    },
  });
}

export function useCreateUser() {
  return useUserMutation((request: CreateUserRequest) => adminUsersApi.create(request));
}

export function useUpdateUser() {
  return useUserMutation(({ id, request }: { id: string; request: UpdateUserRequest }) => adminUsersApi.update(id, request));
}

export function useDisableUser() {
  return useUserMutation((id: string) => adminUsersApi.disable(id));
}

export function useEnableUser() {
  return useUserMutation((id: string) => adminUsersApi.enable(id));
}

export function useResetUserPassword() {
  return useUserMutation(({ id, request }: { id: string; request: ResetPasswordRequest }) =>
    adminUsersApi.resetPassword(id, request));
}

export function useDeleteUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => adminUsersApi.remove(id),
    onSuccess: (_, id) => {
      // Dropped first, so an open details page is not refetched into a "not found" state.
      queryClient.removeQueries({ queryKey: queryKeys.admin.user(id), exact: true });
      return invalidateAdminUsers(queryClient);
    },
  });
}
