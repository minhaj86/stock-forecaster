import Alert from '@mui/material/Alert';
import { useMemo } from 'react';
import { LazyEChart } from '@/components/charts/LazyEChart';
import { useChartTheme } from '@/components/charts/useChartTheme';
import type { PerformanceSeries } from '@/models/analytics';
import { buildPerformanceOption } from './performanceChartOption';

export function PerformanceChart({ series, periodLabel }: { series: PerformanceSeries[]; periodLabel: string }) {
  const theme = useChartTheme();
  const option = useMemo(() => buildPerformanceOption(series, theme), [series, theme]);
  if (series.length === 0) {
    return <Alert severity="info">Performance appears here once market data for your stocks is available.</Alert>;
  }
  const partial = series.filter((entry) => entry.partial).map((entry) => entry.symbol);
  return (
    <>
      <LazyEChart
        option={option}
        height={360}
        ariaLabel={`Performance of ${series.map((entry) => entry.symbol).join(', ')} over ${periodLabel}, indexed to 100 at the start of the period`}
      />
      {partial.length > 0 && (
        <Alert severity="info" variant="outlined" sx={{ mt: 1 }}>
          History for {partial.join(', ')} starts after the beginning of this period; those lines start at their first available price.
        </Alert>
      )}
    </>
  );
}
