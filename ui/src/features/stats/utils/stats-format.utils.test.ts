import { describe, expect, it } from 'vitest';

import { describeStats, formatCoordinates, formatCount, formatDuplicateRate, formatLocalTime, toMinutePoints, toPlaceShares } from './stats-format.utils';

describe('formatCoordinates', () => {
  it('formats north and east with two decimals', () => {
    expect(formatCoordinates(41.75, 2.25)).toBe('41.75°N 2.25°E');
  });

  it('formats south and west without a minus sign', () => {
    expect(formatCoordinates(-33.25, -70.75)).toBe('33.25°S 70.75°W');
  });

  it('leaves out the hemisphere letter at zero', () => {
    expect(formatCoordinates(0, 0)).toBe('0.00° 0.00°');
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

describe('describeStats', () => {
  it('says how often stats refresh when no filter is applied', () => {
    expect(describeStats({ filteredOut: 0 }, false)).toBe('Refreshed every 5 s');
  });

  it('appends the echoed filter and the hidden count', () => {
    expect(describeStats({ maxMds: 10_000, filteredOut: 1234 }, false)).toBe('Refreshed every 5 s · timing spread ≤ 10 µs · 1,234 hidden (last hour)');
  });

  it('lets a backend error win over the filter note', () => {
    expect(describeStats({ maxMds: 10_000, filteredOut: 3 }, true)).toBe('Backend unreachable, showing last data');
    expect(describeStats(undefined, true)).toBe('Backend unreachable');
  });
});
