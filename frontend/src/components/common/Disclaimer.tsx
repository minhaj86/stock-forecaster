import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

export const FINANCIAL_DISCLAIMER =
  'Forecasts and AI-generated analysis are informational only and should not be considered financial advice.';

export function Disclaimer({ text = FINANCIAL_DISCLAIMER }: { text?: string }) {
  return (
    <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start', color: 'text.secondary', mt: 4 }}>
      <InfoOutlinedIcon fontSize="small" aria-hidden sx={{ mt: '2px' }} />
      <Typography variant="caption">{text}</Typography>
    </Box>
  );
}
