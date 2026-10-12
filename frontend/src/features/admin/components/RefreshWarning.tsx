import Alert from '@mui/material/Alert';
import { errorMessage } from '../errors';

/** A background refresh failed: the last loaded data stays visible with this note. */
export function RefreshWarning({ error }: { error: unknown }) {
  return (
    <Alert severity="warning" sx={{ mb: 2 }}>
      Could not refresh: {errorMessage(error)} Showing the last loaded data.
    </Alert>
  );
}
