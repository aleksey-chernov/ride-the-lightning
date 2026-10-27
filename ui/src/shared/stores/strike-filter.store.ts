import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/** Our own cut-off for the timing-spread filter, not a Blitzortung threshold (see docs/phase-8.md). */
export const TIGHT_MDS_NS = 10_000;

type StrikeFilterState = {
  tightOnly: boolean;
  setTightOnly: (tightOnly: boolean) => void;
};

/** Remembered strike filter, shared by the map, the stats panel and the header switch. */
export const useStrikeFilterStore = create<StrikeFilterState>()(
  persist(
    (set) => ({
      tightOnly: false,
      setTightOnly: (tightOnly) => set({ tightOnly }),
    }),
    { name: 'rtl.strikeFilter' },
  ),
);

/** The `mds` limit in ns to apply, or null for no filter. */
export const selectMaxMds = (s: StrikeFilterState): number | null => (s.tightOnly ? TIGHT_MDS_NS : null);
