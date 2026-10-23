import { useEffect, useState } from 'react';

import { StrikeBuffer } from '@features/live-map/lib/strike-buffer';
import { followStrikes } from '@features/live-map/lib/strike-stream';
import { useLiveFeedStore } from '@features/live-map/stores/live-feed.store';

/** Owns the strike buffer and the stream connection for the caller's lifetime; publishes the status to the store. */
export function useLiveStrikes(windowMinutes: number): StrikeBuffer {
  const [buffer] = useState(() => new StrikeBuffer(windowMinutes * 60_000));
  const setStatus = useLiveFeedStore((s) => s.setStatus);

  useEffect(() => followStrikes(windowMinutes, (s, live) => buffer.add(s, live), setStatus), [buffer, windowMinutes, setStatus]);

  return buffer;
}
