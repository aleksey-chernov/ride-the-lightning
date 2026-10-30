import { useGetHistoryRange } from '@api/endpoints/history/history';

/** What the server stores; refreshed after a minute so a page opened on an empty database can offer playback later. */
export function useHistoryRange() {
  return useGetHistoryRange({ query: { staleTime: 60_000 } });
}
