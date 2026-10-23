// Mirrors the backend's *Response types (see Swagger at /api/v1/docs).

export type HealthResponse = { status: string }

export type VersionResponse = { name: string; version: string; scalaVersion: string }

export type ErrorResponse = { error: string }

export type StrikeResponse = {
  time: string // ISO-8601, nanosecond precision
  lat: number
  lon: number
  region: number
  stations: number
  delaySeconds: number
  mds: number
}

export type RecentStrikesResponse = { strikes: StrikeResponse[] }
