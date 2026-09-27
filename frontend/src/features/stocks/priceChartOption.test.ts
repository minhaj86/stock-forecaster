import { describe, expect, it } from 'vitest';
import type { ChartTheme } from '@/components/charts/useChartTheme';
import type { PriceBar } from '@/models/marketData';
import { buildPriceOption } from './priceChartOption';

const theme: ChartTheme = {
  series: ['#111'], text: '#333', textStrong: '#000', grid: '#eee', background: '#fff',
  positive: '#0a0', negative: '#a00', forecast: '#70f', primary: '#00f', fontFamily: 'Inter',
};
const bars: PriceBar[] = [
  { date: '2026-10-08', open: 98, high: 101, low: 97, close: 99, adjustedClose: 99, volume: 1_000 },
  { date: '2026-10-09', open: 99, high: 102.5, low: 98, close: 100, adjustedClose: 100, volume: 1_200_000 },
];

type Option = {
  xAxis: { type: string };
  tooltip: { formatter: (params: { dataIndex: number }[]) => string };
  series: { data: [string, number][]; lineStyle: { color: string } }[];
};

describe('buildPriceOption', () => {
  it('plots closes, coloured by the direction over the period', () => {
    const option = buildPriceOption(bars, 'USD', theme) as unknown as Option;

    expect(option.series[0].data).toEqual([['2026-10-08', 99], ['2026-10-09', 100]]);
    expect(option.series[0].lineStyle.color).toBe(theme.positive);
    expect((buildPriceOption([...bars].reverse(), 'USD', theme) as unknown as Option).series[0].lineStyle.color)
      .toBe(theme.negative);
  });

  it('uses a time axis and shows OHLCV in the tooltip', () => {
    const option = buildPriceOption(bars, 'USD', theme) as unknown as Option;

    expect(option.xAxis.type).toBe('time');
    const tooltip = option.tooltip.formatter([{ dataIndex: 1 }]);
    expect(tooltip).toContain('Oct 9, 2026');
    expect(tooltip).toContain('High $102.50');
    expect(tooltip).toContain('Volume 1,200,000');
  });
});
