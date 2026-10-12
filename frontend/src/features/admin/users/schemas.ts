import { z } from 'zod';
import { passwordPolicySchema } from '@/features/auth/schemas';

/** Mirrors the backend rules: display name 1–100 characters (trimmed), e-mail valid and at most 320 characters. */
const displayName = z.string().trim()
  .min(1, { error: 'Enter a display name' })
  .max(100, { error: 'Use at most 100 characters' });
const email = z.string().trim()
  .min(1, { error: 'Enter an email address' })
  .max(320, { error: 'Use at most 320 characters' })
  .pipe(z.email({ error: 'Enter a valid email address' }));
const role = z.enum(['USER', 'ADMIN']);
const status = z.enum(['ACTIVE', 'DISABLED', 'LOCKED']);

export const createUserSchema = z.object({
  displayName,
  email,
  role,
  status,
  temporaryPassword: passwordPolicySchema,
  requirePasswordChange: z.boolean(),
});
export type CreateUserFormValues = z.infer<typeof createUserSchema>;

export const editUserSchema = z.object({ displayName, email, role, status });
export type EditUserFormValues = z.infer<typeof editUserSchema>;

export const resetPasswordSchema = z.object({
  temporaryPassword: passwordPolicySchema,
  requirePasswordChange: z.boolean(),
});
export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;
