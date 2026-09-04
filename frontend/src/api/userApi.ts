import type { UpdateProfileRequest, UserProfile } from '@/models/user';
import { httpClient } from './httpClient';

export const userApi = {
  me: async (): Promise<UserProfile> => (await httpClient.get<UserProfile>('/users/me')).data,
  updateMe: async (request: UpdateProfileRequest): Promise<UserProfile> =>
    (await httpClient.patch<UserProfile>('/users/me', request)).data,
};
