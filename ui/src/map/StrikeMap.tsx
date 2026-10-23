import type { GeoJSONSource } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { useEffect, useRef } from 'react'
import MapView, { Layer, Source, type MapRef } from 'react-map-gl/maplibre'
import { flash } from './flashes'
import { dotLayer, heatmapLayer } from './layers'
import { maplibre } from './maplibre'
import type { StrikeBuffer, StrikeFeatures } from './strikeBuffer'

const STYLE_URL = 'https://tiles.openfreemap.org/styles/dark'
const REFRESH_MS = 500
const EMPTY: StrikeFeatures = { type: 'FeatureCollection', features: [] } // stable reference: never reset by re-renders

export function StrikeMap({ buffer }: { buffer: StrikeBuffer }) {
  const mapRef = useRef<MapRef>(null)

  // all strikes: a fresh snapshot twice a second, bypassing React re-renders
  useEffect(() => {
    const id = window.setInterval(() => {
      const source = mapRef.current?.getMap().getSource('strikes') as GeoJSONSource | undefined
      source?.setData(buffer.snapshot(Date.now()))
    }, REFRESH_MS)
    return () => window.clearInterval(id)
  }, [buffer])

  // a CSS flash for each strike arriving over the stream (hidden for reduced motion, see index.css)
  useEffect(() => {
    const active = { count: 0 }
    return buffer.onArrival((s) => {
      const map = mapRef.current?.getMap()
      if (map) void flash(map, s.lon, s.lat, active)
    })
  }, [buffer])

  return (
    <MapView
      ref={mapRef}
      mapLib={maplibre}
      initialViewState={{ longitude: 10, latitude: 25, zoom: 1.6 }}
      mapStyle={STYLE_URL}
      attributionControl={{ compact: true, customAttribution: 'Lightning data © Blitzortung.org' }}
      style={{ position: 'absolute', inset: 0 }}
    >
      <Source id="strikes" type="geojson" data={EMPTY}>
        <Layer {...heatmapLayer} />
        <Layer {...dotLayer} />
      </Source>
    </MapView>
  )
}
