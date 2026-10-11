import { create } from 'zustand';
import { marketGroupById, type MarketGroupId } from './marketGroups';

interface MarketFilters {
  groupId: MarketGroupId;
  order: string;
  ascending: boolean;
  search: string;
  selectGroup: (id: MarketGroupId) => void;
  setOrder: (order: string) => void;
  setSearch: (search: string) => void;
}

const initial = marketGroupById('all');

/**
 * Markets page filters live outside the page so opening a market, or another tab, and coming back
 * keeps the chosen topic, sort and search. In memory only: a fresh app launch starts from defaults.
 */
export const useMarketFilters = create<MarketFilters>((set) => ({
  groupId: 'all',
  order: initial.defaultOrder,
  ascending: initial.defaultAscending,
  search: '',
  selectGroup: (id) => {
    const g = marketGroupById(id);
    set({ groupId: id, order: g.defaultOrder, ascending: g.defaultAscending });
  },
  setOrder: (order) => set({ order }),
  setSearch: (search) => set({ search }),
}));
