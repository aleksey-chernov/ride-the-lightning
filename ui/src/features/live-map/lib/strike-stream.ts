import { getStrikesRecent } from '@api/endpoints/strikes/strikes';
import type { StrikeResponse } from '@api/models/strike-response';
import type { ConnectionStatus } from '@shared/types/connection-status';

const RETRY_MS = 5_000;

/**
 * Follows /strikes/stream and, on every (re)connect, loads /strikes/recent to fill gaps.
 * Returns a function that disconnects.
 */
export function followStrikes(minutes: number, onStrike: (s: StrikeResponse, live: boolean) => void, onStatus: (s: ConnectionStatus) => void): () => void {
  let source: EventSource | undefined;
  let retryTimer: number | undefined;
  let closed = false;

  const loadRecent = () =>
    getStrikesRecent({ minutes })
      .then((r) => !closed && r.strikes.forEach((s) => onStrike(s, false)))
      .catch(() => {}); // connection state comes from the stream

  const connect = () => {
    source = new EventSource('/api/v1/strikes/stream');
    source.addEventListener('strike', (e) => onStrike(JSON.parse(e.data) as StrikeResponse, true));
    source.onopen = () => {
      onStatus('live');
      void loadRecent();
    };
    source.onerror = () => {
      onStatus('reconnecting');
      // the browser retries dropped connections itself, but gives up on an error response (e.g. 502 from the proxy)
      if (source?.readyState === EventSource.CLOSED && !closed) retryTimer = window.setTimeout(connect, RETRY_MS);
    };
  };

  onStatus('connecting');
  connect();
  return () => {
    closed = true;
    window.clearTimeout(retryTimer);
    source?.close();
  };
}
