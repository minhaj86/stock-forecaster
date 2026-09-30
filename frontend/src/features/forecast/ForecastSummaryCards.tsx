import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import type { ForecastResult } from '@/models/forecast';
import { formatCurrency, formatDate, formatPercent } from '@/utils/format';
import { rangeLabel } from './forecastChartOption';

function SummaryCard({ label, value, detail }: { label: string; value: ReactNode; detail: ReactNode }) {
  return (
    <Grid size={{ xs: 12, sm: 4 }}>
      <Card variant="outlined" sx={{ height: '100%' }}>
        <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>{label}</Typography>
          <Typography variant="h6" component="p" sx={{ fontVariantNumeric: 'tabular-nums' }}>{value}</Typography>
          <Typography variant="body2" color="text.secondary">{detail}</Typography>
        </CardContent>
      </Card>
    </Grid>
  );
}

/** Headline numbers phrased as estimates: a median and a range, never "the price will be". */
export function ForecastSummaryCards({ result }: { result: ForecastResult }) {
  const { summary, currency, horizonTradingDays: horizon } = result;
  const price = (value: number) => formatCurrency(value, currency);
  const percent = (value: number) => formatPercent(value, { digits: 1 });
  const byReturn = result.target === 'RETURN';
  return (
    <Grid container spacing={2}>
      <SummaryCard
        label={byReturn ? `Expected ${horizon}-day return (median)` : `Median ${horizon}-day forecast`}
        value={byReturn ? percent(summary.expectedReturn) : price(summary.expectedPrice)}
        detail={byReturn ? `Implied price ${price(summary.expectedPrice)} by ${formatDate(summary.targetDate)}`
          : `${percent(summary.expectedReturn)} vs last close, by ${formatDate(summary.targetDate)}`}
      />
      <SummaryCard
        label={`${rangeLabel(result.confidenceLevel)} (estimated)`}
        value={byReturn ? `${percent(summary.lowerReturn)} to ${percent(summary.upperReturn)}`
          : `${price(summary.lowerPrice)} – ${price(summary.upperPrice)}`}
        detail={byReturn ? `${price(summary.lowerPrice)} – ${price(summary.upperPrice)}`
          : `${percent(summary.lowerReturn)} to ${percent(summary.upperReturn)}`}
      />
      <SummaryCard label="Last close" value={price(summary.lastClose)}
        detail={`Data through ${formatDate(result.dataThrough)}`} />
    </Grid>
  );
}
