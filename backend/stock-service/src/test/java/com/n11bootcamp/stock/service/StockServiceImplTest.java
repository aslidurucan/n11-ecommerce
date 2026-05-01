package com.n11bootcamp.stock.service;

import com.n11bootcamp.stock.dto.ReservationItem;
import com.n11bootcamp.stock.dto.StockResponse;
import com.n11bootcamp.stock.dto.UpdateStockRequest;
import com.n11bootcamp.stock.entity.ProductStock;
import com.n11bootcamp.stock.entity.StockReservation;
import com.n11bootcamp.stock.exception.StockNotFoundException;
import com.n11bootcamp.stock.mapper.StockMapper;
import com.n11bootcamp.stock.repository.ProductStockRepository;
import com.n11bootcamp.stock.repository.StockReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StockServiceImpl davranış testleri.
 *
 * Test edilen senaryolar:
 *  - Reservation happy path: Sipariş için stok rezerve edilir
 *  - Insufficient stock: Yetersiz stok varsa rezervasyon yapılmaz, productId listesi döner
 *  - Idempotency: Aynı orderId 2. kez gelirse skip edilir (boş liste döner)
 *  - Release happy path: Rezervasyon iade edilir, kayıtlar silinir
 *  - Release no-op: Hiç rezervasyon yoksa exception fırlatmadan döner
 *  - Increase/Decrease: Pozitif/negatif edge case'leri
 *  - getStock not found: StockNotFoundException
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StockServiceImpl — reservation, idempotency, race condition coverage")
class StockServiceImplTest {

    @Mock private ProductStockRepository stockRepo;
    @Mock private StockReservationRepository reservationRepo;
    @Mock private StockMapper stockMapper;

    @InjectMocks
    private StockServiceImpl stockService;

    // ====================================================================
    // RESERVATION — HAPPY PATH
    // ====================================================================
    @Test
    @DisplayName("Reservation happy path: yeterli stok varsa rezerve edilir, boş liste döner")
    void reserveStock_whenSufficient_reservesAndReturnsEmpty() {
        // GIVEN
        Long orderId = 1L;
        List<ReservationItem> items = List.of(
                new ReservationItem(101L, 2),
                new ReservationItem(102L, 3)
        );

        ProductStock stock1 = buildStock(101L, 10, 0);
        ProductStock stock2 = buildStock(102L, 5, 0);

        when(reservationRepo.existsByOrderId(orderId)).thenReturn(false);
        when(stockRepo.findAllByIdForUpdate(List.of(101L, 102L)))
                .thenReturn(List.of(stock1, stock2));

        // WHEN
        List<Long> insufficient = stockService.reserveStock(orderId, items);

        // THEN: hiç eksik yok
        assertThat(insufficient).isEmpty();

        // Stock'lar reduce edilmiş
        assertThat(stock1.getAvailableQuantity()).isEqualTo(8);
        assertThat(stock1.getReservedQuantity()).isEqualTo(2);
        assertThat(stock2.getAvailableQuantity()).isEqualTo(2);
        assertThat(stock2.getReservedQuantity()).isEqualTo(3);

        // 2 reservation kaydı oluşturulmuş
        verify(reservationRepo, times(2)).save(any(StockReservation.class));
    }

