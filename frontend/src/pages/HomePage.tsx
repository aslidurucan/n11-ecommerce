import { useState, useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSearchParams, Link } from 'react-router-dom'
import { ShoppingCart, SlidersHorizontal, X, ChevronLeft, ChevronRight, Star } from 'lucide-react'
import toast from 'react-hot-toast'
import { productsApi } from '@/api/products'
import { cartApi } from '@/api/cart'
import { useAuthStore } from '@/store/authStore'
import { useCartStore } from '@/store/cartStore'
import { formatPrice, cn } from '@/lib/utils'
import { ProductCardSkeleton } from '@/components/ui/Skeleton'
import Button from '@/components/ui/Button'
import Badge from '@/components/ui/Badge'
import type { ProductFilterRequest } from '@/types/product'

const CATEGORIES = ['Elektronik', 'Bilgisayar', 'Tablet', 'Ses', 'Spor', 'Ev & Yaşam']
const BRANDS = ['Apple', 'Samsung', 'Sony', 'Asus', 'Adidas', 'Nike', 'Dyson']
const SORT_OPTIONS = [
  { value: 'basePrice,asc', label: 'Fiyat: Düşükten Yükseğe' },
  { value: 'basePrice,desc', label: 'Fiyat: Yüksekten Düşüğe' },
  { value: 'createdAt,desc', label: 'En Yeni' },
]

