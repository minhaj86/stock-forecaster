import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Typography from '@mui/material/Typography';
import { AddToWatchlistButton } from '@/features/watchlist/AddToWatchlistButton';
import { formatNumber } from '@/utils/format';
import type { HistoryShortfall } from './hooks';

/** Explains why no forecast is shown instead of showing an unreliable one. */
export function InsufficientHistory({ symbol, shortfall }: { symbol: string; shortfall: HistoryShortfall }) {
  if (shortfall.dataStatus === 'NOT_TRACKED') {
    return (
      <Alert severity="info" action={<AddToWatchlistButton symbol={symbol} variant="icon" />}>
        Price history for {symbol} is collected once it is on a watchlist. Add it to one to generate forecasts.
      </Alert>
    );
  }
  if (shortfall.dataStatus === 'PENDING') {
    return (
      <Alert severity="info" role="status">
        Collecting price history for {symbol}… the forecast appears automatically.
      </Alert>
    );
  }
  return (
    <Alert severity="warning" role="status">
      <AlertTitle>Not enough historical data is available to generate this forecast.</AlertTitle>
      <Typography variant="body2">Required: {formatNumber(shortfall.required, 0)} trading days</Typography>
      <Typography variant="body2">Available: {formatNumber(shortfall.available, 0)} trading days</Typography>
    </Alert>
  );
}
