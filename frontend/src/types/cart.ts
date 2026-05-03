export interface CartResponse {
  userId: string
  items: CartItemResponse[]
  itemCount: number
  grandTotal: number
}

export interface CartItemResponse {
  productId: number
  productName: string
  unitPrice: number
  quantity: number
  totalPrice: number
}

export interface AddItemRequest {
  productId: number
  quantity: number
}

export interface UpdateQuantityRequest {
  quantity: number
}