export default function HomePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { isAuthenticated } = useAuthStore()
  const { setCart } = useCartStore()
  const [addingId, setAddingId] = useState<number | null>(null)
  const [filtersOpen, setFiltersOpen] = useState(false)

  const filters: ProductFilterRequest = {
    page: Number(searchParams.get('page') ?? 0),
    size: 12,
    q: searchParams.get('q') ?? undefined,
    category: searchParams.get('category') ?? undefined,
    brand: searchParams.get('brand') ?? undefined,
    minPrice: searchParams.get('minPrice') ? Number(searchParams.get('minPrice')) : undefined,
    maxPrice: searchParams.get('maxPrice') ? Number(searchParams.get('maxPrice')) : undefined,
    sort: searchParams.get('sort') ?? undefined,
  }

  const { data, isLoading } = useQuery({
    queryKey: ['products', filters],
    queryFn: () => productsApi.getAll(filters),
  })

  const setFilter = useCallback(
    (key: string, value: string | null) => {
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev)
        if (value === null || value === '') next.delete(key)
        else next.set(key, value)
        next.set('page', '0')
        return next
      })
    },
    [setSearchParams]
  )

  // Birden fazla filtre parametresini tek seferde günceller (price range için)
  const setManyFilters = useCallback(
    (updates: Record<string, string | null>) => {
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev)
        Object.entries(updates).forEach(([key, value]) => {
          if (value === null || value === '') next.delete(key)
          else next.set(key, value)
        })
        next.set('page', '0')
        return next
      })
    },
    [setSearchParams]
  )

  const setPage = (page: number) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      next.set('page', String(page))
      return next
    })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const handleAddToCart = async (productId: number) => {
    if (!isAuthenticated) {
      toast.error('Sepete eklemek için giriş yapmalısın')
      return
    }
    setAddingId(productId)
    try {
      const cart = await cartApi.addItem({ productId, quantity: 1 })
      setCart(cart)
      toast.success('Sepete eklendi')
    } catch {
      toast.error('Sepete eklenemedi')
    } finally {
      setAddingId(null)
    }
  }

  // Client-side text search: q ile ad/marka/kategori filtreleme
  const searchQuery = filters.q?.toLowerCase().trim()
  const displayedProducts = searchQuery
    ? (data?.content ?? []).filter((p) =>
        p.name.toLowerCase().includes(searchQuery) ||
        p.brand?.toLowerCase().includes(searchQuery) ||
        p.category?.toLowerCase().includes(searchQuery)
      )
    : (data?.content ?? [])

  const activeFiltersCount = [filters.q, filters.category, filters.brand, filters.minPrice, filters.maxPrice].filter(Boolean).length

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Hero banner */}
      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-indigo-600 via-violet-600 to-purple-700 p-8 mb-10 text-white">
        <div className="relative z-10">
          <Badge variant="purple" className="bg-white/20 text-white mb-3">🛍 Yeni Sezon</Badge>
          <h1 className="text-3xl md:text-4xl font-bold mb-2">En İyi Ürünler,<br />En Uygun Fiyatlar</h1>
          <p className="text-indigo-100 text-sm mb-5">Binlerce ürün arasından seçimini yap</p>
          <Button variant="secondary" size="sm" className="text-indigo-700 font-semibold">
            Alışverişe Başla
          </Button>
        </div>
        {/* Decorative circles */}
        <div className="absolute -right-10 -top-10 w-56 h-56 bg-white/5 rounded-full" />
        <div className="absolute -right-4 bottom-0 w-36 h-36 bg-white/5 rounded-full" />
      </div>

      <div className="flex gap-8">
        {/* Sidebar Filters — desktop */}
        <aside className="hidden lg:block w-56 shrink-0 space-y-6">
          <FilterPanel
            filters={filters}
            onFilter={setFilter}
            onFilterMany={setManyFilters}
          />
        </aside>

        <div className="flex-1 min-w-0">
          {/* Toolbar */}
          <div className="flex items-center justify-between mb-5 gap-3">
            <div className="flex items-center gap-3 flex-wrap">
              <button
                onClick={() => setFiltersOpen(!filtersOpen)}
                className="lg:hidden flex items-center gap-2 px-3 py-2 rounded-xl border border-slate-200 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                <SlidersHorizontal className="w-4 h-4" />
                Filtrele
                {activeFiltersCount > 0 && (
                  <span className="w-5 h-5 bg-indigo-600 text-white text-xs rounded-full flex items-center justify-center">
                    {activeFiltersCount}
                  </span>
                )}
              </button>

              {/* Active filter chips */}
              {filters.q && (
                <FilterChip label={`"${filters.q}"`} onRemove={() => setFilter('q', null)} />
              )}
              {filters.category && (
                <FilterChip label={filters.category} onRemove={() => setFilter('category', null)} />
              )}
              {filters.brand && (
                <FilterChip label={filters.brand} onRemove={() => setFilter('brand', null)} />
              )}
            </div>

            <div className="flex items-center gap-3">
              {data && (
                <span className="text-sm text-slate-500 whitespace-nowrap">
                  {searchQuery
                    ? `${displayedProducts.length} sonuç`
                    : `${data.totalElements} ürün`}
                </span>
              )}
              <select
                value={filters.sort ?? ''}
                onChange={(e) => setFilter('sort', e.target.value || null)}
                className="text-sm rounded-xl border border-slate-200 px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500 cursor-pointer"
              >
                <option value="">Sırala</option>
                {SORT_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Mobile filter panel */}
          {filtersOpen && (
            <div className="lg:hidden mb-5 p-4 bg-white rounded-2xl border border-slate-100 shadow-sm animate-fade-in">
              <FilterPanel filters={filters} onFilter={setFilter} onFilterMany={setManyFilters} />
            </div>
          )}

          {/* Product grid */}
          <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-4">
            {isLoading
              ? Array.from({ length: 12 }).map((_, i) => <ProductCardSkeleton key={i} />)
              : displayedProducts.map((product) => (
                  <div
                    key={product.id}
                    className="group bg-white rounded-2xl border border-slate-100 overflow-hidden hover:shadow-xl hover:shadow-slate-200/60 hover:-translate-y-1 transition-all duration-300 animate-fade-in-up"
                  >
                    {/* Image */}
                    <Link to={`/products/${product.id}`}>
                      <div className="aspect-square bg-gradient-to-br from-slate-50 to-slate-100 relative overflow-hidden">
                        {product.imageUrl ? (
                          <img
                            src={product.imageUrl}
                            alt={product.name}
                            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                          />
                        ) : (
                          <div className="w-full h-full flex items-center justify-center text-4xl">
                            🛍
                          </div>
                        )}
                        {product.stockQuantity === 0 && (
                          <div className="absolute inset-0 bg-white/70 flex items-center justify-center">
                            <Badge variant="danger">Stok Yok</Badge>
                          </div>
                        )}
                      </div>
                    </Link>

                    <div className="p-3.5">
                      <div className="mb-0.5">
                        <span className="text-xs text-slate-400 font-medium">{product.brand}</span>
                      </div>
                      <Link to={`/products/${product.id}`}>
                        <h3 className="text-sm font-semibold text-slate-800 line-clamp-2 hover:text-indigo-600 transition-colors leading-snug">
                          {product.name}
                        </h3>
                      </Link>
                      <div className="flex items-center gap-1 mt-1 mb-3">
                        {[...Array(5)].map((_, i) => (
                          <Star key={i} className={cn("w-3 h-3", i < 4 ? "text-amber-400 fill-amber-400" : "text-slate-200 fill-slate-200")} />
                        ))}
                        <span className="text-xs text-slate-400 ml-0.5">(4.0)</span>
                      </div>
                      <div className="flex items-center justify-between">
                        <span className="text-base font-bold text-slate-900">
                          {formatPrice(product.basePrice)}
                        </span>
                        <button
                          onClick={() => handleAddToCart(product.id)}
                          disabled={addingId === product.id || product.stockQuantity === 0}
                          className={cn(
                            'w-8 h-8 rounded-xl flex items-center justify-center transition-all duration-200',
                            'bg-indigo-600 text-white hover:bg-indigo-700 active:scale-95',
                            'disabled:opacity-50 disabled:cursor-not-allowed'
                          )}
                        >
                          {addingId === product.id ? (
                            <svg className="animate-spin w-3.5 h-3.5" fill="none" viewBox="0 0 24 24">
                              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                            </svg>
                          ) : (
                            <ShoppingCart className="w-3.5 h-3.5" />
                          )}
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
          </div>

          {/* Arama sonucu yok */}
          {!isLoading && searchQuery && displayedProducts.length === 0 && (
            <div className="col-span-full py-16 text-center text-slate-400">
              <p className="text-lg font-medium mb-1">Sonuç bulunamadı</p>
              <p className="text-sm">"{filters.q}" için eşleşen ürün yok.</p>
            </div>
          )}

          {/* Pagination */}
          {data && data.totalPages > 1 && !searchQuery && (
            <div className="flex items-center justify-center gap-2 mt-10">
              <button
                onClick={() => setPage(filters.page! - 1)}
                disabled={data.first}
                className="p-2 rounded-xl border border-slate-200 text-slate-500 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              {Array.from({ length: Math.min(data.totalPages, 7) }).map((_, i) => {
                const page = i
                const active = page === filters.page
                return (
                  <button
                    key={page}
                    onClick={() => setPage(page)}
                    className={cn(
                      'w-9 h-9 rounded-xl text-sm font-medium transition-all',
                      active
                        ? 'bg-indigo-600 text-white shadow-sm shadow-indigo-200'
                        : 'border border-slate-200 text-slate-600 hover:bg-slate-50'
                    )}
                  >
                    {page + 1}
                  </button>
                )
              })}
              <button
                onClick={() => setPage(filters.page! + 1)}
                disabled={data.last}
                className="p-2 rounded-xl border border-slate-200 text-slate-500 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// ---- Sub-components ----

function FilterPanel({
  filters,
  onFilter,
  onFilterMany,
}: {
  filters: ProductFilterRequest
  onFilter: (key: string, value: string | null) => void
  onFilterMany: (updates: Record<string, string | null>) => void
}) {
  return (
    <div className="space-y-6">
      <div>
        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3">Kategori</h3>
        <div className="space-y-1">
          {CATEGORIES.map((cat) => {
            const isActive = (filters.category ?? '') === cat
            return (
              <button
                key={cat}
                onClick={() => onFilter('category', isActive ? null : cat)}
                className={cn(
                  'w-full text-left px-3 py-2 rounded-xl text-sm font-medium transition-all',
                  isActive
                    ? 'bg-indigo-600 text-white'
                    : 'text-slate-600 hover:bg-slate-100'
                )}
              >
                {cat}
              </button>
            )
          })}
        </div>
      </div>

      <div>
        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3">Marka</h3>
        <div className="space-y-1">
          {BRANDS.map((brand) => {
            const isActive = (filters.brand ?? '') === brand
            return (
              <button
                key={brand}
                onClick={() => onFilter('brand', isActive ? null : brand)}
                className={cn(
                  'w-full text-left px-3 py-2 rounded-xl text-sm font-medium transition-all',
                  isActive
                    ? 'bg-indigo-600 text-white'
                    : 'text-slate-600 hover:bg-slate-100'
                )}
              >
                {brand}
              </button>
            )
          })}
        </div>
      </div>

      <div>
        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3">Fiyat Aralığı</h3>
        <div className="space-y-2">
          {[
            { label: '0 - 10.000 ₺', min: '0', max: '10000' },
            { label: '10.000 - 30.000 ₺', min: '10000', max: '30000' },
            { label: '30.000 - 60.000 ₺', min: '30000', max: '60000' },
            { label: '60.000 ₺ +', min: '60000', max: '' },
          ].map((r) => {
            const active = String(filters.minPrice ?? '') === r.min
            return (
              <button
                key={r.label}
                onClick={() => {
                  if (active) {
                    onFilterMany({ minPrice: null, maxPrice: null })
                  } else {
                    onFilterMany({ minPrice: r.min, maxPrice: r.max || null })
                  }
                }}
                className={cn(
                  'w-full text-left px-3 py-2 rounded-xl text-sm font-medium transition-all',
                  active ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-100'
                )}
              >
                {r.label}
              </button>
            )
          })}
        </div>
      </div>
    </div>
  )
}

function FilterChip({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-indigo-50 text-indigo-700 text-xs font-semibold">
      {label}
      <button onClick={onRemove} className="hover:text-indigo-900">
        <X className="w-3 h-3" />
      </button>
    </span>
  )
}
