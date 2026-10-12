import GppMaybeOutlinedIcon from '@mui/icons-material/GppMaybeOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Typography from '@mui/material/Typography';
import { Link as RouterLink, Outlet } from 'react-router';
import { useAuth } from '@/features/auth/authContext';

/** In-shell 403 state; the admin page (and its API calls) never render for non-administrators. */
export function AdminAccessDenied() {
  return (
    <Card>
      <Box sx={{ textAlign: 'center', py: { xs: 5, md: 7 }, px: 3, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5 }}>
        <Box sx={{ color: 'warning.main', '& svg': { fontSize: 48 } }} aria-hidden><GppMaybeOutlinedIcon /></Box>
        <Typography variant="h5" component="h1">403 – You do not have access to the admin console</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 480 }}>
          The admin console is available to administrators only. If you need access, ask an administrator of your
          StockLens installation.
        </Typography>
        <Button variant="contained" component={RouterLink} to="/app/dashboard" sx={{ mt: 1 }}>Back to dashboard</Button>
      </Box>
    </Card>
  );
}

/**
 * Guards the `/app/admin` routes for a better experience only: the backend authorises every admin request itself
 * (role ADMIN and an active account).
 */
export function AdminRoute() {
  const { user } = useAuth();
  return user?.role === 'ADMIN' ? <Outlet /> : <AdminAccessDenied />;
}
