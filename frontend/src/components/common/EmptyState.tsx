import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
}

export function EmptyState({ icon, title, description, action }: EmptyStateProps) {
  return (
    <Box
      role="status"
      sx={{ textAlign: 'center', py: { xs: 5, md: 7 }, px: 3, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5 }}
    >
      {icon && <Box sx={{ color: 'primary.main', '& svg': { fontSize: 44 } }} aria-hidden>{icon}</Box>}
      <Typography variant="h6" component="p">{title}</Typography>
      {description && (
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 440 }}>{description}</Typography>
      )}
      {action && <Box sx={{ mt: 1 }}>{action}</Box>}
    </Box>
  );
}
