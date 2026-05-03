import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ShoppingCart, ArrowLeft, Package, Star, Truck, Shield, RotateCcw, Plus, Minus } from 'lucide-react'
import toast from 'react-hot-toast'
import { productsApi } from '@/api/products'
import { cartApi } from '@/api/cart'
import { useAuthStore } from '@/store/authStore'
import { useCartStore } from '@/store/cartStore'
import { formatPrice, cn } from '@/lib/utils'
import { Skeleton } from '@/components/ui/Skeleton'
import Button from '@/components/ui/Button'
import Badge from '@/components/ui/Badge'

export default function ProductDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { isAuthenticated } = useAuthStore()
  const { setCart } = useCartStore()
  const [quantity, setQuantity] = useState(1)
  const [adding, setAdding] = useState(false)

  const { data: product, isLoading } = useQuery({
    queryKey: ['product', id],
    queryFn: () => productsApi.getById(Number(id)),
    enabled: !!id,
  })

  const handleAddToCart = async () => {
    if (!isAuthenticated) {
      toast.error('Sepete eklemek için giriş yapmalısın')
      navigate('/login', { state: { from: `/products/${id}` } })
      return
    }
    setAdding(true)
    try {
      const cart = await cartApi.addItem({ productId: product!.id, quantity })
      setCart(cart)
      toast.success(`${quantity} ürün sepete eklendi`)
    } catch {
      toast.error('Sepete eklenemedi')
    } finally {
      setAdding(false)
    }
  }

  if (isLoading) return <ProductDetailSkeleton />

  if (!product) return (
    <div className="max-w-4xl mx-auto px-4 py-20 text-center">
      <Package className="w-12 h-12 text-slate-300 mx-auto mb-3" />
      <p className="text-slate-500">Ürün bulunamadı</p>
      <Button variant="ghost" className="mt-4" onClick={() => navigate('/')}>Geri Dön</Button>
    </div>
  )

  const inStock = (product.stockQuantity ?? 1) > 0

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-sm text-slate-400 mb-8">
        <Link to="/" className="hover:text-indigo-600 transition-colors">Ürünler</Link>
        <span>/</span>
        <span className="text-slate-700 font-medium">{product.name}</span>
      </nav>

      <div className="grid md:grid-cols-2 gap-10">
        {/* Image */}
        <div className="bg-gradient-to-br from-slate-50 to-slate-100 rounded-3xl aspect-square flex items-center justify-center overflow-hidden">
          {product.imageUrl ? (
            <img src={product.imageUrl} alt={product.name} className="w-full h-full object-cover" />
          ) : (
            <div className="text-8xl">🛍</div>
          )}
        </div>

        {/* Info */}
        <div className="space-y-5">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <Badge variant="purple">{product.brand}</Badge>
              <Badge variant="default">{product.category}</Badge>
              {!inStock && <Badge variant="danger">Stok Yok</Badge>}
            </div>
            <h1 className="text-2xl font-bold text-slate-900 leading-tight">{product.name}</h1>

            {/* Stars */}
            <div className="flex items-center gap-2 mt-2">
              <div className="flex">
                {[...Array(5)].map((_, i) => (
                  <Star key={i} className={cn("w-4 h-4", i < 4 ? "text-amber-400 fill-amber-400" : "text-slate-200 fill-slate-200")} />
                ))}
              </div>
              <span className="text-sm text-slate-500">4.0 (128 değerlendirme)</span>
            </div>
          </div>

          <p className="text-slate-600 leading-relaxed text-sm">{product.description}</p>

          {/* Price */}
          <div className="bg-slate-50 rounded-2xl p-5">
            <p className="text-3xl font-bold text-slate-900">{formatPrice(product.basePrice)}</p>
            <p className="text-sm text-emerald-600 mt-1 font-medium">✓ Kargo bedava</p>
          </div>

          {/* Quantity + Add to Cart */}
          {inStock && (
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-1 border border-slate-200 rounded-xl overflow-hidden bg-white">
                <button
                  onClick={() => setQuantity(Math.max(1, quantity - 1))}
                  className="px-3 py-2.5 text-slate-600 hover:bg-slate-50 transition-colors"
                >
                  <Minus className="w-4 h-4" />
                </button>
                <span className="px-4 py-2.5 text-sm font-semibold text-slate-800 min-w-[48px] text-center">
                  {quantity}
                </span>
                <button
                  onClick={() => setQuantity(quantity + 1)}
                  className="px-3 py-2.5 text-slate-600 hover:bg-slate-50 transition-colors"
                >
                  <Plus className="w-4 h-4" />
                </button>
              </div>
              <Button
                onClick={handleAddToCart}
                loading={adding}
                icon={<ShoppingCart className="w-4 h-4" />}
                className="flex-1"
                size="lg"
              >
                Sepete Ekle
              </Button>
            </div>
          )}

          {/* Features */}
          <div className="grid grid-cols-3 gap-3 pt-2">
            {[
              { icon: <Truck className="w-4 h-4" />, label: 'Ücretsiz Kargo' },
              { icon: <Shield className="w-4 h-4" />, label: '2 Yıl Garanti' },
              { icon: <RotateCcw className="w-4 h-4" />, label: '30 Gün İade' },
            ].map((f) => (
              <div key={f.label} className="flex flex-col items-center gap-1.5 p-3 bg-slate-50 rounded-2xl text-center">
                <span className="text-indigo-600">{f.icon}</span>
                <span className="text-xs font-medium text-slate-600">{f.label}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <button
        onClick={() => navigate(-1)}
        className="mt-10 flex items-center gap-2 text-sm text-slate-500 hover:text-slate-800 transition-colors"
      >
        <ArrowLeft className="w-4 h-4" /> Geri
      </button>
    </div>
  )
}

function ProductDetailSkeleton() {
  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="grid md:grid-cols-2 gap-10">
        <Skeleton className="aspect-square rounded-3xl" />
        <div className="space-y-4">
          <Skeleton className="h-6 w-32" />
          <Skeleton className="h-8 w-3/4" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-16 w-full rounded-2xl" />
          <Skeleton className="h-12 w-full rounded-xl" />
        </div>
      </div>
    </div>
  )
}
