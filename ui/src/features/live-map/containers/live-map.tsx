import { useMemo } from 'react';

import { Badge } from '@shared/components/ui/badge';
import { Card } from '@shared/components/ui/card';
import type { MapFocus } from '@shared/types/map-focus';
import { StrikeMap } from '@features/live-map/components/strike-map';
import { PlaybackControls } from '@features/live-map/containers/playback-controls';
import { useHistoryRange } from '@features/live-map/hooks/use-history-range';
import { useHistoryStrikes } from '@features/live-map/hooks/use-history-strikes';
import { useLiveStrikes } from '@features/live-map/hooks/use-live-strikes';
import { usePlaybackFrames } from '@features/live-map/hooks/use-playback-frames';
import { StrikeTimeline } from '@features/live-map/lib/strike-timeline';
import { selectMapMode, usePlaybackStore } from '@features/live-map/stores/playback.store';
import { speedOf } from '@features/live-map/utils/playback.utils';

const WINDOW_MINUTES = 15;

type Props = { focus: MapFocus | null };

/** Map feature: live strikes from the feed, or stored ones in playback. */
export function LiveMap({ focus }: Props) {
  const live = useLiveStrikes(WINDOW_MINUTES);
  const mode = usePlaybackStore(selectMapMode);
  const trailMs = usePlaybackStore((s) => speedOf(s.speed).trailMs);
  const { data: stored } = useHistoryRange();
  const { data: history } = useHistoryStrikes();
  const timeline = useMemo(() => (history ? new StrikeTimeline(history.strikes) : null), [history]);
  const playback = usePlaybackFrames(timeline, trailMs);

  return (
    <Card className="gap-0 overflow-hidden p-0 has-[canvas:focus-visible]:ring-3 has-[canvas:focus-visible]:ring-ring/50 lg:min-h-96 [&_canvas]:outline-none">
      {/* the height sits on the map, so the bar below adds page height instead of taking map height */}
      <div className="relative h-[60dvh] min-h-80 lg:h-auto lg:min-h-0 lg:flex-1">
        <StrikeMap frames={mode === 'playback' ? playback : live} focus={focus} />
        {mode === 'playback' && (
          <Badge variant="secondary" className="pointer-events-none absolute top-2 left-2" aria-hidden>
            Playback
          </Badge>
        )}
      </div>
      {/* stays while in playback, so losing history on a refetch never strands the map without the Live switch */}
      {(stored?.enabled || mode === 'playback') && <PlaybackControls oldestMs={stored?.oldest ? Date.parse(stored.oldest) : undefined} />}
    </Card>
  );
}
