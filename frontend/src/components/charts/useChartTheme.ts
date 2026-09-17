import { useTheme } from '@mui/material/styles';
import { useMemo } from 'react';

/** Concrete colours from the active MUI theme for canvas-rendered charts. */
export function useChartTheme() {
  const theme = useTheme();
  return useMemo(() => ({
    series: theme.palette.chart,
    text: theme.palette.text.secondary,
    textStrong: theme.palette.text.primary,
    grid: theme.palette.divider,
    background: theme.palette.background.paper,
    positive: theme.palette.positive.main,
    negative: theme.palette.negative.main,
    forecast: theme.palette.forecast.main,
    primary: theme.palette.primary.main,
    fontFamily: theme.typography.fontFamily,
  }), [theme]);
}

export type ChartTheme = ReturnType<typeof useChartTheme>;
