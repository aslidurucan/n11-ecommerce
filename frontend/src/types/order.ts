export interface CreateOrderRequest {
  items: OrderItemRequest[]
  shippingAddress: ShippingAddressRequest
  card: CardRequest
}

export interface OrderItemRequest {
  productId: number
  quantity: number
  unitPrice: number
  productName: string
}

export interface ShippingAddressRequest {
  firstName: string
  lastName: string
  phone: string
  email: string
  address: string
  city: string
  country: string
}

export interface CardRequest {
  holderName: string
  number: string
  expireMonth: string
  expireYear: string
  cvc: string
}

export interface OrderResponse {
  id: number
  userId: string
  status: OrderStatus
  totalAmount: number
  items: OrderItemResponse[]
  shipFirstName: string
  shipLastName: string
  shipEmail: string
  shipPhone: string
  shipAddress: string
  shipCity: string
  shipCountry: string
  paymentId?: string
  createdAt: string
  updatedAt: string
}

export interface OrderItemResponse {
  productId: number
  productName: string
  unitPrice: number
  quantity: number
}

export type OrderStatus =
  | 'PENDING'
  | 'STOCK_RESERVED'
  | 'PAYMENT_PROCESSING'
  | 'COMPLETED'
  | 'PAYMENT_FAILED'
  | 'CANCELLED'
