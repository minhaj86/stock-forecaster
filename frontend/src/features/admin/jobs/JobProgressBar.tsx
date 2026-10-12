import Box from '@mui/material/Box';
import LinearProgress from '@mui/material/LinearProgress';
import Typography from '@mui/material/Typography';

/** Determinate progress with the value as text, so it is not conveyed by the bar alone. */
export function JobProgressBar({ progress, label }: { progress: number; label: string }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 140 }}>
      <LinearProgress variant="determinate" value={progress} aria-label={label} sx={{ flexGrow: 1, height: 6, borderRadius: 3 }} />
      <Typography variant="caption" sx={{ minWidth: 36, textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
        {progress}%
      </Typography>
    </Box>
  );
}
