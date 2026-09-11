import SearchOffIcon from '@mui/icons-material/SearchOff';
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import { Link as RouterLink, useRouteError } from 'react-router';
import { EmptyState } from '@/components/common/EmptyState';

export function NotFoundPage() {
  return (
    <Box sx={{ py: 6 }}>
      <EmptyState
        icon={<SearchOffIcon />}
        title="Page not found"
        description="The page you are looking for does not exist or has moved."
        action={<Button variant="contained" component={RouterLink} to="/app/dashboard">Go to dashboard</Button>}
      />
    </Box>
  );
}

/** Last-resort boundary for rendering errors inside a route. */
export function RouteErrorPage() {
  const error = useRouteError();
  console.error('Unhandled route error', error);
  return (
    <Box sx={{ py: 6 }}>
      <EmptyState
        icon={<ReportProblemOutlinedIcon />}
        title="Something went wrong on this page"
        description="An unexpected error occurred while displaying this page. Reloading usually helps; if not, please try again later."
        action={<Button variant="contained" onClick={() => window.location.reload()}>Reload</Button>}
      />
    </Box>
  );
}
