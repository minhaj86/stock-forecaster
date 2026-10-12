import { z } from 'zod';

const utf8Bytes = (value: string) => new TextEncoder().encode(value).length;

export const loginSchema = z.object({
  email: z.string().trim().min(1, { error: 'Enter your email address' }).pipe(z.email({ error: 'Enter a valid email address' })),
  password: z.string().min(1, { error: 'Enter your password' }),
  rememberMe: z.boolean(),
});

export type LoginFormValues = z.infer<typeof loginSchema>;

/** Mirrors the backend password policy: 12 characters minimum, 72 bytes maximum (BCrypt). */
export const passwordPolicySchema = z.string()
  .min(12, { error: 'Use at least 12 characters' })
  .refine((value) => utf8Bytes(value) <= 72, { error: 'Use at most 72 characters' });

export const registerSchema = z.object({
  displayName: z.string().trim().min(1, { error: 'Enter your name' }).max(100, { error: 'Use at most 100 characters' }),
  email: z.string().trim().min(1, { error: 'Enter your email address' }).pipe(z.email({ error: 'Enter a valid email address' })),
  password: passwordPolicySchema,
  confirmPassword: z.string().min(1, { error: 'Confirm your password' }),
}).refine((values) => values.password === values.confirmPassword, {
  path: ['confirmPassword'],
  error: 'Passwords do not match',
});

export type RegisterFormValues = z.infer<typeof registerSchema>;

/** The new password must satisfy the policy and differ from the current one (backend `PASSWORD_UNCHANGED`). */
export function createChangePasswordSchema(missingCurrentPasswordMessage: string) {
  return z.object({
    currentPassword: z.string().min(1, { error: missingCurrentPasswordMessage }),
    newPassword: passwordPolicySchema,
    confirmPassword: z.string().min(1, { error: 'Confirm your new password' }),
  }).refine((values) => values.newPassword === values.confirmPassword, {
    path: ['confirmPassword'],
    error: 'Passwords do not match',
  }).refine((values) => values.newPassword !== values.currentPassword, {
    path: ['newPassword'],
    error: 'Choose a password that differs from the current one',
  });
}

export type ChangePasswordFormValues = z.infer<ReturnType<typeof createChangePasswordSchema>>;
