import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from 'recharts';

import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from '@shared/components/ui/chart';
import type { MinutePoint } from '@features/stats/utils/stats-format.utils';

type Props = { points: MinutePoint[] };

const CHART_CONFIG = { count: { label: 'Strikes', color: 'var(--chart-1)' } } satisfies ChartConfig;

/** Strikes per minute over the last hour, oldest on the left. */
export function StrikesPerMinuteChart({ points }: Props) {
  return (
    <section className="space-y-2" aria-label="Strikes per minute, last hour">
      <h3 className="text-xs font-medium text-muted-foreground">Per minute, last hour</h3>
      <ChartContainer config={CHART_CONFIG} className="aspect-auto h-36 w-full">
        <BarChart data={points} margin={{ top: 4, right: 0, bottom: 0, left: 0 }} barCategoryGap={1} accessibilityLayer>
          <CartesianGrid vertical={false} />
          <XAxis dataKey="label" tickLine={false} axisLine={false} interval="preserveStartEnd" minTickGap={32} tickMargin={6} />
          <YAxis width={32} tickCount={3} tickLine={false} axisLine={false} allowDecimals={false} />
          <ChartTooltip
            cursor={false}
            isAnimationActive={false}
            content={<ChartTooltipContent labelFormatter={(_, payload) => (payload[0]?.payload as MinutePoint | undefined)?.label} />}
          />
          <Bar dataKey="count" fill="var(--color-count)" radius={1} isAnimationActive={false} />
        </BarChart>
      </ChartContainer>
    </section>
  );
}
