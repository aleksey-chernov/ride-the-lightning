import { describe, expect, it } from 'vitest';

import { formatMicros, formatSeconds } from './units.utils';

describe('formatMicros', () => {
  it('converts nanoseconds to microseconds with one decimal', () => {
    expect(formatMicros(8_430)).toBe('8.4 µs');
  });

  it('drops a trailing zero decimal', () => {
    expect(formatMicros(10_000)).toBe('10 µs');
  });
});

describe('formatSeconds', () => {
  it('keeps one decimal below 10 s', () => {
    expect(formatSeconds(4.24)).toBe('4.2 s');
  });

  it('rounds to whole seconds from 10 s', () => {
    expect(formatSeconds(23.4)).toBe('23 s');
  });

  it('drops a trailing zero decimal', () => {
    expect(formatSeconds(3)).toBe('3 s');
  });
});
