import Alert from '@mui/material/Alert';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { AuthLayout } from './AuthLayout';
import { useAuth } from './authContext';
import { ChangePasswordForm } from './ChangePasswordForm';

/**
 * Shown while an administrator-issued temporary password must be replaced. The password-change guard opens the app
 * once the new session no longer requires a change; this page never navigates itself.
 */
export function ChangePasswordPage() {
  const auth = useAuth();
  return (
    <AuthLayout title="Choose a new password">
      <Stack spacing={2.5}>
        <Alert severity="info">
          An administrator set a temporary password for <strong>{auth.user?.email}</strong>. Choose a new password to
          continue. Your other sessions will be signed out.
        </Alert>
        <ChangePasswordForm currentPasswordLabel="Temporary password" submitLabel="Change password" autoFocus fullWidthSubmit />
        <Typography variant="body2" sx={{ textAlign: 'center' }}>
          Not now?{' '}
          <Link component="button" type="button" variant="body2" onClick={() => void auth.logout()}>Sign out</Link>
        </Typography>
      </Stack>
    </AuthLayout>
  );
}
