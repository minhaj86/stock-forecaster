import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { AuthProvider } from '@/features/auth/AuthProvider';
import { ColorModeProvider } from '@/theme/ColorModeProvider';
import { NotificationProvider } from './NotificationProvider';

export function AppProviders({ queryClient, children }: { queryClient: QueryClient; children: ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <ColorModeProvider>
        <NotificationProvider>
          <AuthProvider>{children}</AuthProvider>
        </NotificationProvider>
      </ColorModeProvider>
    </QueryClientProvider>
  );
}
