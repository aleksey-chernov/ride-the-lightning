import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';

/**
 * MapLibre, loaded lazily into its own chunk. v6 looks for its worker next to its own file,
 * which Vite relocates, so we point it at a Vite-built copy before the first map is created.
 */
export const maplibre = import('maplibre-gl').then((lib) => {
  lib.setWorkerUrl(workerUrl);
  return lib;
});
