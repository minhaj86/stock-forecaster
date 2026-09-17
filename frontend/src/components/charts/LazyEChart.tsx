import { lazy, Suspense, type ComponentProps } from 'react';
import { ChartSkeleton } from '../common/Skeletons';

const EChart = lazy(() => import('./EChart'));

/** ECharts is large; it is downloaded only when the first chart renders. */
export function LazyEChart(props: ComponentProps<typeof EChart>) {
  const height = typeof props.height === 'number' ? props.height : 320;
  return (
    <Suspense fallback={<ChartSkeleton height={height} label="Loading chart" />}>
      <EChart {...props} />
    </Suspense>
  );
}
