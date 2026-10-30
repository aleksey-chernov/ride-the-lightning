import { describe, expect, it } from 'vitest';

import { formatSeconds } from './units.utils';

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
