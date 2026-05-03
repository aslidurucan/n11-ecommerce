import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Trash2, Plus, Minus, ShoppingBag, ArrowRight } from 'lucide-react'
import toast from 'react-hot-toast'
import { cartApi } from '@/api/cart'
import { useAuthStore } from '@/store/authStore'
import { useCartStore } from '@/store/cartStore'
import { formatPrice } from '@/lib/utils'
import Button from '@/components/ui/Button'
import EmptyState from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'

export default function CartPage() {
  const { isAuthenticated } = useAuthStore()
  const { setCart } = useCartStore()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [updatingId, setUpdatingId] = useState<number | null>(null)

  const { data: cart, isLoading } = useQuery({
    queryKey: ['cart'],
    queryFn: cartApi.getCart,
    enabled: isAuthenticated,
  })

  useEffect(() => {
    if (cart) setCart(cart)
  }, [cart, setCart])

  const updateMutation = useMutation({
    mutationFn: ({ productId, quantity }: { productId: number; quantity: number }) =>
      cartApi.updateQuantity(productId, { quantity }),
    onSuccess: (data) => {
      setCart(data)
      queryClient.setQueryData(['cart'], data)
    },
    onError: () => toast.error('Güncelleme başarısız'),
  })

  const removeMutation = useMutation({
    mutationFn: (productId: number) => cartApi.removeItem(productId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cart'] }),
    onError: () => toast.error('Silme başarısız'),
  })

  const handleQuantityChange = async (productId: number, newQty: number) => {
    if (newQty < 1) return
    setUpdatingId(productId)
    await updateMutation.mutateAsync({ productId, quantity: newQty })
    setUpdatingId(null)
  }

  if (!isAuthenticated) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-16">
        <EmptyState
          icon={<ShoppingBag className="w-8 h-8" />}
          title="Sepetini görmek için giriş yap"
          description="Hesabına giriş yaptıktan sonra sepetini yönetebilirsin"
          action={<Button onClick={() => navigate('/login')}>Giriş Yap</Button>}
        />
      </div>
    )
  }

  if (isLoading) return <CartSkeleton />

  if (!cart || cart.items.length === 0) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-16">
        <EmptyState
          icon={<ShoppingBag className="w-8 h-8" />}
          title="Sepetiniz boş"
          description="Ürünlere göz atarak alışverişe başlayabilirsiniz"
          action={<Link to="/"><Button>Alışverişe Başla</Button></Link>}
        />
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <h1 className="text-2xl font-bold text-slate-900 mb-8">
        Sepetim <span className="text-slate-400 font-normal text-lg">({cart.items.length} ürün)</span>
      </h1>

      <div className="grid lg:grid-cols-3 gap-6">
        {/* Items */}
        <div className="lg:col-span-2 space-y-3">
          {cart.items.map((item) => (
            <div
              key={item.productId}
              className="bg-white rounded-2xl border border-slate-100 p-4 flex gap-4 animate-fade-in"
            >
              {/* Image placeholder */}
              <div className="w-20 h-20 rounded-xl bg-gradient-to-br from-slate-50 to-slate-100 flex items-center justify-center shrink-0 text-2xl">
                🛍
              </div>

              <div className="flex-1 min-w-0">
                <Link
                  to={`/products/${item.productId}`}
                  className="text-sm font-semibold text-slate-800 hover:text-indigo-600 transition-colors line-clamp-2"
                >
                  {item.productName}
                </Link>
                <p className="text-xs text-slate-400 mt-0.5">{formatPrice(item.unitPrice)} / adet</p>

                <div className="flex items-center justify-between mt-3">
                  {/* Qty controls */}
                  <div className="flex items-center gap-1 border border-slate-200 rounded-xl overflow-hidden">
                    <button
                      onClick={() => handleQuantityChange(item.productId, item.quantity - 1)}
                      disabled={updatingId === item.productId || item.quantity <= 1}
                      className="px-2.5 py-1.5 text-slate-600 hover:bg-slate-50 disabled:opacity-40 transition-colors"
                    >
                      <Minus className="w-3 h-3" />
                    </button>
                    <span className="px-3 py-1.5 text-sm font-semibold text-slate-800 min-w-[32px] text-center">
                      {updatingId === item.productId ? '...' : item.quantity}
                    </span>
                    <button
                      onClick={() => handleQuantityChange(item.productId, item.quantity + 1)}
                      disabled={updatingId === item.productId}
                      className="px-2.5 py-1.5 text-slate-600 hover:bg-slate-50 disabled:opacity-40 transition-colors"
                    >
                      <Plus className="w-3 h-3" />
                    </button>
                  </div>

                  <div className="flex items-center gap-3">
                    <span className="text-sm font-bold text-slate-900">{formatPrice(item.totalPrice)}</span>
                    <button
                      onClick={() => removeMutation.mutate(item.productId)}
                      className="p-1.5 rounded-lg text-slate-400 hover:text-red-500 hover:bg-red-50 transition-all"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Summary */}
        <div>
          <div className="bg-white rounded-2xl border border-slate-100 p-5 sticky top-24">
            <h2 className="text-base font-bold text-slate-800 mb-4">Sipariş Özeti</h2>

            <div className="space-y-2.5 text-sm">
              {cart.items.map((item) => (
                <div key={item.productId} className="flex justify-between text-slate-600">
                  <span className="truncate mr-2 max-w-[160px]">{item.productName} <span className="text-slate-400">x{item.quantity}</span></span>
                  <span className="shrink-0 font-medium">{formatPrice(item.totalPrice)}</span>
                </div>
              ))}
              <div className="border-t border-slate-100 pt-2.5 mt-2.5 flex justify-between font-bold text-slate-900">
                <span>Toplam</span>
                <span>{formatPrice(cart.grandTotal)}</span>
              </div>
              <div className="flex justify-between text-emerald-600 text-xs font-medium">
                <span>Kargo</span>
                <span>Ücretsiz</span>
              </div>
            </div>

            <Button
              onClick={() => navigate('/checkout')}
              className="w-full mt-5"
              size="lg"
              icon={<ArrowRight className="w-4 h-4" />}
            >
              Ödemeye Geç
            </Button>

            <Link to="/" className="block text-center text-sm text-slate-500 hover:text-indigo-600 mt-3 transition-colors">
              Alışverişe devam et
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}

function CartSkeleton() {
  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <Skeleton className="h-8 w-40 mb-8" />
      <div className="grid lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="bg-white rounded-2xl p-4 flex gap-4">
              <Skeleton className="w-20 h-20 rounded-xl shrink-0" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-3 w-1/4" />
                <Skeleton className="h-8 w-32 mt-4" />
              </div>
            </div>
          ))}
        </div>
        <Skeleton className="h-64 rounded-2xl" />
      </div>
    </div>
  )
}
