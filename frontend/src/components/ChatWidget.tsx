import { useEffect, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { X, Send, Sparkles, Loader2 } from 'lucide-react'
import { chatApi } from '@/api/chat'
import type { ChatResponse } from '@/types/chat'
import { cn } from '@/lib/utils'

/**
 * Bir tur konuşmayı temsil eder: kullanıcı sorgusu ve servis cevabı.
 */
interface Turn {
    id: number
    query: string
    response: ChatResponse | null
    error: string | null
}

/**
 * Floating chatbot widget.
 *
 * Sağ alt köşede sabit bir buton görünür; tıklanınca chat penceresi
 * açılır. Kullanıcı doğal dil sorgusu yazar ("1000 tl altı kulaklık" gibi),
 * arka tarafta chat-service AI veya keyword extraction ile filter çıkarır
 * ve product-service'ten ürünleri çeker.
 *
 * Anonim erişime açıktır — login zorunlu değil.
 */
export default function ChatWidget() {
    const [open, setOpen] = useState(false)
    const [input, setInput] = useState('')
    const [turns, setTurns] = useState<Turn[]>([])
    const navigate = useNavigate()
    const scrollRef = useRef<HTMLDivElement>(null)
    const inputRef = useRef<HTMLInputElement>(null)

    const mutation = useMutation({
        mutationFn: (query: string) => chatApi.search({ query }, 'tr'),
    })

    // Yeni mesaj gelince scroll en alta in
    useEffect(() => {
        if (scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight
        }
    }, [turns, mutation.isPending])

    // Pencere açılınca input'a focus
    useEffect(() => {
        if (open) {
            setTimeout(() => inputRef.current?.focus(), 100)
        }
    }, [open])

    const handleSend = () => {
        const query = input.trim()
        if (!query || mutation.isPending) return

        const turnId = Date.now()
        const newTurn: Turn = { id: turnId, query, response: null, error: null }
        setTurns((prev) => [...prev, newTurn])
        setInput('')

        mutation.mutate(query, {
            onSuccess: (data) => {
                setTurns((prev) =>
                    prev.map((t) => (t.id === turnId ? { ...t, response: data } : t))
                )
            },
            onError: (err: any) => {
                const msg = err?.response?.data?.message || err?.message || 'Bir hata oluştu'
                setTurns((prev) =>
                    prev.map((t) => (t.id === turnId ? { ...t, error: msg } : t))
                )
            },
        })
    }

    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault()
            handleSend()
        }
    }

    const handleProductClick = (id: number) => {
        setOpen(false)
        navigate(`/products/${id}`)
    }

    const formatPrice = (price: number) =>
        new Intl.NumberFormat('tr-TR', {
            style: 'currency',
            currency: 'TRY',
            maximumFractionDigits: 0,
        }).format(price)

    return (
        <>
            {/* Floating button */}
            <button
                onClick={() => setOpen(true)}
                className={cn(
                    'fixed bottom-6 right-6 z-50',
                    'flex items-center gap-2 px-5 py-3.5',
                    'bg-gradient-to-br from-indigo-600 to-purple-600 text-white',
                    'rounded-full shadow-lg shadow-indigo-300',
                    'hover:shadow-xl hover:shadow-indigo-400 hover:scale-105',
                    'transition-all duration-200',
                    open && 'opacity-0 pointer-events-none scale-95'
                )}
                aria-label="AI ile ürün ara"
            >
                <Sparkles className="w-5 h-5" />
                <span className="font-semibold text-sm">AI Asistan</span>
            </button>

            {/* Chat window */}
            {open && (
                <div
                    className={cn(
                        'fixed bottom-6 right-6 z-50',
                        'w-[380px] max-w-[calc(100vw-3rem)] h-[560px] max-h-[calc(100vh-3rem)]',
                        'bg-white rounded-2xl shadow-2xl border border-slate-200',
                        'flex flex-col overflow-hidden'
                    )}
                >
                    {/* Header */}
                    <div className="flex items-center justify-between px-5 py-4 bg-gradient-to-br from-indigo-600 to-purple-600 text-white">
                        <div className="flex items-center gap-2.5">
                            <div className="w-9 h-9 rounded-full bg-white/20 flex items-center justify-center">
                                <Sparkles className="w-5 h-5" />
                            </div>
                            <div>
                                <div className="font-semibold text-sm">AI Alışveriş Asistanı</div>
                                <div className="text-xs text-indigo-100">Ne aramak istersin?</div>
                            </div>
                        </div>
                        <button
                            onClick={() => setOpen(false)}
                            className="p-1.5 rounded-lg hover:bg-white/20 transition-colors"
                            aria-label="Kapat"
                        >
                            <X className="w-5 h-5" />
                        </button>
                    </div>

                    {/* Messages */}
                    <div
                        ref={scrollRef}
                        className="flex-1 overflow-y-auto px-4 py-4 space-y-4 bg-slate-50"
                    >
                        {/* Welcome message */}
                        {turns.length === 0 && !mutation.isPending && (
                            <div className="space-y-3">
                                <BotMessage>Merhaba! 👋 Sana hangi ürünü bulayım?</BotMessage>
                                <div className="text-xs text-slate-500 px-1">Örnek sorular:</div>
                                <div className="flex flex-wrap gap-2">
                                    {[
                                        '10000 tl altı kulaklık',
                                        'Apple ürünleri',
                                        '50000 tl altı laptop',
                                        'spor ayakkabı',
                                    ].map((sample) => (
                                        <button
                                            key={sample}
                                            onClick={() => {
                                                setInput(sample)
                                                inputRef.current?.focus()
                                            }}
                                            className="text-xs px-3 py-1.5 bg-white border border-slate-200 rounded-full hover:bg-indigo-50 hover:border-indigo-300 hover:text-indigo-700 transition-colors"
                                        >
                                            {sample}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Turns */}
                        {turns.map((turn) => (
                            <div key={turn.id} className="space-y-3">
                                {/* User message */}
                                <UserMessage>{turn.query}</UserMessage>

                                {/* Bot response */}
                                {turn.response && (
                                    <>
                                        <BotMessage>{turn.response.reply}</BotMessage>

                                        {/* Interpreted filter chips */}
                                        {hasFilters(turn.response.interpretedFilter) && (
                                            <div className="flex flex-wrap gap-1.5 pl-1">
                                                {turn.response.interpretedFilter.category && (
                                                    <FilterChip
                                                        label="Kategori"
                                                        value={turn.response.interpretedFilter.category}
                                                    />
                                                )}
                                                {turn.response.interpretedFilter.brand && (
                                                    <FilterChip
                                                        label="Marka"
                                                        value={turn.response.interpretedFilter.brand}
                                                    />
                                                )}
                                                {turn.response.interpretedFilter.minPrice != null && (
                                                    <FilterChip
                                                        label="Min"
                                                        value={formatPrice(turn.response.interpretedFilter.minPrice)}
                                                    />
                                                )}
                                                {turn.response.interpretedFilter.maxPrice != null && (
                                                    <FilterChip
                                                        label="Max"
                                                        value={formatPrice(turn.response.interpretedFilter.maxPrice)}
                                                    />
                                                )}
                                            </div>
                                        )}

                                        {/* Product cards */}
                                        {turn.response.products.length > 0 && (
                                            <div className="space-y-2 pl-1">
                                                {turn.response.products.map((product) => (
                                                    <button
                                                        key={product.id}
                                                        onClick={() => handleProductClick(product.id)}
                                                        className="w-full flex items-center gap-3 p-2.5 bg-white border border-slate-200 rounded-xl hover:border-indigo-300 hover:shadow-md transition-all text-left group"
                                                    >
                                                        {product.imageUrl ? (
                                                            <img
                                                                src={product.imageUrl}
                                                                alt={product.name}
                                                                className="w-14 h-14 rounded-lg object-cover bg-slate-100 flex-shrink-0"
                                                                onError={(e) => {
                                                                    ;(e.target as HTMLImageElement).style.display = 'none'
                                                                }}
                                                            />
                                                        ) : (
                                                            <div className="w-14 h-14 rounded-lg bg-slate-100 flex-shrink-0" />
                                                        )}
                                                        <div className="flex-1 min-w-0">
                                                            <div className="text-sm font-medium text-slate-900 truncate group-hover:text-indigo-700">
                                                                {product.name}
                                                            </div>
                                                            <div className="text-xs text-slate-500 truncate">
                                                                {product.brand} • {product.category}
                                                            </div>
                                                            <div className="text-sm font-semibold text-indigo-600 mt-0.5">
                                                                {formatPrice(product.basePrice)}
                                                            </div>
                                                        </div>
                                                    </button>
                                                ))}
                                            </div>
                                        )}
                                    </>
                                )}

                                {/* Error */}
                                {turn.error && <BotMessage error>{turn.error}</BotMessage>}
                            </div>
                        ))}

                        {/* Pending */}
                        {mutation.isPending && (
                            <div className="flex items-center gap-2 text-sm text-slate-500 pl-1">
                                <Loader2 className="w-4 h-4 animate-spin" />
                                <span>Düşünüyorum...</span>
                            </div>
                        )}
                    </div>

                    {/* Input */}
                    <div className="border-t border-slate-200 p-3 bg-white">
                        <div className="flex items-center gap-2">
                            <input
                                ref={inputRef}
                                type="text"
                                value={input}
                                onChange={(e) => setInput(e.target.value)}
                                onKeyDown={handleKeyDown}
                                placeholder="Ne aramak istersin?"
                                disabled={mutation.isPending}
                                className="flex-1 px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:border-transparent disabled:opacity-50"
                            />
                            <button
                                onClick={handleSend}
                                disabled={!input.trim() || mutation.isPending}
                                className="p-2.5 bg-indigo-600 text-white rounded-full hover:bg-indigo-700 active:bg-indigo-800 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                                aria-label="Gönder"
                            >
                                <Send className="w-4 h-4" />
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </>
    )
}

// ============================================================
// Helper components
// ============================================================

function BotMessage({
                        children,
                        error = false,
                    }: {
    children: React.ReactNode
    error?: boolean
}) {
    return (
        <div className="flex items-start gap-2">
            <div
                className={cn(
                    'w-7 h-7 rounded-full flex items-center justify-center flex-shrink-0 mt-0.5',
                    error
                        ? 'bg-red-100 text-red-600'
                        : 'bg-gradient-to-br from-indigo-500 to-purple-600 text-white'
                )}
            >
                <Sparkles className="w-3.5 h-3.5" />
            </div>
            <div
                className={cn(
                    'px-3.5 py-2.5 rounded-2xl rounded-tl-sm text-sm max-w-[85%]',
                    error
                        ? 'bg-red-50 text-red-800 border border-red-100'
                        : 'bg-white text-slate-800 border border-slate-200'
                )}
            >
                {children}
            </div>
        </div>
    )
}

function UserMessage({ children }: { children: React.ReactNode }) {
    return (
        <div className="flex justify-end">
            <div className="px-3.5 py-2.5 rounded-2xl rounded-tr-sm text-sm max-w-[85%] bg-indigo-600 text-white">
                {children}
            </div>
        </div>
    )
}

function FilterChip({ label, value }: { label: string; value: string }) {
    return (
        <span className="inline-flex items-center gap-1 text-[11px] px-2 py-1 bg-indigo-50 text-indigo-700 border border-indigo-200 rounded-full">
      <span className="font-medium opacity-70">{label}:</span>
      <span className="font-semibold">{value}</span>
    </span>
    )
}

function hasFilters(f: {
    category: string | null
    brand: string | null
    minPrice: number | null
    maxPrice: number | null
}): boolean {
    return f.category != null || f.brand != null || f.minPrice != null || f.maxPrice != null
}