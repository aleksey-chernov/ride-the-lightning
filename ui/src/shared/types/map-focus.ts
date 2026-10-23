/** A request to move the map; `requestId` changes on every request so selecting the same place twice flies again. */
export type MapFocus = { lon: number; lat: number; zoom: number; requestId: number };
