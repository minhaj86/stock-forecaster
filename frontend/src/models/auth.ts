export type Role = 'USER' | 'ADMIN';

export interface UserSummary {
  id: string;
  email: string;
  role: Role;
  /** Set by an administrator: every API call except `/users/me` and `/auth/*` is refused until it is changed. */
  passwordChangeRequired: boolean;
}

/** The refresh token is never part of the body: it lives in an HttpOnly cookie. */
export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  expiresAt: string;
  user: UserSummary;
}

export interface LoginRequest {
  email: string;
  password: string;
  rememberMe: boolean;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

export interface ChangePasswordRequest {
  /** The current or administrator-issued temporary password. */
  currentPassword: string;
  newPassword: string;
}
