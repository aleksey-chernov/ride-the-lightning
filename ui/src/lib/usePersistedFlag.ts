import { useEffect, useState } from 'react'

/** Boolean UI preference remembered in localStorage (falls back to `initial` if storage is unavailable). */
export function usePersistedFlag(key: string, initial: boolean) {
  const [value, setValue] = useState(() => {
    try {
      const stored = localStorage.getItem(key)
      return stored === null ? initial : stored === 'true'
    } catch {
      return initial
    }
  })

  useEffect(() => {
    try {
      localStorage.setItem(key, String(value))
    } catch {
      // private mode or blocked storage: keep it in memory only
    }
  }, [key, value])

  return [value, setValue] as const
}
