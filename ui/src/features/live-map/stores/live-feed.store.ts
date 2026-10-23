import { create } from 'zustand';

import type { ConnectionStatus } from '@shared/types/connection-status';

type LiveFeedState = {
  status: ConnectionStatus;
  setStatus: (status: ConnectionStatus) => void;
};

/** State of the live strike feed, readable anywhere (e.g. the app header) without prop drilling. */
export const useLiveFeedStore = create<LiveFeedState>()((set) => ({
  status: 'connecting',
  setStatus: (status) => set({ status }),
}));
