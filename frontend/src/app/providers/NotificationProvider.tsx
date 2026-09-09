import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Snackbar from '@mui/material/Snackbar';
import { useCallback, useState, type ReactNode } from 'react';
import { NotificationContext, type Notification } from './notificationContext';

/** Transient feedback for mutations (e.g. "Added MSFT to Tech", with undo). */
export function NotificationProvider({ children }: { children: ReactNode }) {
  const [current, setCurrent] = useState<(Notification & { key: number }) | null>(null);
  const notify = useCallback((notification: Notification) => setCurrent({ ...notification, key: Date.now() }), []);
  const close = () => setCurrent(null);

  return (
    <NotificationContext.Provider value={notify}>
      {children}
      <Snackbar
        key={current?.key}
        open={current !== null}
        autoHideDuration={6000}
        onClose={(_, reason) => reason !== 'clickaway' && close()}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {current ? (
          <Alert
            severity={current.severity ?? 'success'}
            variant="filled"
            onClose={close}
            action={current.action && (
              <Button color="inherit" size="small" onClick={() => { current.action?.onClick(); close(); }}>
                {current.action.label}
              </Button>
            )}
            sx={{ alignItems: 'center' }}
          >
            {current.message}
          </Alert>
        ) : undefined}
      </Snackbar>
    </NotificationContext.Provider>
  );
}
