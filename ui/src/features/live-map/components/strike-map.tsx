import type { GeoJSONSource } from 'maplibre-gl';

import 'maplibre-gl/dist/maplibre-gl.css';

import { useEffect, useRef, useState } from 'react';
import MapView, { Layer, Source, type MapRef } from 'react-map-gl/maplibre';

import type { MapFocus } from '@shared/types/map-focus';
import { dotLayer, heatmapLayer } from '@features/live-map/constants/map-layers';
import { INITIAL_VIEW, MAP_STYLE_URL, STRIKE_ATTRIBUTION } from '@features/live-map/constants/map-style';
import { maplibre } from '@features/live-map/lib/maplibre';
import type { StrikeBuffer, StrikeFeatures } from '@features/live-map/lib/strike-buffer';
import { flash } from '@features/live-map/lib/strike-flash';

const REFRESH_MS = 500;
const FLY_DURATION_MS = 1600;
const EMPTY: StrikeFeatures = { type: 'FeatureCollection', features: [] }; // stable reference: never reset by re-renders

type Props = { buffer: StrikeBuffer; focus: MapFocus | null };

/** Renders the buffer's strikes; talks to MapLibre directly so frequent updates skip React re-renders. */
export function StrikeMap({ buffer, focus }: Props) {
  const mapRef = useRef<MapRef>(null);
  const [loaded, setLoaded] = useState(false);

  // all strikes: a fresh snapshot twice a second
  useEffect(() => {
    const id = window.setInterval(() => {
      const source = mapRef.current?.getMap().getSource('strikes') as GeoJSONSource | undefined;
      source?.setData(buffer.snapshot(Date.now()));
    }, REFRESH_MS);
    return () => window.clearInterval(id);
  }, [buffer]);

  // a CSS flash for each strike arriving over the stream (hidden for reduced motion, see styles/globals.css)
  useEffect(() => {
    const active = { count: 0 };
    return buffer.onArrival((s) => {
      const map = mapRef.current?.getMap();
      if (map) void flash(map, s.lon, s.lat, active);
    });
  }, [buffer]);

  // move the camera on each focus request; a request made before the map loads is applied on load
  useEffect(() => {
    const map = mapRef.current?.getMap();
    if (!focus || !loaded || !map) return;
    const camera = { center: [focus.lon, focus.lat] as [number, number], zoom: focus.zoom };
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) map.jumpTo(camera);
    else map.flyTo({ ...camera, duration: FLY_DURATION_MS });
    return () => {
      map.stop();
    };
  }, [focus, loaded]);

  return (
    <MapView
      ref={mapRef}
      onLoad={(e) => {
        // MapLibre opens the compact attribution on load; start it folded behind its (i) button
        e.target.getContainer().querySelector('.maplibregl-ctrl-attrib')?.classList.remove('maplibregl-compact-show');
        setLoaded(true);
      }}
      mapLib={maplibre}
      initialViewState={INITIAL_VIEW}
      mapStyle={MAP_STYLE_URL}
      attributionControl={{ compact: true, customAttribution: STRIKE_ATTRIBUTION }}
      style={{ position: 'absolute', inset: 0 }}
    >
      <Source id="strikes" type="geojson" data={EMPTY}>
        <Layer {...heatmapLayer} />
        <Layer {...dotLayer} />
      </Source>
    </MapView>
  );
}
