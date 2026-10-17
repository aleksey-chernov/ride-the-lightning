// Mirrors the backend's *Response types (see Swagger at /api/v1/docs).

export type HealthResponse = { status: string }

export type VersionResponse = { name: string; version: string; scalaVersion: string }

export type ErrorResponse = { error: string }
