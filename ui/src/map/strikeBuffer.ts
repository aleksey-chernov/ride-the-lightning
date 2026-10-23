import type { FeatureCollection, Point } from 'geojson'
import type { StrikeResponse } from '@/api/types'

type BufferedStrike = { timeMs: number; lat: number; lon: number }

export type StrikeFeatures = FeatureCollection<Point, { age: number }>
export type ArrivalListener = (s: StrikeResponse) => void

/** Recent strikes, kept outside React state; the map reads snapshots on timers. */
export class StrikeBuffer {
  private readonly strikes = new Map<string, BufferedStrike>()
  private readonly listeners = new Set<ArrivalListener>()
  private readonly windowMs: number

  constructor(windowMs: number) {
    this.windowMs = windowMs
  }

  get size(): number {
    return this.strikes.size
  }

  /** Ignores repeats, e.g. when /strikes/recent and the stream overlap. `live` strikes notify arrival listeners. */
  add(s: StrikeResponse, live = false): void {
    const key = `${s.time}|${s.lat}|${s.lon}`
    if (this.strikes.has(key)) return
    this.strikes.set(key, { timeMs: Date.parse(s.time), lat: s.lat, lon: s.lon })
    if (live) this.listeners.forEach((listener) => listener(s))
  }

  /** Called for each new strike from the live stream; returns an unsubscribe function. */
  onArrival(listener: ArrivalListener): () => void {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  /** Evicts strikes older than the window; `age` goes from 0 (now) to 1 (oldest kept). */
  snapshot(nowMs: number): StrikeFeatures {
    const features: StrikeFeatures['features'] = []
    for (const [key, s] of this.strikes) {
      const age = (nowMs - s.timeMs) / this.windowMs
      if (age > 1) {
        this.strikes.delete(key)
        continue
      }
      features.push(point(s.lon, s.lat, { age: Math.max(0, age) }))
    }
    return { type: 'FeatureCollection', features }
  }
}

function point<P>(lon: number, lat: number, properties: P) {
  return { type: 'Feature' as const, geometry: { type: 'Point' as const, coordinates: [lon, lat] }, properties }
}
