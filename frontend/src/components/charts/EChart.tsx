import Box from '@mui/material/Box';
import { useEffect, useRef } from 'react';
import { echarts, type EChartsCoreOption } from './echarts';

interface EChartProps {
  option: EChartsCoreOption;
  height: number | string;
  /** Short description of what the chart shows, announced by screen readers. */
  ariaLabel: string;
}

/**
 * Minimal ECharts binding: one instance per mount, option updates without re-creating the chart, and resizing with
 * its container (sidebar toggles, window resizes).
 */
export default function EChart({ option, height, ariaLabel }: EChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<ReturnType<typeof echarts.init> | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;
    const chart = echarts.init(container, undefined, { renderer: 'canvas' });
    chartRef.current = chart;
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(container);
    return () => {
      observer.disconnect();
      chart.dispose();
      chartRef.current = null;
    };
  }, []);

  useEffect(() => {
    chartRef.current?.setOption(option, { notMerge: true });
  }, [option]);

  return <Box ref={containerRef} role="img" aria-label={ariaLabel} sx={{ width: '100%', height }} />;
}
