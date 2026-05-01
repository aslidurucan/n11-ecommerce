import { Outlet } from 'react-router-dom'
import Header from './Header'
import Footer from './Footer'
import { useEffect } from 'react'
import { useAuthStore } from '@/store/authStore'
import { useCartStore } from '@/store/cartStore'
import { cartApi } from '@/api/cart'

export default function Layout() {
  const { isAuthenticated } = useAuthStore()
  const { setCart } = useCartStore()

  // Sync cart when authenticated
  useEffect(() => {
    if (!isAuthenticated) return
    cartApi.getCart().then(setCart).catch(() => {})
  }, [isAuthenticated, setCart])

  return (
    <div className="min-h-dvh flex flex-col">
      <Header />
      <main className="flex-1">
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}
