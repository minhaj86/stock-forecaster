import type { EChartsCoreOption } from '@/components/charts/echarts';
import type { ChartTheme } from '@/components/charts/useChartTheme';
import type { PerformanceSeries } from '@/models/analytics';

/** Show at most this many lines initially; the rest can be enabled from the legend. */
export const INITIALLY_VISIBLE_SERIES = 8;

interface TooltipParam {
  seriesName: string;
  marker: string;
  value: [string, number];
  axisValueLabel: string;
}

/** ECharts option for performance rebased to 100 (pure function, unit-testable). */
export function buildPerformanceOption(series: PerformanceSeries[], theme: ChartTheme): EChartsCoreOption {
  const selected = Object.fromEntries(series.map((entry, index) => [entry.symbol, index < INITIALLY_VISIBLE_SERIES]));
  return {
    color: theme.series,
    textStyle: { fontFamily: theme.fontFamily, color: theme.text },
    aria: { enabled: true },
    animationDuration: 300,
    grid: { left: 8, right: 16, top: 44, bottom: 64, containLabel: true },
    legend: { type: 'scroll', top: 0, left: 0, textStyle: { color: theme.textStrong }, selected },
    tooltip: {
      trigger: 'axis',
      order: 'valueDesc',
      formatter: (params: TooltipParam[]) => {
        const rows = params.map((param) => {
          const change = param.value[1] - 100;
          const sign = change > 0 ? '+' : '';
          return `${param.marker} <b>${param.seriesName}</b>&nbsp; ${param.value[1].toFixed(2)} (${sign}${change.toFixed(2)}%)`;
        });
        return [params[0]?.axisValueLabel ?? '', ...rows].join('<br/>');
      },
    },
    xAxis: {
      type: 'time',
      axisLine: { lineStyle: { color: theme.grid } },
      axisLabel: { color: theme.text, hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      scale: true,
      name: 'Indexed (start = 100)',
      nameTextStyle: { color: theme.text, align: 'left' },
      axisLabel: { color: theme.text },
      splitLine: { lineStyle: { color: theme.grid, type: 'dashed' } },
    },
    dataZoom: [
      { type: 'inside', throttle: 50 },
      { type: 'slider', height: 22, bottom: 12, borderColor: theme.grid, textStyle: { color: theme.text } },
    ],
    series: series.map((entry, index) => ({
      name: entry.symbol,
      type: 'line',
      showSymbol: false,
      sampling: 'lttb',
      emphasis: { focus: 'series' },
      lineStyle: { width: 2 },
      data: entry.points.map((point) => [point.date, point.value]),
      ...(index === 0 ? {
        markLine: {
          silent: true,
          symbol: 'none',
          label: { show: false },
          lineStyle: { color: theme.text, type: 'dotted', opacity: 0.6 },
          data: [{ yAxis: 100 }],
        },
      } : {}),
    })),
  };
}
