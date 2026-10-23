import type { GeoJSONSource } from 'maplibre-gl';

import 'maplibre-gl/dist/maplibre-gl.css';

import { useEffect, useRef } from 'react';
import MapView, { Layer, Source, type MapRef } from 'react-map-gl/maplibre';

import { dotLayer, heatmapLayer } from '@features/live-map/constants/map-layers';
import { INITIAL_VIEW, MAP_STYLE_URL, STRIKE_ATTRIBUTION } from '@features/live-map/constants/map-style';
import { maplibre } from '@features/live-map/lib/maplibre';
import type { StrikeBuffer, StrikeFeatures } from '@features/live-map/lib/strike-buffer';
import { flash } from '@features/live-map/lib/strike-flash';

const REFRESH_MS = 500;
const EMPTY: StrikeFeatures = { type: 'FeatureCollection', features: [] }; // stable reference: never reset by re-renders

type Props = { buffer: StrikeBuffer };

/** Renders the buffer's strikes; talks to MapLibre directly so frequent updates skip React re-renders. */
export function StrikeMap({ buffer }: Props) {
  const mapRef = useRef<MapRef>(null);

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

  return (
    <MapView
      ref={mapRef}
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
