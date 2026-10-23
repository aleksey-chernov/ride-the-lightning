import type { LayerProps } from 'react-map-gl/maplibre';

// `age`: 0 = just now, 1 = oldest strike in the window

/** Density glow at world/continent zoom; fades out as you zoom in. */
export const heatmapLayer: LayerProps = {
  id: 'strikes-heat',
  type: 'heatmap',
  maxzoom: 8,
  paint: {
    'heatmap-weight': ['interpolate', ['linear'], ['get', 'age'], 0, 1, 0.7, 0.4, 1, 0], // fades out completely
    'heatmap-intensity': ['interpolate', ['linear'], ['zoom'], 0, 0.6, 8, 2],
    'heatmap-radius': ['interpolate', ['linear'], ['zoom'], 0, 6, 8, 24],
    'heatmap-color': ['interpolate', ['linear'], ['heatmap-density'], 0, 'rgba(0,0,0,0)', 0.2, '#4338ca', 0.45, '#a855f7', 0.7, '#f59e0b', 1, '#fef9c3'],
    'heatmap-opacity': ['interpolate', ['linear'], ['zoom'], 5, 0.75, 8, 0],
  },
};

/** One dot per strike: white when fresh, through yellow and orange to faded violet. */
export const dotLayer: LayerProps = {
  id: 'strikes-dots',
  type: 'circle',
  paint: {
    'circle-radius': ['interpolate', ['linear'], ['zoom'], 0, 1.5, 6, 3, 10, 6],
    'circle-color': ['interpolate', ['linear'], ['get', 'age'], 0, '#ffffff', 0.05, '#fde047', 0.3, '#f97316', 1, '#7c3aed'],
    'circle-opacity': ['interpolate', ['linear'], ['get', 'age'], 0, 1, 0.7, 0.5, 1, 0], // gone before eviction
  },
};
