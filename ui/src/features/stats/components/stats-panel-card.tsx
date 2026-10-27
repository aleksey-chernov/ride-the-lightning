import { cn } from 'cn';

import type { StatsResponse } from '@api/models/stats-response';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@shared/components/ui/card';
import { Separator } from '@shared/components/ui/separator';
import { Skeleton } from '@shared/components/ui/skeleton';
import { describeStats, toMinutePoints, toPlaceShares } from '@features/stats/utils/stats-format.utils';

import { DetectionSection } from './detection-section';
import { HotspotList } from './hotspot-list';
import { PlaceList } from './place-list';
import { StatsHeadline } from './stats-headline';
import { StrikesPerMinuteChart } from './strikes-per-minute-chart';

type Props = {
  stats: StatsResponse | undefined;
  isError: boolean;
  /** true while the previous filter's stats are shown until the new ones arrive */
  isPlaceholder: boolean;
  onHotspotSelect: (hotspot: { lon: number; lat: number }) => void;
};

function StatsSkeleton() {
  return (
    <div className="space-y-4" aria-hidden>
      <div className="grid grid-cols-3 gap-2">
        <Skeleton className="h-14" />
        <Skeleton className="h-14" />
        <Skeleton className="h-14" />
      </div>
      <Skeleton className="h-36 w-full" />
      <Skeleton className="h-32 w-full" />
    </div>
  );
}

function StatsBody({ stats, isPlaceholder, onHotspotSelect }: { stats: StatsResponse; isPlaceholder: boolean; onHotspotSelect: Props['onHotspotSelect'] }) {
  return (
    <div className={cn('space-y-4 transition-opacity', isPlaceholder && 'opacity-50')} aria-busy={isPlaceholder}>
      <StatsHeadline
        lastMinute={stats.lastMinute}
        last10Minutes={stats.last10Minutes}
        lastHour={stats.lastHour}
        received={stats.received}
        duplicatesDropped={stats.duplicatesDropped}
      />
      <StrikesPerMinuteChart points={toMinutePoints(stats.perMinute)} />
      <Separator />
      <HotspotList hotspots={stats.hotspots} onSelect={onHotspotSelect} />
      <Separator />
      <PlaceList places={toPlaceShares(stats.places)} />
      <Separator />
      <DetectionSection detection={stats.detection} maxMds={stats.maxMds} />
    </div>
  );
}

/** Stats card: last good stats (dimmed while a new filter loads), skeletons while loading, a note when the backend is unreachable. */
export function StatsPanelCard({ stats, isError, isPlaceholder, onHotspotSelect }: Props) {
  return (
    <Card className="lg:h-full lg:min-h-0">
      <CardHeader>
        <CardTitle>Stats</CardTitle>
        <CardDescription>{describeStats(stats, isError)}</CardDescription>
      </CardHeader>
      {/* stacked below lg: the page scrolls; side by side: only the content does */}
      <CardContent className="relative lg:min-h-0 lg:flex-1 lg:overflow-y-auto">
        {stats ? (
          <StatsBody stats={stats} isPlaceholder={isPlaceholder} onHotspotSelect={onHotspotSelect} />
        ) : isError ? (
          <p className="text-sm text-muted-foreground">Stats unavailable</p>
        ) : (
          <StatsSkeleton />
        )}
      </CardContent>
    </Card>
  );
}
