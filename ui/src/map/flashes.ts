import type { Map as MapLibreMap } from 'maplibre-gl'
import { maplibre } from './maplibre'

const FLASH_MS = 1_600 // matches the CSS animation in index.css, plus a little slack
const MAX_FLASHES = 100 // caps DOM work during bursts

/** Shows a short CSS flash at a strike's position; the marker removes itself afterwards. */
export async function flash(map: MapLibreMap, lon: number, lat: number, active: { count: number }): Promise<void> {
  if (active.count >= MAX_FLASHES) return
  const { Marker } = await maplibre
  const element = document.createElement('div')
  element.className = 'strike-flash'
  const marker = new Marker({ element }).setLngLat([lon, lat]).addTo(map)
  active.count++
  window.setTimeout(() => {
    marker.remove()
    active.count--
  }, FLASH_MS)
}
