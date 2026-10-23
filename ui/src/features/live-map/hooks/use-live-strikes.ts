import { useEffect, useState } from 'react';

import { StrikeBuffer } from '@features/live-map/lib/strike-buffer';
import { followStrikes } from '@features/live-map/lib/strike-stream';
import { useLiveFeedStore } from '@features/live-map/stores/live-feed.store';

/** Owns the strike buffer and the stream connection for the caller's lifetime; publishes status and count to the store. */
export function useLiveStrikes(windowMinutes: number): StrikeBuffer {
  const [buffer] = useState(() => new StrikeBuffer(windowMinutes * 60_000));
  const setStatus = useLiveFeedStore((s) => s.setStatus);
  const setCount = useLiveFeedStore((s) => s.setCount);

  useEffect(() => followStrikes(windowMinutes, (s, live) => buffer.add(s, live), setStatus), [buffer, windowMinutes, setStatus]);

  // the count is only for display, so once a second is enough
  useEffect(() => {
    const id = window.setInterval(() => setCount(buffer.size), 1_000);
    return () => window.clearInterval(id);
  }, [buffer, setCount]);

  return buffer;
}
