import { defineConfig } from 'orval';

// Generates src/api/ (react-query hooks + models) from the backend's OpenAPI spec; output is committed.
export default defineConfig({
  rideTheLightning: {
    input: {
      target: 'http://localhost:8080/api/v1/docs/docs.yaml',
      filters: { mode: 'exclude', tags: ['stream'] }, // SSE is consumed with EventSource, not fetch
    },
    output: {
      mode: 'tags-split',
      target: 'src/api/endpoints',
      schemas: 'src/api/models',
      client: 'react-query',
      httpClient: 'fetch',
      baseUrl: '/api/v1',
      clean: true,
      indexFiles: false, // no barrels: import each model/endpoint file directly
      namingConvention: 'kebab-case',
      override: {
        mutator: { path: 'src/shared/lib/fetch-json.ts', name: 'fetchJson' },
        fetch: { includeHttpResponseReturnType: false }, // hooks return the body, not { data, status, headers }
      },
    },
  },
});
