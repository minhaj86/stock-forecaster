import Alert from '@mui/material/Alert';
import { useMarketDataProvider } from '@/features/stocks/hooks';

/** Simulated prices must never be mistaken for real ones. */
export function DemoDataBanner() {
  const provider = useMarketDataProvider();
  if (!provider.data?.synthetic) return null;
  return (
    <Alert severity="warning" variant="outlined" sx={{ mb: 3 }} role="note">
      <strong>Demo data.</strong> {provider.data.description}
    </Alert>
  );
}