    // ====================================================================
    // RESERVATION — INSUFFICIENT STOCK
    // ====================================================================
    @Test
    @DisplayName("Insufficient stock: yetersiz ürünlerin productId listesi döner, hiç rezerve edilmez")
    void reserveStock_whenInsufficient_returnsProductIdsAndDoesNotReserve() {
        // GIVEN
        Long orderId = 1L;
        List<ReservationItem> items = List.of(
                new ReservationItem(101L, 2),
                new ReservationItem(102L, 100)
        );

        ProductStock stock1 = buildStock(101L, 10, 0);
        ProductStock stock2 = buildStock(102L, 5, 0);

        when(reservationRepo.existsByOrderId(orderId)).thenReturn(false);
        when(stockRepo.findAllByIdForUpdate(List.of(101L, 102L)))
                .thenReturn(List.of(stock1, stock2));

        // WHEN
        List<Long> insufficient = stockService.reserveStock(orderId, items);

        // THEN: sadece 102 yetersiz
        assertThat(insufficient).containsExactly(102L);

        // KRİTİK: HİÇBİR rezervasyon kaydı oluşturulmamış (tümü ya hep ya hiç)
        verify(reservationRepo, never()).save(any(StockReservation.class));

        // Stock'lar değişmemiş
        assertThat(stock1.getAvailableQuantity()).isEqualTo(10);
        assertThat(stock1.getReservedQuantity()).isEqualTo(0);
        assertThat(stock2.getAvailableQuantity()).isEqualTo(5);
        assertThat(stock2.getReservedQuantity()).isEqualTo(0);
    }

    // ====================================================================
    // RESERVATION — IDEMPOTENCY
    // ====================================================================
    @Test
    @DisplayName("Idempotency: aynı orderId 2. kez gelirse hiçbir şey yapılmaz")
    void reserveStock_whenAlreadyReserved_skipsAndReturnsEmpty() {
        // GIVEN: bu order zaten rezerve edilmiş
        Long orderId = 1L;
        List<ReservationItem> items = List.of(new ReservationItem(101L, 2));

        when(reservationRepo.existsByOrderId(orderId)).thenReturn(true);

        // WHEN
        List<Long> insufficient = stockService.reserveStock(orderId, items);

        // THEN: boş liste döner ama yan etki yok
        assertThat(insufficient).isEmpty();

        // KRİTİK: stok ne lock'lanmış, ne de rezervasyon kaydı oluşturulmuş
        verify(stockRepo, never()).findAllByIdForUpdate(any());
        verify(reservationRepo, never()).save(any(StockReservation.class));
    }

    // ====================================================================
    // RELEASE — HAPPY PATH
    // ====================================================================
    @Test
    @DisplayName("Release happy path: rezervasyon iade edilir, kayıtlar silinir")
    void releaseStock_whenReservationsExist_releasesAndDeletes() {
        // GIVEN: order için 2 rezervasyon var
        Long orderId = 1L;
        StockReservation r1 = StockReservation.builder()
                .orderId(orderId).productId(101L).quantity(2).build();
        StockReservation r2 = StockReservation.builder()
                .orderId(orderId).productId(102L).quantity(3).build();

        ProductStock stock1 = buildStock(101L, 8, 2);
        ProductStock stock2 = buildStock(102L, 2, 3);

        when(reservationRepo.findByOrderId(orderId)).thenReturn(List.of(r1, r2));
        when(stockRepo.findAllByIdForUpdate(List.of(101L, 102L)))
                .thenReturn(List.of(stock1, stock2));

        // WHEN
        stockService.releaseStock(orderId);

        // THEN: stoklar geri verilmiş (release: available++)
        assertThat(stock1.getAvailableQuantity()).isEqualTo(10);
        assertThat(stock1.getReservedQuantity()).isEqualTo(0);
        assertThat(stock2.getAvailableQuantity()).isEqualTo(5);
        assertThat(stock2.getReservedQuantity()).isEqualTo(0);

        // Kayıtlar silinmiş
        verify(reservationRepo, times(1)).deleteByOrderId(orderId);
    }

    // ====================================================================
    // RELEASE — NO-OP
    // ====================================================================
    @Test
    @DisplayName("Release no-op: rezervasyon yoksa exception fırlatmaz, sessizce döner")
    void releaseStock_whenNoReservations_isNoOp() {
        // GIVEN: hiç rezervasyon yok
        Long orderId = 999L;
        when(reservationRepo.findByOrderId(orderId)).thenReturn(Collections.emptyList());

        // WHEN
        stockService.releaseStock(orderId);

        // THEN: stok lock'lanmamış, deleteByOrderId çağrılmamış
        verify(stockRepo, never()).findAllByIdForUpdate(any());
        verify(reservationRepo, never()).deleteByOrderId(any());
    }

