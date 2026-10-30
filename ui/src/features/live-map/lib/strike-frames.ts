import type { ArrivalListener, StrikeBuffer, StrikeFeatures } from '@features/live-map/lib/strike-buffer';

const LIVE_REFRESH_MS = 500;

/** What the map draws and when: live reads the buffer on a timer, playback redraws when the playhead moves. */
export type StrikeFrames = {
  snapshot: () => StrikeFeatures;
  /** Calls `redraw` whenever a new frame is due; returns an unsubscribe function. */
  subscribe: (redraw: () => void) => () => void;
  /** Strikes to flash: new ones over the stream (live), or a capped sample of those the playhead passes (playback). */
  onArrival?: (listener: ArrivalListener) => () => void;
};

export const NO_STRIKES: StrikeFeatures = { type: 'FeatureCollection', features: [] };

/** The live buffer, redrawn twice a second. */
export function liveFrames(buffer: StrikeBuffer): StrikeFrames {
  return {
    snapshot: () => buffer.snapshot(Date.now()),
    subscribe: (redraw) => {
      const id = window.setInterval(redraw, LIVE_REFRESH_MS);
      return () => window.clearInterval(id);
    },
    onArrival: (listener) => buffer.onArrival(listener),
  };
}
