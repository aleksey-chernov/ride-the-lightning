import { useEffect, useState } from 'react'
import { StrikeBuffer } from './strikeBuffer'
import { followStrikes, type StreamStatus } from './strikeStream'

/** Owns the strike buffer and the stream connection for the lifetime of the component. */
export function useLiveStrikes(windowMinutes = 15) {
  const [buffer] = useState(() => new StrikeBuffer(windowMinutes * 60_000))
  const [status, setStatus] = useState<StreamStatus>('connecting')
  const [count, setCount] = useState(0)

  useEffect(() => followStrikes(windowMinutes, (s, live) => buffer.add(s, live), setStatus), [buffer, windowMinutes])

  // the count is only for display, so once a second is enough
  useEffect(() => {
    const id = window.setInterval(() => setCount(buffer.size), 1_000)
    return () => window.clearInterval(id)
  }, [buffer])

  return { buffer, status, count, windowMinutes }
}
