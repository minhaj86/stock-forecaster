import { useMemo } from 'react';
import { LazyEChart } from '@/components/charts/LazyEChart';
import { useChartTheme } from '@/components/charts/useChartTheme';
import type { ForecastResult } from '@/models/forecast';
import { buildForecastOption, rangeLabel } from './forecastChartOption';

export function ForecastChart({ result, height = 360 }: { result: ForecastResult; height?: number }) {
  const theme = useChartTheme();
  const option = useMemo(() => buildForecastOption(result, theme), [result, theme]);
  const what = result.target === 'RETURN' ? 'return' : 'price';
  return (
    <LazyEChart option={option} height={height}
      ariaLabel={`${result.symbol} ${what} history and ${result.horizonTradingDays}-trading-day ${result.modelName} `
        + `forecast with median and ${rangeLabel(result.confidenceLevel)}`} />
  );
}
