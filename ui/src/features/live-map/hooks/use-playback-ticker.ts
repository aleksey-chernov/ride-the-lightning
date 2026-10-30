import { useEffect } from 'react';

import { PLAYBACK_TICK_MS } from '@features/live-map/constants/playback';
import { usePlaybackStore } from '@features/live-map/stores/playback.store';

/** The playback clock: advances the playhead by the real time passed, while playing and `ready` (strikes loaded). */
export function usePlaybackTicker(ready: boolean): void {
  const playing = usePlaybackStore((s) => s.playing);
  const tick = usePlaybackStore((s) => s.tick);

  useEffect(() => {
    if (!playing || !ready) return;
    let last = performance.now();
    const id = window.setInterval(() => {
      const now = performance.now();
      tick(now - last);
      last = now;
    }, PLAYBACK_TICK_MS);
    return () => window.clearInterval(id);
  }, [playing, ready, tick]);
}
