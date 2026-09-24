import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useForm, type FieldPath } from 'react-hook-form';
import { Link as RouterLink } from 'react-router';
import { toApiError } from '@/api/apiError';
import { FormTextField } from '@/components/forms/FormTextField';
import { AuthLayout } from './AuthLayout';
import { useAuth } from './authContext';
import { registerSchema, type RegisterFormValues } from './schemas';

const FIELDS: FieldPath<RegisterFormValues>[] = ['displayName', 'email', 'password', 'confirmPassword'];

export function RegisterPage() {
  const auth = useAuth();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const { control, handleSubmit, setError, formState: { isSubmitting } } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { displayName: '', email: '', password: '', confirmPassword: '' },
    mode: 'onTouched',
  });

  const onSubmit = async (values: RegisterFormValues) => {
    setSubmitError(null);
    try {
      // The backend signs the user in right after registration; the public-route guard then opens the dashboard.
      await auth.register({ displayName: values.displayName, email: values.email, password: values.password });
    } catch (error) {
      const apiError = toApiError(error);
      if (apiError.code === 'EMAIL_ALREADY_REGISTERED') {
        setError('email', { message: 'An account with this email already exists' }, { shouldFocus: true });
      } else if (apiError.code === 'PASSWORD_POLICY_VIOLATION') {
        setError('password', { message: apiError.message }, { shouldFocus: true });
      } else if (apiError.fieldErrors.length > 0) {
        apiError.fieldErrors.forEach(({ field, message }) => {
          const target = FIELDS.find((name) => name === field);
          if (target) setError(target, { message });
        });
      } else {
        setSubmitError(apiError.message);
      }
    }
  };

  return (
    <AuthLayout title="Create your account">
      <Stack component="form" spacing={2.5} noValidate onSubmit={handleSubmit(onSubmit)} aria-label="Registration form">
        {submitError && <Alert severity="error" role="alert">{submitError}</Alert>}
        <FormTextField control={control} name="displayName" label="Name" autoComplete="name" autoFocus />
        <FormTextField control={control} name="email" label="Email" type="email" autoComplete="email" />
        <FormTextField
          control={control}
          name="password"
          label="Password"
          type="password"
          autoComplete="new-password"
          helperText="At least 12 characters. A memorable passphrase works well."
        />
        <FormTextField control={control} name="confirmPassword" label="Confirm password" type="password" autoComplete="new-password" />
        <Button type="submit" variant="contained" size="large" loading={isSubmitting} fullWidth>
          Create account
        </Button>
        <Typography variant="body2" sx={{ textAlign: 'center' }}>
          Already have an account? <Link component={RouterLink} to="/login">Sign in</Link>
        </Typography>
      </Stack>
    </AuthLayout>
  );
}
