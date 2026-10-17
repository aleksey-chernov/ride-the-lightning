import type { ErrorResponse } from './types'

/** GET a JSON endpoint under /api/v1; throws with the backend's error message on failure. */
export async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`/api/v1${path}`)
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ErrorResponse | null
    throw new Error(body?.error ?? `${response.status} ${response.statusText}`)
  }
  return (await response.json()) as T
}
