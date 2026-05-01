import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Package, ChevronRight } from 'lucide-react'
import { ordersApi } from '@/api/orders'
import { formatPrice, formatDate } from '@/lib/utils'
import Badge from '@/components/ui/Badge'
import EmptyState from '@/components/ui/EmptyState'
import { OrderCardSkeleton } from '@/components/ui/Skeleton'
import Button from '@/components/ui/Button'
import type { OrderStatus } from '@/types/order'

const statusMap: Record<OrderStatus, { label: string; variant: 'default' | 'success' | 'warning' | 'danger' | 'info' | 'purple' }> = {
  PENDING: { label: 'Bekliyor', variant: 'warning' },
  STOCK_RESERVED: { label: 'Stok Ayrıldı', variant: 'info' },
  PAYMENT_PROCESSING: { label: 'Ödeme İşlemde', variant: 'purple' },
  COMPLETED: { label: 'Tamamlandı', variant: 'success' },
  PAYMENT_FAILED: { label: 'Ödeme Başarısız', variant: 'danger' },
  CANCELLED: { label: 'İptal Edildi', variant: 'danger' },
}

export default function OrdersPage() {
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['orders', page],
    queryFn: () => ordersApi.getMyOrders(page, 10),
  })

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <h1 className="text-2xl font-bold text-slate-900 mb-8">Siparişlerim</h1>

      {isLoading && (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => <OrderCardSkeleton key={i} />)}
        </div>
      )}

      {!isLoading && (!data || data.content.length === 0) && (
        <EmptyState
          icon={<Package className="w-8 h-8" />}
          title="Henüz siparişiniz yok"
          description="İlk siparişinizi vermek için alışverişe başlayın"
          action={<Link to="/"><Button>Alışverişe Başla</Button></Link>}
        />
      )}

      {data && data.content.length > 0 && (
        <>
          <div className="space-y-3">
            {data.content.map((order) => {
              const status = statusMap[order.status] ?? { label: order.status, variant: 'default' as const }
              return (
                <Link
                  key={order.id}
                  to={`/orders/${order.id}`}
                  className="block bg-white rounded-2xl border border-slate-100 p-5 hover:shadow-md hover:border-slate-200 transition-all group animate-fade-in-up"
                >
                  <div className="flex items-start justify-between gap-4">
                    <div className="space-y-1.5">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-bold text-slate-900">#{order.id}</span>
                        <Badge variant={status.variant}>{status.label}</Badge>
                      </div>
                      <p className="text-xs text-slate-400">{formatDate(order.createdAt)}</p>
                      <p className="text-xs text-slate-500">
                        {order.items.length} ürün · {order.items.map(i => i.productName).slice(0, 2).join(', ')}
                        {order.items.length > 2 && ` +${order.items.length - 2} daha`}
                      </p>
                    </div>
                    <div className="flex items-center gap-3 shrink-0">
                      <span className="text-base font-bold text-slate-900">{formatPrice(order.totalAmount)}</span>
                      <ChevronRight className="w-4 h-4 text-slate-400 group-hover:text-indigo-600 group-hover:translate-x-0.5 transition-all" />
                    </div>
                  </div>
                </Link>
              )
            })}
          </div>

          {/* Pagination */}
          {data.totalPages > 1 && (
            <div className="flex justify-center gap-2 mt-8">
              <Button variant="outline" size="sm" disabled={data.first} onClick={() => setPage(page - 1)}>Önceki</Button>
              <span className="px-4 py-2 text-sm text-slate-500">{page + 1} / {data.totalPages}</span>
              <Button variant="outline" size="sm" disabled={data.last} onClick={() => setPage(page + 1)}>Sonraki</Button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
