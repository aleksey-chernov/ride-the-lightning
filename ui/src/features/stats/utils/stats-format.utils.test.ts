import { describe, expect, it } from 'vitest';

import { formatCoordinates, formatCount, formatDuplicateRate, formatLocalTime, toMinutePoints, toPlaceShares } from './stats-format.utils';

describe('formatCoordinates', () => {
  it('formats north and east with one decimal', () => {
    expect(formatCoordinates(41.5, 2.5)).toBe('41.5°N 2.5°E');
  });

  it('formats south and west without a minus sign', () => {
    expect(formatCoordinates(-33.5, -70.5)).toBe('33.5°S 70.5°W');
  });

  it('leaves out the hemisphere letter at zero', () => {
    expect(formatCoordinates(0, 0)).toBe('0.0° 0.0°');
  });
});

describe('formatDuplicateRate', () => {
  it('gives the duplicate share as a percentage with one decimal', () => {
    expect(formatDuplicateRate(1000, 123)).toBe('12.3');
  });

  it('gives zero when nothing has been received', () => {
    expect(formatDuplicateRate(0, 0)).toBe('0.0');
  });
});

describe('formatCount', () => {
  it('groups thousands', () => {
    expect(formatCount(1234567)).toBe('1,234,567');
  });
});

describe('formatLocalTime', () => {
  it('shows hours and minutes in local time, zero-padded', () => {
    expect(formatLocalTime(new Date(2026, 9, 18, 9, 5).toISOString())).toBe('09:05');
  });
});

describe('toMinutePoints', () => {
  it('keeps order and counts and adds a local time label', () => {
    const first = new Date(2026, 9, 18, 23, 59).toISOString();
    const second = new Date(2026, 9, 19, 0, 0).toISOString();
    expect(
      toMinutePoints([
        { minute: first, count: 3 },
        { minute: second, count: 0 },
      ]),
    ).toEqual([
      { minute: first, label: '23:59', count: 3 },
      { minute: second, label: '00:00', count: 0 },
    ]);
  });
});

describe('toPlaceShares', () => {
  it('scales counts against the busiest place and keeps the kind', () => {
    expect(
      toPlaceShares([
        { name: 'Spain', kind: 'country', count: 40 },
        { name: 'Balearic Sea', kind: 'sea', count: 10 },
      ]),
    ).toEqual([
      { name: 'Spain', kind: 'country', count: 40, share: 1 },
      { name: 'Balearic Sea', kind: 'sea', count: 10, share: 0.25 },
    ]);
  });

  it('gives zero shares when every count is zero', () => {
    expect(toPlaceShares([{ name: 'Spain', kind: 'country', count: 0 }])).toEqual([{ name: 'Spain', kind: 'country', count: 0, share: 0 }]);
  });

  it('returns an empty list for no places', () => {
    expect(toPlaceShares([])).toEqual([]);
  });
});
