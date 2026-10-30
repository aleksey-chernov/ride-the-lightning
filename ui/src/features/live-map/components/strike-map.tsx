import type { GeoJSONSource } from 'maplibre-gl';

import 'maplibre-gl/dist/maplibre-gl.css';

import { useEffect, useRef, useState } from 'react';
import MapView, { Layer, Source, type MapRef } from 'react-map-gl/maplibre';

import type { MapFocus } from '@shared/types/map-focus';
import { dotLayer, heatmapLayer } from '@features/live-map/constants/map-layers';
import { INITIAL_VIEW, MAP_STYLE_URL, STRIKE_ATTRIBUTION } from '@features/live-map/constants/map-style';
import { maplibre } from '@features/live-map/lib/maplibre';
import { flash } from '@features/live-map/lib/strike-flash';
import { NO_STRIKES, type StrikeFrames } from '@features/live-map/lib/strike-frames';

const FLY_DURATION_MS = 1600;

type Props = { frames: StrikeFrames; focus: MapFocus | null };

/**
 * Renders the frames' strikes (live or playback); talks to MapLibre directly so frequent updates skip React re-renders.
 * Stays mounted across modes: a remount would reload the style and tiles and lose the camera.
 */
export function StrikeMap({ frames, focus }: Props) {
  const mapRef = useRef<MapRef>(null);
  const [loaded, setLoaded] = useState(false);
  const activeFlashes = useRef({ count: 0 }); // one counter across re-subscriptions, so MAX_FLASHES holds

  // a frame whenever the frames say so, plus one right away so a mode or load change shows at once
  useEffect(() => {
    const redraw = () => {
      const source = mapRef.current?.getMap().getSource('strikes') as GeoJSONSource | undefined;
      source?.setData(frames.snapshot());
    };
    redraw();
    return frames.subscribe(redraw);
  }, [frames, loaded]);

  // a CSS flash for each arriving strike, live or in playback (hidden for reduced motion, see styles/globals.css)
  useEffect(() => {
    const active = activeFlashes.current;
    return frames.onArrival?.((s) => {
      const map = mapRef.current?.getMap();
      if (map) void flash(map, s.lon, s.lat, active);
    });
  }, [frames]);

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
      <Source id="strikes" type="geojson" data={NO_STRIKES}>
        <Layer {...heatmapLayer} />
        <Layer {...dotLayer} />
      </Source>
    </MapView>
  );
}
