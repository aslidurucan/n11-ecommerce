import { create } from 'zustand'
import type { CartResponse } from '@/types/cart'

interface CartState {
  cart: CartResponse | null
  itemCount: number
  setCart: (cart: CartResponse | null) => void
  clearCart: () => void
}

export const useCartStore = create<CartState>()((set) => ({
  cart: null,
  itemCount: 0,

  setCart: (cart) =>
    set({
      cart,
      itemCount: cart?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0,
    }),

  clearCart: () => set({ cart: null, itemCount: 0 }),
}))
