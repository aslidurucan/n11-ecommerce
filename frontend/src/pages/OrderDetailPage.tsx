import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Package, MapPin, CreditCard } from 'lucide-react'
import { ordersApi } from '@/api/orders'
import { formatPrice, formatDate } from '@/lib/utils'
import Badge from '@/components/ui/Badge'
import { Skeleton } from '@/components/ui/Skeleton'
import type { OrderStatus } from '@/types/order'

const statusMap: Record<OrderStatus, { label: string; variant: 'default' | 'success' | 'warning' | 'danger' | 'info' | 'purple' }> = {
  PENDING: { label: 'Bekliyor', variant: 'warning' },
  STOCK_RESERVED: { label: 'Stok Ayrıldı', variant: 'info' },
  PAYMENT_PROCESSING: { label: 'Ödeme İşlemde', variant: 'purple' },
  COMPLETED: { label: 'Tamamlandı', variant: 'success' },
  PAYMENT_FAILED: { label: 'Ödeme Başarısız', variant: 'danger' },
  CANCELLED: { label: 'İptal Edildi', variant: 'danger' },
}

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>()

  const { data: order, isLoading } = useQuery({
    queryKey: ['order', id],
    queryFn: () => ordersApi.getById(Number(id)),
    enabled: !!id,
  })

  if (isLoading) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-8 space-y-4">
        <Skeleton className="h-6 w-32" />
        <Skeleton className="h-32 rounded-2xl" />
        <Skeleton className="h-48 rounded-2xl" />
      </div>
    )
  }

  if (!order) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-20 text-center">
        <Package className="w-12 h-12 text-slate-300 mx-auto mb-3" />
        <p className="text-slate-500">Sipariş bulunamadı</p>
        <Link to="/orders" className="text-sm text-indigo-600 mt-2 inline-block">← Siparişlerime Dön</Link>
      </div>
    )
  }

  const status = statusMap[order.status] ?? { label: order.status, variant: 'default' as const }

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <Link to="/orders" className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-indigo-600 transition-colors mb-6">
        <ArrowLeft className="w-4 h-4" /> Siparişlerime Dön
      </Link>

      {/* Header */}
      <div className="bg-white rounded-2xl border border-slate-100 p-6 mb-4">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h1 className="text-xl font-bold text-slate-900">Sipariş #{order.id}</h1>
            <p className="text-sm text-slate-400 mt-0.5">{formatDate(order.createdAt)}</p>
          </div>
          <div className="flex items-center gap-3">
            <Badge variant={status.variant} className="text-sm px-3 py-1">{status.label}</Badge>
            <span className="text-xl font-bold text-slate-900">{formatPrice(order.totalAmount)}</span>
          </div>
        </div>
      </div>

      <div className="grid md:grid-cols-2 gap-4 mb-4">
        {/* Shipping address */}
        <div className="bg-white rounded-2xl border border-slate-100 p-5">
          <div className="flex items-center gap-2 mb-3">
            <MapPin className="w-4 h-4 text-indigo-600" />
            <h2 className="text-sm font-bold text-slate-800">Teslimat Adresi</h2>
          </div>
          <div className="text-sm text-slate-600 space-y-0.5">
            <p className="font-semibold text-slate-800">{order.shipFirstName} {order.shipLastName}</p>
            <p>{order.shipAddress}</p>
            <p>{order.shipCity}, {order.shipCountry}</p>
            <p>{order.shipPhone}</p>
            <p>{order.shipEmail}</p>
          </div>
        </div>

        {/* Payment */}
        <div className="bg-white rounded-2xl border border-slate-100 p-5">
          <div className="flex items-center gap-2 mb-3">
            <CreditCard className="w-4 h-4 text-indigo-600" />
            <h2 className="text-sm font-bold text-slate-800">Ödeme Bilgisi</h2>
          </div>
          <div className="text-sm text-slate-600 space-y-1">
            <div className="flex justify-between">
              <span>Durum</span>
              <Badge variant={status.variant}>{status.label}</Badge>
            </div>
            {order.paymentId && (
              <div className="flex justify-between">
                <span>Ödeme ID</span>
                <span className="font-mono text-xs text-slate-500 truncate max-w-[140px]">{order.paymentId}</span>
              </div>
            )}
            <div className="flex justify-between font-bold text-slate-800 pt-1">
              <span>Toplam</span>
              <span>{formatPrice(order.totalAmount)}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Items */}
      <div className="bg-white rounded-2xl border border-slate-100 p-5">
        <h2 className="text-sm font-bold text-slate-800 mb-4">Ürünler ({order.items.length})</h2>
        <div className="divide-y divide-slate-50">
          {order.items.map((item) => (
            <div key={item.productId} className="flex items-center justify-between py-3 first:pt-0 last:pb-0">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-slate-100 rounded-xl flex items-center justify-center text-lg shrink-0">
                  🛍
                </div>
                <div>
                  <Link to={`/products/${item.productId}`} className="text-sm font-medium text-slate-800 hover:text-indigo-600 transition-colors">
                    {item.productName}
                  </Link>
                  <p className="text-xs text-slate-400">{formatPrice(item.unitPrice)} x {item.quantity}</p>
                </div>
              </div>
              <span className="text-sm font-bold text-slate-900">
                {formatPrice(item.unitPrice * item.quantity)}
              </span>
            </div>
          ))}
        </div>
        <div className="flex justify-between font-bold text-slate-900 pt-4 mt-2 border-t border-slate-100">
          <span>Toplam</span>
          <span>{formatPrice(order.totalAmount)}</span>
        </div>
      </div>
    </div>
  )
}
