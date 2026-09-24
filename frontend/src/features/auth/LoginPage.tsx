import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Link as RouterLink } from 'react-router';
import { toApiError } from '@/api/apiError';
import { FormTextField } from '@/components/forms/FormTextField';
import { AuthLayout } from './AuthLayout';
import { useAuth } from './authContext';
import { loginSchema, type LoginFormValues } from './schemas';

function messageFor(error: unknown): string {
  const apiError = toApiError(error);
  if (apiError.code === 'INVALID_CREDENTIALS') return 'Incorrect email or password.';
  return apiError.message;
}

export function LoginPage() {
  const auth = useAuth();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [forgotOpen, setForgotOpen] = useState(false);
  const { control, handleSubmit, formState: { isSubmitting } } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '', rememberMe: true },
  });

  const onSubmit = async (values: LoginFormValues) => {
    setSubmitError(null);
    try {
      // On success the public-route guard takes the user back to where they were going.
      await auth.login(values);
    } catch (error) {
      setSubmitError(messageFor(error));
    }
  };

  return (
    <AuthLayout title="Sign in">
      <Stack component="form" spacing={2.5} noValidate onSubmit={handleSubmit(onSubmit)} aria-label="Sign in form">
        {auth.sessionExpired && !submitError && (
          <Alert severity="info">Your session has ended. Please sign in again.</Alert>
        )}
        {submitError && <Alert severity="error" role="alert">{submitError}</Alert>}
        <FormTextField control={control} name="email" label="Email" type="email" autoComplete="email" autoFocus />
        <FormTextField control={control} name="password" label="Password" type="password" autoComplete="current-password" />
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Controller
            name="rememberMe"
            control={control}
            render={({ field }) => (
              <FormControlLabel
                control={<Checkbox checked={field.value} onChange={(event) => field.onChange(event.target.checked)} />}
                label="Remember me"
              />
            )}
          />
          <Link component="button" type="button" variant="body2" onClick={() => setForgotOpen(true)}>
            Forgot password?
          </Link>
        </Box>
        <Button type="submit" variant="contained" size="large" loading={isSubmitting} fullWidth>
          Sign in
        </Button>
        <Typography variant="body2" sx={{ textAlign: 'center' }}>
          Don&apos;t have an account?{' '}
          <Link component={RouterLink} to="/register">Create one</Link>
        </Typography>
      </Stack>
      <Dialog open={forgotOpen} onClose={() => setForgotOpen(false)} aria-labelledby="forgot-password-title">
        <DialogTitle id="forgot-password-title">Password reset</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Self-service password reset is not available yet. Please contact your administrator to regain access.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setForgotOpen(false)} autoFocus>Close</Button>
        </DialogActions>
      </Dialog>
    </AuthLayout>
  );
}
