import type { EChartsCoreOption } from '@/components/charts/echarts';
import type { ChartTheme } from '@/components/charts/useChartTheme';
import type { ForecastResult } from '@/models/forecast';

/**
 * Series aligned on one axis of trading days: history, then forecast steps. The forecast line and the range start at
 * the last observation so the projection visibly continues from it.
 */
export interface ForecastChartData {
  dates: string[];
  historical: (number | null)[];
  expected: (number | null)[];
  lower: (number | null)[];
  upper: (number | null)[];
  /** {@code upper - lower}, stacked on {@code lower} to shade the range. */
  band: (number | null)[];
  /** Index of the last observation (the forecast origin). */
  originIndex: number;
}

export function forecastChartData(result: ForecastResult): ForecastChartData {
  const { history, forecast } = result;
  const originIndex = history.length - 1;
  const origin = history[originIndex];
  const blanks = (count: number) => Array<null>(Math.max(count, 0)).fill(null);
  const startAtOrigin = (values: number[]) =>
    (origin ? [...blanks(originIndex), origin.value, ...values] : values);
  return {
    dates: [...history.map((point) => point.date), ...forecast.map((point) => point.date)],
    historical: [...history.map((point) => point.value), ...blanks(forecast.length)],
    expected: startAtOrigin(forecast.map((point) => point.expected)),
    lower: startAtOrigin(forecast.map((point) => point.lowerBound)),
    upper: startAtOrigin(forecast.map((point) => point.upperBound)),
    band: origin
      ? [...blanks(originIndex), 0, ...forecast.map((point) => point.upperBound - point.lowerBound)]
      : forecast.map((point) => point.upperBound - point.lowerBound),
    originIndex,
  };
}

export function valueFormatter(result: Pick<ForecastResult, 'target' | 'currency'>): (value: number) => string {
  if (result.target === 'RETURN') {
    const percent = new Intl.NumberFormat(undefined, { style: 'percent', minimumFractionDigits: 1, maximumFractionDigits: 1, signDisplay: 'exceptZero' });
    return (value) => percent.format(value);
  }
  const money = new Intl.NumberFormat(undefined, { style: 'currency', currency: result.currency });
  return (value) => money.format(value);
}

export function rangeLabel(confidenceLevel: number): string {
  return `${Math.round(confidenceLevel * 100)}% range`;
}

interface TooltipParam {
  dataIndex: number;
}

const formatDay = (date: string) =>
  new Date(`${date}T00:00:00Z`).toLocaleDateString(undefined, { dateStyle: 'medium', timeZone: 'UTC' });

/**
 * Historical values (solid), the median forecast (dashed) and the shaded uncertainty range, separated by a marker at
 * the last close. Categories are trading days, so weekends do not leave gaps.
 */
export function buildForecastOption(result: ForecastResult, theme: ChartTheme): EChartsCoreOption {
  const data = forecastChartData(result);
  const format = valueFormatter(result);
  const range = rangeLabel(result.confidenceLevel);
  const medianLabel = 'Forecast (median)';
  return {
    textStyle: { fontFamily: theme.fontFamily, color: theme.text },
    aria: { enabled: true },
    legend: {
      top: 0, data: ['Historical', medianLabel, range], textStyle: { color: theme.text },
      itemStyle: { opacity: 1 },
    },
    grid: { left: 8, right: 16, top: 36, bottom: 16, containLabel: true },
    tooltip: {
      trigger: 'axis',
      formatter: (params: TooltipParam[]) => {
        const index = params[0]?.dataIndex ?? 0;
        const lines = [`<b>${formatDay(data.dates[index])}</b>`];
        const historical = data.historical[index];
        if (index <= data.originIndex && historical !== null) {
          lines.push(`${index === data.originIndex ? 'Last close' : 'Historical'} ${format(historical)}`);
        } else {
          const expected = data.expected[index];
          const lower = data.lower[index];
          const upper = data.upper[index];
          lines.push(`${index - data.originIndex} trading days ahead`);
          if (expected !== null) lines.push(`Median ${format(expected)}`);
          if (lower !== null && upper !== null) lines.push(`${range} ${format(lower)} – ${format(upper)}`);
        }
        return lines.join('<br/>');
      },
    },
    xAxis: {
      type: 'category', data: data.dates, boundaryGap: false,
      axisLine: { lineStyle: { color: theme.grid } },
      axisLabel: {
        color: theme.text, hideOverlap: true,
        formatter: (value: string) => new Date(`${value}T00:00:00Z`)
          .toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' }),
      },
    },
    yAxis: {
      type: 'value', scale: true,
      axisLabel: { color: theme.text, formatter: format },
      splitLine: { lineStyle: { color: theme.grid, type: 'dashed' } },
    },
    dataZoom: [{ type: 'inside' }],
    series: [
      {
        name: 'Historical', type: 'line', data: data.historical, showSymbol: false,
        lineStyle: { width: 2, color: theme.primary }, itemStyle: { color: theme.primary },
        markLine: {
          symbol: 'none', silent: true,
          lineStyle: { color: theme.text, type: 'dotted', width: 1 },
          label: { formatter: 'Last close', color: theme.text, position: 'insideEndTop' },
          data: data.originIndex >= 0 ? [{ xAxis: data.dates[data.originIndex] }] : [],
        },
      },
      {
        // Invisible base of the shaded range; "all" keeps stacking correct when bounds are negative returns.
        name: `${range} (lower)`, type: 'line', data: data.lower, stack: 'range', stackStrategy: 'all',
        showSymbol: false, silent: true, lineStyle: { opacity: 0 }, itemStyle: { color: theme.forecast },
        tooltip: { show: false },
      },
      {
        name: range, type: 'line', data: data.band, stack: 'range', stackStrategy: 'all', showSymbol: false,
        silent: true, lineStyle: { opacity: 0 }, itemStyle: { color: theme.forecast },
        areaStyle: { color: theme.forecast, opacity: 0.18 },
      },
      {
        name: medianLabel, type: 'line', data: data.expected, showSymbol: false,
        lineStyle: { width: 2, type: 'dashed', color: theme.forecast }, itemStyle: { color: theme.forecast },
      },
    ],
  };
}
