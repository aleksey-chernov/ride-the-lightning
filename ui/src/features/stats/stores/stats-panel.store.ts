import { create } from 'zustand';
import { persist } from 'zustand/middleware';

type StatsPanelState = {
  open: boolean;
  setOpen: (open: boolean) => void;
};

/** Whether the stats panel is unfolded; remembered across visits in localStorage. */
export const useStatsPanelStore = create<StatsPanelState>()(
  persist(
    (set) => ({
      open: true,
      setOpen: (open) => set({ open }),
    }),
    { name: 'rtl.stats-panel' },
  ),
);
