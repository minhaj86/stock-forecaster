export type Role = 'USER' | 'ADMIN';

export interface UserSummary {
  id: string;
  email: string;
  role: Role;
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
