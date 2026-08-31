import type { Role } from './auth';

export interface UserProfile {
  id: string;
  email: string;
  role: Role;
  displayName: string;
  timeZone: string;
  memberSince: string;
  updatedAt: string;
}

export interface UpdateProfileRequest {
  displayName?: string;
  timeZone?: string;
}
