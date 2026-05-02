import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CreditCard, MapPin, ChevronRight, Lock, CheckCircle } from 'lucide-react'
import toast from 'react-hot-toast'
import { cartApi } from '@/api/cart'
import { ordersApi } from '@/api/orders'
import { useCartStore } from '@/store/cartStore'
import { formatPrice } from '@/lib/utils'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import type { CreateOrderRequest } from '@/types/order'
import { v4 as uuidv4 } from 'uuid'

type Step = 'address' | 'payment' | 'success'

const INITIAL_ADDRESS = {
  firstName: '', lastName: '', phone: '', email: '',
  address: '', city: '', country: 'Türkiye',
}
const INITIAL_CARD = {
  holderName: '', number: '', expireMonth: '', expireYear: '', cvc: '',
}

export default function CheckoutPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { setCart } = useCartStore()
  const [step, setStep] = useState<Step>('address')
  const [address, setAddress] = useState(INITIAL_ADDRESS)
  const [card, setCard] = useState(INITIAL_CARD)
  const [loading, setLoading] = useState(false)
  const [orderId, setOrderId] = useState<number | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [idempotencyKey] = useState(() => uuidv4())

  const { data: cart } = useQuery({
    queryKey: ['cart'],
    queryFn: cartApi.getCart,
  })

  const validateAddress = () => {
    const e: Record<string, string> = {}
    if (!address.firstName) e.firstName = 'Ad gerekli'
    if (!address.lastName) e.lastName = 'Soyad gerekli'
    if (!address.phone) e.phone = 'Telefon gerekli'
    if (!address.email) e.email = 'E-posta gerekli'
    if (!address.address) e.address = 'Adres gerekli'
    if (!address.city) e.city = 'Şehir gerekli'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const validateCard = () => {
    const e: Record<string, string> = {}
    if (!card.holderName) e.holderName = 'Ad Soyad gerekli'
    if (!card.number || card.number.replace(/\s/g, '').length < 16) e.number = 'Geçerli kart no girin'
    if (!card.expireMonth) e.expireMonth = 'Ay gerekli'
    if (!card.expireYear) e.expireYear = 'Yıl gerekli'
    if (!card.cvc || card.cvc.length < 3) e.cvc = 'CVV gerekli'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleAddressNext = () => {
    if (validateAddress()) setStep('payment')
  }

  const handlePlaceOrder = async () => {
    if (!validateCard()) return
    setLoading(true)
    try {
      const req: CreateOrderRequest = {
        items: (cart?.items ?? []).map(item => ({
          productId: item.productId,
          quantity: item.quantity,
          unitPrice: item.unitPrice,
          productName: item.productName,
        })),
        shippingAddress: address,
        card: {
          ...card,
          number: card.number.replace(/\s/g, ''),
        },
      }
      const order = await ordersApi.create(req, idempotencyKey)
      setOrderId(order.id)
      // Backend cart'ı temizle (sessizce — sipariş zaten oluştu)
      try { await cartApi.clearCart() } catch { /* ignore */ }
      queryClient.invalidateQueries({ queryKey: ['cart'] })
      setCart(null)
      setStep('success')
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Sipariş oluşturulamadı'
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  const formatCardNumber = (val: string) => {
    return val.replace(/\D/g, '').slice(0, 16).replace(/(.{4})/g, '$1 ').trim()
  }

  const af = (field: keyof typeof address) => ({
    value: address[field],
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => setAddress({ ...address, [field]: e.target.value }),
    error: errors[field],
  })

  if (step === 'success') {
    return (
      <div className="max-w-md mx-auto px-4 py-20 text-center animate-fade-in-up">
        <div className="w-20 h-20 bg-emerald-100 rounded-3xl flex items-center justify-center mx-auto mb-6">
          <CheckCircle className="w-10 h-10 text-emerald-500" />
        </div>
        <h1 className="text-2xl font-bold text-slate-900 mb-2">Siparişiniz Alındı!</h1>
        <p className="text-slate-500 mb-2">Sipariş #{orderId} oluşturuldu.</p>
        <p className="text-sm text-slate-400 mb-8">
          Ödeme işleminiz devam ediyor. Bildirim e-postanıza gönderilecek.
        </p>
        <div className="flex gap-3 justify-center">
          <Button onClick={() => navigate('/orders')}>Siparişlerime Git</Button>
          <Button variant="outline" onClick={() => navigate('/')}>Alışverişe Devam</Button>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <h1 className="text-2xl font-bold text-slate-900 mb-8">Ödeme</h1>

      {/* Steps indicator */}
      <div className="flex items-center gap-2 mb-8">
        {(['address', 'payment'] as Step[]).map((s, i) => (
          <div key={s} className="flex items-center gap-2">
            <div className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold transition-all ${
              step === s ? 'bg-indigo-600 text-white' :
              (i === 0 && step === 'payment') ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'
            }`}>
              <span className="w-5 h-5 rounded-full border-2 flex items-center justify-center text-xs border-current">
                {i === 0 && step === 'payment' ? '✓' : i + 1}
              </span>
              {s === 'address' ? 'Teslimat' : 'Ödeme'}
            </div>
            {i < 1 && <ChevronRight className="w-4 h-4 text-slate-300" />}
          </div>
        ))}
      </div>

      <div className="grid lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          {/* Address Step */}
          {step === 'address' && (
            <div className="bg-white rounded-3xl border border-slate-100 p-6 animate-fade-in">
              <div className="flex items-center gap-2 mb-5">
                <MapPin className="w-5 h-5 text-indigo-600" />
                <h2 className="text-base font-bold text-slate-800">Teslimat Adresi</h2>
              </div>
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-3">
                  <Input label="Ad" placeholder="Ali" {...af('firstName')} />
                  <Input label="Soyad" placeholder="Yılmaz" {...af('lastName')} />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <Input label="Telefon" placeholder="0555 000 0000" type="tel" {...af('phone')} />
                  <Input label="E-posta" placeholder="ali@email.com" type="email" {...af('email')} />
                </div>
                <Input label="Adres" placeholder="Mahalle, Cadde, Bina No, Daire" {...af('address')} />
                <div className="grid grid-cols-2 gap-3">
                  <Input label="Şehir" placeholder="İstanbul" {...af('city')} />
                  <Input label="Ülke" placeholder="Türkiye" {...af('country')} />
                </div>
                <Button onClick={handleAddressNext} className="w-full" size="lg">
                  Devam Et <ChevronRight className="w-4 h-4" />
                </Button>
              </div>
            </div>
          )}

          {/* Payment Step */}
          {step === 'payment' && (
            <div className="bg-white rounded-3xl border border-slate-100 p-6 animate-fade-in">
              <div className="flex items-center gap-2 mb-5">
                <CreditCard className="w-5 h-5 text-indigo-600" />
                <h2 className="text-base font-bold text-slate-800">Kart Bilgileri</h2>
                <span className="ml-auto flex items-center gap-1 text-xs text-slate-400">
                  <Lock className="w-3 h-3" /> Güvenli ödeme
                </span>
              </div>

              {/* Test card info */}
              <div className="bg-indigo-50 rounded-xl p-3 mb-5 text-xs text-indigo-700">
                <strong>Test kartı:</strong> 5528 7900 0000 0008 | 12/30 | 123
              </div>

              <div className="space-y-4">
                <Input
                  label="Kart Üzerindeki Ad"
                  placeholder="ALİ YILMAZ"
                  value={card.holderName}
                  onChange={(e) => setCard({ ...card, holderName: e.target.value.toUpperCase() })}
                  error={errors.holderName}
                />
                <Input
                  label="Kart Numarası"
                  placeholder="0000 0000 0000 0000"
                  value={card.number}
                  onChange={(e) => setCard({ ...card, number: formatCardNumber(e.target.value) })}
                  error={errors.number}
                  maxLength={19}
                  icon={<CreditCard className="w-4 h-4" />}
                />
                <div className="grid grid-cols-3 gap-3">
                  <Input
                    label="Ay"
                    placeholder="12"
                    maxLength={2}
                    value={card.expireMonth}
                    onChange={(e) => setCard({ ...card, expireMonth: e.target.value.replace(/\D/g, '') })}
                    error={errors.expireMonth}
                  />
                  <Input
                    label="Yıl"
                    placeholder="2030"
                    maxLength={4}
                    value={card.expireYear}
                    onChange={(e) => setCard({ ...card, expireYear: e.target.value.replace(/\D/g, '') })}
                    error={errors.expireYear}
                  />
                  <Input
                    label="CVV"
                    placeholder="123"
                    maxLength={4}
                    value={card.cvc}
                    onChange={(e) => setCard({ ...card, cvc: e.target.value.replace(/\D/g, '') })}
                    error={errors.cvc}
                  />
                </div>
                <div className="flex gap-3 pt-1">
                  <Button variant="outline" onClick={() => setStep('address')} className="flex-1">
                    Geri
                  </Button>
                  <Button onClick={handlePlaceOrder} loading={loading} className="flex-1" size="lg">
                    {cart ? `${formatPrice(cart.grandTotal)} Öde` : 'Sipariş Ver'}
                  </Button>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Order summary */}
        <div>
          <div className="bg-white rounded-2xl border border-slate-100 p-5 sticky top-24">
            <h2 className="text-sm font-bold text-slate-800 mb-4">Sipariş Özeti</h2>
            {cart?.items.map((item) => (
              <div key={item.productId} className="flex justify-between text-xs text-slate-600 py-1.5 border-b border-slate-50 last:border-0">
                <span className="truncate mr-2 max-w-[140px]">{item.productName} <span className="text-slate-400">x{item.quantity}</span></span>
                <span className="font-medium shrink-0">{formatPrice(item.totalPrice)}</span>
              </div>
            ))}
            <div className="flex justify-between font-bold text-slate-900 pt-3 mt-1">
              <span>Toplam</span>
              <span>{cart ? formatPrice(cart.grandTotal) : '-'}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
