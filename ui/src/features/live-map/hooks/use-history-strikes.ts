import { useGetHistoryStrikes } from '@api/endpoints/history/history';
import { usePlaybackStore } from '@features/live-map/stores/playback.store';

const NO_RANGE = { from: '', to: '' };

/** Stored strikes for the playback range; the range is frozen in the store, so the query key stays put while playing. */
export function useHistoryStrikes() {
  const range = usePlaybackStore((s) => s.range);
  const params = range ? { from: new Date(range.fromMs).toISOString(), to: new Date(range.toMs).toISOString() } : NO_RANGE;
  // up to ~10 MB per range: drop a range soon after leaving it instead of react-query's default 5 min; data never goes stale
  return useGetHistoryStrikes(params, { query: { enabled: range !== null, gcTime: 30_000, staleTime: Infinity } });
}
