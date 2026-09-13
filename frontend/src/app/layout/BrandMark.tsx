import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

export function BrandMark({ size = 'medium' }: { size?: 'medium' | 'large' }) {
  const dimension = size === 'large' ? 40 : 30;
  return (
    <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 1.25 }}>
      <Box
        component="svg"
        viewBox="0 0 64 64"
        aria-hidden
        sx={{ width: dimension, height: dimension, flexShrink: 0, color: 'primary.main' }}
      >
        <rect width="64" height="64" rx="14" fill="currentColor" />
        <polyline points="12,44 26,30 36,38 52,18" fill="none" stroke="#fff" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round" />
        <circle cx="52" cy="18" r="4" fill="#fff" />
      </Box>
      <Typography
        component="span"
        sx={{ fontWeight: 800, letterSpacing: '-0.03em', fontSize: size === 'large' ? '1.75rem' : '1.25rem' }}
      >
        StockLens
      </Typography>
    </Box>
  );
}
