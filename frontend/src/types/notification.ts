export interface OrderNotification {
    orderId: number
    status: 'COMPLETED' | 'CANCELLED'
    userId: string
    totalAmount: number | null
    reason: string | null
    occurredAt: string
}