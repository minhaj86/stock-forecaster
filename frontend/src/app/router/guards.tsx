import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from '@/features/auth/authContext';

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

/** Children render only for authenticated users; others are sent to the login page and brought back afterwards. */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'initializing') return <SessionRestoring />;
  if (status === 'unauthenticated') return <Navigate to="/login" replace state={{ from: location }} />;
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
  if (status === 'authenticated') {
    const from = (location.state as { from?: { pathname: string; search?: string } } | null)?.from;
    return <Navigate to={from ? `${from.pathname}${from.search ?? ''}` : '/app/dashboard'} replace />;
  }
  return <Outlet />;
}
