const ONE_DECIMAL = new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 });
const WHOLE = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });

/** Nanoseconds as microseconds: "8.4 µs", "10 µs". */
export function formatMicros(ns: number): string {
  return `${ONE_DECIMAL.format(ns / 1000)} µs`;
}

/** "4.2 s" below 10 s, whole seconds from there ("23 s"). */
export function formatSeconds(seconds: number): string {
  return `${(seconds < 10 ? ONE_DECIMAL : WHOLE).format(seconds)} s`;
}
