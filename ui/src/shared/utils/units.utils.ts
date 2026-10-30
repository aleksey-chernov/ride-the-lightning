const ONE_DECIMAL = new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 });
const WHOLE = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });

/** "4.2 s" below 10 s, whole seconds from there ("23 s"). */
export function formatSeconds(seconds: number): string {
  return `${(seconds < 10 ? ONE_DECIMAL : WHOLE).format(seconds)} s`;
}
