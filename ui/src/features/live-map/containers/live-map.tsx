import { Card } from '@shared/components/ui/card';
import { StrikeCount } from '@features/live-map/components/strike-count';
import { StrikeMap } from '@features/live-map/components/strike-map';
import { useLiveStrikes } from '@features/live-map/hooks/use-live-strikes';
import { useLiveFeedStore } from '@features/live-map/stores/live-feed.store';

const WINDOW_MINUTES = 15;

/** Live map feature: connects to the strike feed and renders the map with its strike count. */
export function LiveMap() {
  const buffer = useLiveStrikes(WINDOW_MINUTES);
  const count = useLiveFeedStore((s) => s.count);

  return (
    <Card className="relative min-h-96 overflow-hidden p-0">
      <StrikeMap buffer={buffer} />
      <div className="absolute top-3 left-3">
        <StrikeCount count={count} minutes={WINDOW_MINUTES} />
      </div>
    </Card>
  );
}
