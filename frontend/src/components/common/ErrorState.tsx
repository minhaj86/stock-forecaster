import RefreshIcon from '@mui/icons-material/Refresh';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import { toApiError } from '@/api/apiError';

interface ErrorStateProps {
  title: string;
  error: unknown;
  onRetry?: () => void;
}

/** Explains what failed using the backend's message and offers a retry. */
export function ErrorState({ title, error, onRetry }: ErrorStateProps) {
  const apiError = toApiError(error);
  return (
    <Alert
      severity="error"
      variant="outlined"
      action={onRetry && (
        <Button color="inherit" size="small" startIcon={<RefreshIcon />} onClick={onRetry}>
          Retry
        </Button>
      )}
    >
      <AlertTitle>{title}</AlertTitle>
      {apiError.message}
      {apiError.correlationId && (
        <Typography variant="caption" component="p" sx={{ mt: 0.5, opacity: 0.8 }}>
          Reference: {apiError.correlationId}
        </Typography>
      )}
    </Alert>
  );
}
