import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { BrandMark } from '@/app/layout/BrandMark';
import { FINANCIAL_DISCLAIMER } from '@/components/common/Disclaimer';

export function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Box
      component="main"
      sx={{
        minHeight: '100dvh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
        px: 2, py: 6, gap: 3,
        background: (theme) => theme.palette.mode === 'light'
          ? 'radial-gradient(1200px 600px at 50% -10%, rgba(36,87,214,0.10), transparent 70%)'
          : 'radial-gradient(1200px 600px at 50% -10%, rgba(122,162,255,0.12), transparent 70%)',
      }}
    >
      <Box sx={{ textAlign: 'center' }}>
        <BrandMark size="large" />
        <Typography color="text.secondary" sx={{ mt: 1 }}>Smarter stock analytics</Typography>
      </Box>
      <Card sx={{ width: '100%', maxWidth: 420 }}>
        <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
          <Typography variant="h5" component="h1" sx={{ mb: 3 }}>{title}</Typography>
          {children}
        </CardContent>
      </Card>
      <Typography variant="caption" color="text.secondary" sx={{ maxWidth: 420, textAlign: 'center' }}>
        {FINANCIAL_DISCLAIMER}
      </Typography>
    </Box>
  );
}
