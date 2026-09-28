import { describe, expect, it } from 'vitest';
import type { ChartTheme } from '@/components/charts/useChartTheme';
import type { PerformanceSeries } from '@/models/analytics';
import { buildPerformanceOption, INITIALLY_VISIBLE_SERIES } from './performanceChartOption';

const theme: ChartTheme = {
  series: ['#111', '#222'], text: '#333', textStrong: '#000', grid: '#eee', background: '#fff',
  positive: '#0a0', negative: '#a00', forecast: '#70f', primary: '#00f', fontFamily: 'Inter',
};

function series(count: number): PerformanceSeries[] {
  return Array.from({ length: count }, (_, index) => ({
    symbol: `S${index}`, companyName: `Stock ${index}`, baseDate: '2026-09-09', partial: false, periodReturn: 0.1,
    points: [{ date: '2026-09-09', value: 100 }, { date: '2026-10-09', value: 110 }],
  }));
}

describe('buildPerformanceOption', () => {
  it('plots one zoomable line per stock with a dotted baseline at 100', () => {
    const option = buildPerformanceOption(series(2), theme) as Record<string, unknown>;
    const lines = option.series as Array<Record<string, unknown>>;

    expect(lines).toHaveLength(2);
    expect(lines[0].data).toEqual([['2026-09-09', 100], ['2026-10-09', 110]]);
    expect((lines[0].markLine as { data: unknown[] }).data).toEqual([{ yAxis: 100 }]);
    expect(lines[1].markLine).toBeUndefined();
    expect(option.dataZoom).toHaveLength(2);
  });

  it('initially shows a readable number of lines; the legend reveals the rest', () => {
    const option = buildPerformanceOption(series(10), theme) as { legend: { selected: Record<string, boolean> } };

    const visible = Object.values(option.legend.selected).filter(Boolean);
    expect(visible).toHaveLength(INITIALLY_VISIBLE_SERIES);
    expect(option.legend.selected.S9).toBe(false);
  });
});
