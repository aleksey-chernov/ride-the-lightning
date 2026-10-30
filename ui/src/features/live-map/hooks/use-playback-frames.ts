import { useMemo } from 'react';

import { MAX_FLASHES_PER_FRAME, MAX_TICK_DELTA_MS } from '@features/live-map/constants/playback';
import { NO_STRIKES, type StrikeFrames } from '@features/live-map/lib/strike-frames';
import type { StrikeTimeline } from '@features/live-map/lib/strike-timeline';
import { usePlaybackStore } from '@features/live-map/stores/playback.store';
import { speedOf } from '@features/live-map/utils/playback.utils';

/** Frames at the playhead, redrawn only when it moves; reads the store outside React so the map never re-renders for it. */
export function usePlaybackFrames(timeline: StrikeTimeline | null, trailMs: number): StrikeFrames {
  return useMemo(
    () => ({
      snapshot: () => timeline?.snapshot(usePlaybackStore.getState().positionMs, trailMs) ?? NO_STRIKES,
      subscribe: (redraw) =>
        usePlaybackStore.subscribe((s, prev) => {
          if (s.positionMs !== prev.positionMs) redraw();
        }),
      // flashes for strikes the playhead passed while playing; a seek or a jump longer than one tick flashes nothing
      onArrival: (listener) =>
        usePlaybackStore.subscribe((s, prev) => {
          const advancedMs = s.positionMs - prev.positionMs;
          if (!timeline || !prev.playing || advancedMs <= 0 || advancedMs > MAX_TICK_DELTA_MS * speedOf(s.speed).factor) return;
          timeline.arrivals(prev.positionMs, s.positionMs, MAX_FLASHES_PER_FRAME).forEach(listener);
        }),
    }),
    [timeline, trailMs],
  );
}