    // ====================================================================
    // INCREASE STOCK
    // ====================================================================
    @Test
    @DisplayName("Increase: pozitif delta ile available artar")
    void increaseStock_whenPositiveDelta_increasesAvailable() {
        ProductStock stock = buildStock(101L, 5, 0);
        when(stockRepo.findByIdForUpdate(101L)).thenReturn(Optional.of(stock));
        when(stockMapper.toResponse(any())).thenReturn(buildStockResponse(101L, 15));

        stockService.increaseStock(101L, 10);

        assertThat(stock.getAvailableQuantity()).isEqualTo(15);
        verify(stockRepo, times(1)).save(stock);
    }

    @Test
    @DisplayName("Increase: 0 veya negatif delta IllegalArgumentException fırlatır")
    void increaseStock_whenNonPositiveDelta_throws() {
        assertThatThrownBy(() -> stockService.increaseStock(101L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Delta must be positive");

        assertThatThrownBy(() -> stockService.increaseStock(101L, -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Delta must be positive");

        verify(stockRepo, never()).findByIdForUpdate(anyLong());
    }

    // ====================================================================
    // DECREASE STOCK
    // ====================================================================
    @Test
    @DisplayName("Decrease: yeterli stok varsa available azalır")
    void decreaseStock_whenSufficient_decreasesAvailable() {
        ProductStock stock = buildStock(101L, 10, 0);
        when(stockRepo.findByIdForUpdate(101L)).thenReturn(Optional.of(stock));
        when(stockMapper.toResponse(any())).thenReturn(buildStockResponse(101L, 7));

        stockService.decreaseStock(101L, 3);

        assertThat(stock.getAvailableQuantity()).isEqualTo(7);
        verify(stockRepo, times(1)).save(stock);
    }

    @Test
    @DisplayName("Decrease: yetersiz stokta IllegalStateException fırlatır")
    void decreaseStock_whenInsufficient_throws() {
        ProductStock stock = buildStock(101L, 5, 0);
        when(stockRepo.findByIdForUpdate(101L)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> stockService.decreaseStock(101L, 100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient stock");

        // Stok değişmemiş, save çağrılmamış
        assertThat(stock.getAvailableQuantity()).isEqualTo(5);
        verify(stockRepo, never()).save(any());
    }

    // ====================================================================
    // GET STOCK
    // ====================================================================
    @Test
    @DisplayName("getStock: ürün yoksa StockNotFoundException")
    void getStock_whenNotFound_throwsStockNotFoundException() {
        when(stockRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.getStock(999L))
                .isInstanceOf(StockNotFoundException.class);
    }

    // ====================================================================
    // SET STOCK (admin)
    // ====================================================================
    @Test
    @DisplayName("setStock: yeni ürün için yeni stock yaratır")
    void setStock_whenNewProduct_createsNewStock() {
        UpdateStockRequest request = new UpdateStockRequest();
        request.setProductId(999L);
        request.setQuantity(50);

        when(stockRepo.findById(999L)).thenReturn(Optional.empty());
        when(stockMapper.toResponse(any())).thenReturn(buildStockResponse(999L, 50));

        StockResponse response = stockService.setStock(request);

        assertThat(response).isNotNull();
        verify(stockRepo, times(1)).save(any(ProductStock.class));
    }

    // ====================================================================
    // TEST DATA BUILDERS
    // ====================================================================
    private ProductStock buildStock(Long productId, int available, int reserved) {
        return ProductStock.builder()
                .productId(productId)
                .availableQuantity(available)
                .reservedQuantity(reserved)
                .build();
    }

    private StockResponse buildStockResponse(Long productId, int available) {
        StockResponse response = new StockResponse();
        response.setProductId(productId);
        response.setAvailableQuantity(available);
        response.setReservedQuantity(0);
        return response;
    }
}