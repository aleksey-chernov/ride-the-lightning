import { create } from 'zustand';

import type { ConnectionStatus } from '@shared/types/connection-status';

type LiveFeedState = {
  status: ConnectionStatus;
  count: number; // strikes in the window, refreshed once a second
  setStatus: (status: ConnectionStatus) => void;
  setCount: (count: number) => void;
};

/** State of the live strike feed, readable anywhere (e.g. the app header) without prop drilling. */
export const useLiveFeedStore = create<LiveFeedState>()((set) => ({
  status: 'connecting',
  count: 0,
  setStatus: (status) => set({ status }),
  setCount: (count) => set({ count }),
}));
