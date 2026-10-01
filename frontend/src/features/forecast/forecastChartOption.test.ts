import { describe, expect, it } from 'vitest';
import type { ChartTheme } from '@/components/charts/useChartTheme';
import { forecastResult } from '@/test/fixtures';
import { buildForecastOption, forecastChartData, valueFormatter } from './forecastChartOption';

const theme: ChartTheme = {
  series: ['#111'], text: '#333', textStrong: '#000', grid: '#eee', background: '#fff',
  positive: '#0a0', negative: '#a00', forecast: '#70f', primary: '#00f', fontFamily: 'Inter',
};

type Series = {
  name: string;
  data: (number | null)[];
  stack?: string;
  stackStrategy?: string;
  lineStyle: { type?: string; color?: string; opacity?: number };
  areaStyle?: { color: string };
  markLine?: { data: { xAxis: string }[] };
};
type Option = {
  legend: { data: string[] };
  xAxis: { type: string; data: string[] };
  yAxis: { axisLabel: { formatter: (value: number) => string } };
  tooltip: { formatter: (params: { dataIndex: number }[]) => string };
  series: Series[];
};

describe('forecastChartData', () => {
  it('aligns history and forecast on one trading-day axis, starting the range at the last close', () => {
    const data = forecastChartData(forecastResult());

    expect(data.dates).toHaveLength(10);
    expect(data.originIndex).toBe(4);
    expect(data.historical).toEqual([236, 237, 238, 239, 240, null, null, null, null, null]);
    expect(data.expected.slice(0, 4)).toEqual([null, null, null, null]);
    expect(data.expected[4]).toBe(240);
    expect(data.expected[5]).toBe(240.5);
    expect(data.lower[4]).toBe(240);
    expect(data.band[4]).toBe(0);
    // Band height is upper minus lower, so stacked on the lower bound it ends at the upper bound.
    expect(data.band[9]).toBe(251 - 230);
    expect((data.lower[9] ?? 0) + (data.band[9] ?? 0)).toBe(data.upper[9]);
  });
});

describe('buildForecastOption', () => {
  it('draws history solid, the median dashed and the range as a stacked band', () => {
    const option = buildForecastOption(forecastResult(), theme) as unknown as Option;
    const [historical, lower, band, median] = option.series;

    expect(option.xAxis.type).toBe('category');
    expect(option.legend.data).toEqual(['Historical', 'Forecast (median)', '80% range']);
    expect(historical.lineStyle.type).toBeUndefined();
    expect(median.lineStyle.type).toBe('dashed');
    expect(median.lineStyle.color).toBe(theme.forecast);
    expect(lower.stack).toBe('range');
    expect(band.stack).toBe('range');
    // Negative return bounds must still stack on the lower bound.
    expect(lower.stackStrategy).toBe('all');
    expect(band.stackStrategy).toBe('all');
    expect(lower.lineStyle.opacity).toBe(0);
    expect(band.areaStyle?.color).toBe(theme.forecast);
    expect(historical.markLine?.data).toEqual([{ xAxis: '2026-10-09' }]);
  });

  it('explains history and forecast points in the tooltip', () => {
    const option = buildForecastOption(forecastResult(), theme) as unknown as Option;

    expect(option.tooltip.formatter([{ dataIndex: 4 }])).toContain('Last close $240.00');
    const ahead = option.tooltip.formatter([{ dataIndex: 9 }]);
    expect(ahead).toContain('5 trading days ahead');
    expect(ahead).toContain('Median $242.10');
    expect(ahead).toContain('80% range $230.00 – $251.00');
  });

  it('formats return forecasts as percentages', () => {
    const result = forecastResult('MSFT', { target: 'RETURN' });
    const option = buildForecastOption(result, theme) as unknown as Option;

    expect(option.yAxis.axisLabel.formatter(0.052)).toBe('+5.2%');
    expect(valueFormatter(result)(-0.031)).toBe('-3.1%');
    expect(valueFormatter(forecastResult())(242.1)).toBe('$242.10');
  });
});
