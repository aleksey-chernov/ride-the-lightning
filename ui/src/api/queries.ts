import { useQuery } from '@tanstack/react-query'
import { getJson } from './client'
import type { VersionResponse } from './types'

export function useVersion() {
  return useQuery({
    queryKey: ['version'],
    queryFn: () => getJson<VersionResponse>('/version'),
    staleTime: Infinity,
    retry: true, // the backend may still be starting; connection state comes from the stream
  })
}
