import { useEffect, useRef } from 'react'
import { Client, IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import toast from 'react-hot-toast'
import { useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import type { OrderNotification } from '@/types/notification'


export function useOrderNotifications() {
    const user = useAuthStore((state) => state.user)
    const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
    const queryClient = useQueryClient()
    const clientRef = useRef<Client | null>(null)
    const seenOrderIds = useRef<Set<number>>(new Set())

    useEffect(() => {
        if (!isAuthenticated || !user?.keycloakId) {
            return
        }

        const myUserId = user.keycloakId
        // Bu zaman damgasindan onceki event'leri yok say (eski/birikmis mesajlar)
        const connectionEstablishedAt = Date.now()

        const wsUrl = `${window.location.origin}/ws`

        const client = new Client({
            webSocketFactory: () => new SockJS(wsUrl) as any,
            reconnectDelay: 5000,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
            debug: () => {},
        })

        client.onConnect = () => {
            client.subscribe('/topic/orders', (message: IMessage) => {
                try {
                    const notification = JSON.parse(message.body) as OrderNotification

                    // 1. Topic broadcast — sadece kendi siparis bildirimlerimi goster
                    if (notification.userId !== myUserId) {
                        return
                    }

                    // 2. Ayni siparis icin tekrar tekrar toast cikmasin
                    if (seenOrderIds.current.has(notification.orderId)) {
                        return
                    }
                    seenOrderIds.current.add(notification.orderId)

                    // 3. Baglanti kurulmadan ONCE olusan event'leri yok say
                    //    (RabbitMQ'da birikmis eski mesajlar)
                    const eventTime = new Date(notification.occurredAt).getTime()
                    if (eventTime < connectionEstablishedAt - 10000) {
                        // 10sn'den daha eski event = baglantidan once olusmus
                        return
                    }

                    // Siparis listesini yenile
                    queryClient.invalidateQueries({ queryKey: ['orders'] })

                    if (notification.status === 'COMPLETED') {
                        toast.success(
                            `🎉 Siparişiniz onaylandı! (Sipariş #${notification.orderId})`,
                            { duration: 6000 }
                        )
                    } else if (notification.status === 'CANCELLED') {
                        toast.error(
                            `Siparişiniz iptal edildi: ${notification.reason || 'Bilinmeyen sebep'}`,
                            { duration: 8000 }
                        )
                    }
                } catch (err) {
                    console.error('Geçersiz WebSocket mesajı:', err)
                }
            })
        }

        client.onStompError = (frame) => {
            console.error('STOMP error:', frame.headers['message'])
        }

        client.activate()
        clientRef.current = client

        return () => {
            if (clientRef.current?.active) {
                clientRef.current.deactivate()
            }
            clientRef.current = null
        }
    }, [isAuthenticated, user?.keycloakId, queryClient])
}