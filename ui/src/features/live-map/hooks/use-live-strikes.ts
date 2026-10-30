import { useEffect, useMemo, useState } from 'react';

import { StrikeBuffer } from '@features/live-map/lib/strike-buffer';
import { liveFrames, type StrikeFrames } from '@features/live-map/lib/strike-frames';
import { followStrikes } from '@features/live-map/lib/strike-stream';
import { useLiveFeedStore } from '@features/live-map/stores/live-feed.store';

const PRUNE_MS = 60_000;

/** Owns the strike buffer and the stream connection for the caller's lifetime; publishes the status to the store. */
export function useLiveStrikes(windowMinutes: number): StrikeFrames {
  const [buffer] = useState(() => new StrikeBuffer(windowMinutes * 60_000));
  const setStatus = useLiveFeedStore((s) => s.setStatus);

  useEffect(() => followStrikes(windowMinutes, (s, live) => buffer.add(s, live), setStatus), [buffer, windowMinutes, setStatus]);

  // the stream keeps running during playback, when nothing takes live snapshots
  useEffect(() => {
    const id = window.setInterval(() => buffer.prune(Date.now()), PRUNE_MS);
    return () => window.clearInterval(id);
  }, [buffer]);

  return useMemo(() => liveFrames(buffer), [buffer]);
}
