package com.n11bootcamp.stock.service;

import com.n11bootcamp.stock.dto.ReservationItem;
import com.n11bootcamp.stock.dto.StockResponse;
import com.n11bootcamp.stock.dto.UpdateStockRequest;

import java.util.List;


public interface StockService {

    // ===================== ADMIN YÖNETİM =====================

    /**
     * Ürünün stok bilgisini getirir.
     * Redis cache'den döner (5 dakika TTL), yoksa DB'den okur.
     *
     * @throws com.n11bootcamp.stock.exception.StockNotFoundException ürün stok kaydı yoksa
     */
    StockResponse getStock(Long productId);


    StockResponse setStock(UpdateStockRequest request);

    /**
     * Mevcut stoka delta kadar ekler (depo girişi, iade vb.).
     * Pessimistic lock ile race condition'a kapalı.
     * Cache'i temizler.
     *
     * @throws com.n11bootcamp.stock.exception.StockNotFoundException ürün stok kaydı yoksa
     */
    StockResponse increaseStock(Long productId, int delta);

    /**
     * Mevcut stoktan delta kadar çıkarır (fire, kayıp vb.).
     * Stok yetersizse IllegalStateException fırlatır.
     * Cache'i temizler.
     *
     * @throws com.n11bootcamp.stock.exception.StockNotFoundException ürün stok kaydı yoksa
     * @throws IllegalStateException delta > available ise
     */
    StockResponse decreaseStock(Long productId, int delta);

    // ===================== SAGA =====================

    /**
     * Verilen sipariş kalemleri için stok rezervasyonu yapar.
     *
     * İki-pass stratejisi:
     *   1. Tüm ürünleri kontrol et — hepsinde yeterli stok var mı?
     *   2. Hepsi tamam → hepsini atomik olarak rezerve et (ya hep ya hiç)
     *
     * Pessimistic lock + sıralı ID → deadlock önlenir.
     * Idempotency: aynı orderId için rezervasyon zaten varsa tekrar işlemez.
     *
     * @return yetersiz stok bulunan productId listesi. Boş ise rezervasyon başarılı.
     */
    List<Long> reserveStock(Long orderId, List<ReservationItem> items);

    /**
     * Compensation: Ödeme başarısız olunca rezerve edilen stoğu geri açar.
     *
     * stock_reservations tablosundan bu orderId için yapılan rezervasyonları bulur,
     * her birini geri açar ve rezervasyon kayıtlarını siler.
     *
     * İdempotent: rezervasyon kaydı yoksa sessizce geçer (zaten release edilmiş demektir).
     */
    void releaseStock(Long orderId);
}
