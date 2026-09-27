import type { EChartsCoreOption } from '@/components/charts/echarts';
import type { ChartTheme } from '@/components/charts/useChartTheme';
import type { PriceBar } from '@/models/marketData';

interface TooltipParam {
  dataIndex: number;
}

/** Closing-price line with an OHLCV tooltip. Candlesticks, overlays and volume arrive with the full chart. */
export function buildPriceOption(bars: PriceBar[], currency: string, theme: ChartTheme): EChartsCoreOption {
  const money = new Intl.NumberFormat(undefined, { style: 'currency', currency });
  const rising = bars.length > 1 && bars[bars.length - 1].close >= bars[0].close;
  const color = rising ? theme.positive : theme.negative;
  return {
    textStyle: { fontFamily: theme.fontFamily, color: theme.text },
    aria: { enabled: true },
    grid: { left: 8, right: 16, top: 16, bottom: 56, containLabel: true },
    tooltip: {
      trigger: 'axis',
      formatter: (params: TooltipParam[]) => {
        const bar = bars[params[0]?.dataIndex ?? 0];
        if (!bar) return '';
        return [
          `<b>${new Date(`${bar.date}T00:00:00Z`).toLocaleDateString(undefined, { dateStyle: 'medium', timeZone: 'UTC' })}</b>`,
          `Open ${money.format(bar.open)}`, `High ${money.format(bar.high)}`, `Low ${money.format(bar.low)}`,
          `Close ${money.format(bar.close)}`, `Volume ${bar.volume.toLocaleString()}`,
        ].join('<br/>');
      },
    },
    // A time axis gives clean month ticks with year boundaries for any period length.
    xAxis: { type: 'time', axisLine: { lineStyle: { color: theme.grid } },
      axisLabel: { color: theme.text, hideOverlap: true } },
    yAxis: { type: 'value', scale: true, axisLabel: { color: theme.text },
      splitLine: { lineStyle: { color: theme.grid, type: 'dashed' } } },
    dataZoom: [{ type: 'inside' }, { type: 'slider', height: 22, bottom: 12, borderColor: theme.grid }],
    series: [{
      name: 'Close', type: 'line', showSymbol: false, sampling: 'lttb', data: bars.map((bar) => [bar.date, bar.close]),
      lineStyle: { width: 2, color }, itemStyle: { color },
      areaStyle: { opacity: 0.08, color },
    }],
  };
}
