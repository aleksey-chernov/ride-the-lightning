import { useQuery } from '@tanstack/react-query'
import { getJson } from './client'
import type { HealthResponse, VersionResponse } from './types'

export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: () => getJson<HealthResponse>('/health'),
    refetchInterval: 10_000,
    retry: false,
  })
}

export function useVersion() {
  return useQuery({
    queryKey: ['version'],
    queryFn: () => getJson<VersionResponse>('/version'),
    staleTime: Infinity,
  })
}
