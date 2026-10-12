import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation, type Location } from 'react-router';
import { useAuth } from '@/features/auth/authContext';

type FromState = { from?: Pick<Location, 'pathname' | 'search'> } | null;

/** Where the user was going before a guard intervened, or the dashboard. */
function destinationOf(location: Location): string {
  const from = (location.state as FromState)?.from;
  return from ? `${from.pathname}${from.search ?? ''}` : '/app/dashboard';
}

function SessionRestoring() {
  return (
    <Box role="status" sx={{ minHeight: '100dvh', display: 'grid', placeItems: 'center' }}>
      <Box sx={{ textAlign: 'center' }}>
        <CircularProgress aria-hidden />
        <Typography color="text.secondary" sx={{ mt: 2 }}>Restoring your session…</Typography>
      </Box>
    </Box>
  );
}

/**
 * Children render only for authenticated users; others are sent to the login page and brought back afterwards. Users
 * with an administrator-issued temporary password must choose a new one first.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { status, user } = useAuth();
  const location = useLocation();
  if (status === 'initializing') return <SessionRestoring />;
  if (status === 'unauthenticated') return <Navigate to="/login" replace state={{ from: location }} />;
  if (user?.passwordChangeRequired) return <Navigate to="/change-password" replace state={{ from: location }} />;
  return <>{children}</>;
}

/**
 * Login and registration are pointless for signed-in users. This guard is also what moves a user on after signing
 * in: back to the page that required authentication, or to the dashboard.
 */
export function PublicOnlyRoute() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'initializing') return <SessionRestoring />;
  if (status === 'authenticated') return <Navigate to={destinationOf(location)} replace />;
  return <Outlet />;
}

/**
 * The forced password change is only for signed-in users whose password must be replaced. Once it is changed, this
 * guard moves the user on to where they were going.
 */
export function PasswordChangeRoute() {
  const { status, user } = useAuth();
  const location = useLocation();
  if (status === 'initializing') return <SessionRestoring />;
  if (status === 'unauthenticated') return <Navigate to="/login" replace />;
  if (!user?.passwordChangeRequired) return <Navigate to={destinationOf(location)} replace />;
  return <Outlet />;
}
