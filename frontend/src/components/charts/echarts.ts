import { CandlestickChart, LineChart, BarChart } from 'echarts/charts';
import {
  AriaComponent,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  MarkAreaComponent,
  MarkLineComponent,
  TooltipComponent,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';

// Register only what the app uses so the chart bundle stays small.
echarts.use([
  LineChart, CandlestickChart, BarChart,
  GridComponent, TooltipComponent, LegendComponent, DataZoomComponent, MarkLineComponent, MarkAreaComponent,
  AriaComponent, CanvasRenderer,
]);

export { echarts };
export type { EChartsCoreOption } from 'echarts/core';
