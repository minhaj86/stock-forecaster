import type { AlertColor } from '@mui/material/Alert';
import { createContext, useContext, type ReactNode } from 'react';

export interface Notification {
  message: ReactNode;
  severity?: AlertColor;
  action?: { label: string; onClick: () => void };
}

export const NotificationContext = createContext<((notification: Notification) => void) | null>(null);

export function useNotify(): (notification: Notification) => void {
  const notify = useContext(NotificationContext);
  if (!notify) throw new Error('useNotify must be used inside NotificationProvider');
  return notify;
}
