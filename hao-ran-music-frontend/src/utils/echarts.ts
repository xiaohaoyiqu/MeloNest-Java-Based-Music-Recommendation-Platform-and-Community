import { use } from 'echarts/core';
import { BarChart, FunnelChart, LineChart, PieChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
use([
    BarChart, FunnelChart, LineChart, PieChart,
    GridComponent, LegendComponent, TooltipComponent,
    CanvasRenderer
]);
export { init, graphic } from 'echarts/core';
export type { ECharts } from 'echarts/core';
