import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '@/api/queryKeys';
import { userApi } from '@/api/userApi';
import { useAuth } from '@/features/auth/authContext';
import type { UpdateProfileRequest } from '@/models/user';

export function useCurrentUserProfile() {
  const { status } = useAuth();
  return useQuery({
    queryKey: queryKeys.currentUser,
    queryFn: userApi.me,
    enabled: status === 'authenticated',
    staleTime: 5 * 60_000,
  });
}

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: UpdateProfileRequest) => userApi.updateMe(request),
    onSuccess: (profile) => queryClient.setQueryData(queryKeys.currentUser, profile),
  });
}
