import { describe, expect, it } from 'vitest'
import type { StrikeResponse } from '@/api/types'
import { StrikeBuffer } from './strikeBuffer'

const T0 = Date.parse('2026-10-18T12:00:00Z')
const strike = (secondsAgo: number, lat = 50): StrikeResponse => ({
  time: new Date(T0 - secondsAgo * 1000).toISOString(),
  lat,
  lon: 10,
  region: 1,
  stations: 20,
  delaySeconds: 5,
  mds: 5000,
})

describe('StrikeBuffer', () => {
  it('ignores the same strike arriving twice', () => {
    const buffer = new StrikeBuffer(15 * 60_000)
    buffer.add(strike(10))
    buffer.add(strike(10))
    expect(buffer.size).toBe(1)
  })

  it('maps age from 0 (now) to 1 (window edge)', () => {
    const buffer = new StrikeBuffer(100_000)
    buffer.add(strike(0, 1))
    buffer.add(strike(50, 2))
    const ages = buffer.snapshot(T0).features.map((f) => f.properties.age)
    expect(ages).toEqual([0, 0.5])
  })

  it('evicts strikes older than the window', () => {
    const buffer = new StrikeBuffer(60_000)
    buffer.add(strike(30))
    buffer.add(strike(90))
    expect(buffer.snapshot(T0).features).toHaveLength(1)
    expect(buffer.size).toBe(1)
  })

  it('puts coordinates in GeoJSON order: [lon, lat]', () => {
    const buffer = new StrikeBuffer(60_000)
    buffer.add(strike(1, 41.5))
    expect(buffer.snapshot(T0).features[0].geometry.coordinates).toEqual([10, 41.5])
  })

  it('notifies arrival listeners only for new live strikes', () => {
    const buffer = new StrikeBuffer(15 * 60_000)
    const arrived: number[] = []
    buffer.onArrival((s) => arrived.push(s.lat))
    buffer.add(strike(20, 1), true) // live
    buffer.add(strike(25, 2)) // backfill from /strikes/recent
    buffer.add(strike(20, 1), true) // repeat
    expect(arrived).toEqual([1])
  })
})
