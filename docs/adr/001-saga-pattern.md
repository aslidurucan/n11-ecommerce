# ADR 001: Distributed Transaction için Saga Pattern Seçimi

**Tarih:** 2026-04
**Durum:** Kabul edildi
**Karar verenler:** Backend ekip

## Bağlam

E-ticaret platformunda checkout akışı, birden fazla servisi koordine etmeyi gerektiriyor:
1. Order kaydı (order-service'in DB'si)
2. Stok rezervasyonu (stock-service'in DB'si)
3. Ödeme (Iyzico — external API)

Bu üç işlemin atomic olması istenir. Aşağıdaki yaklaşımları değerlendirdik:

## Değerlendirilen Seçenekler

### Seçenek A: Distributed Transaction (XA / 2PC)
- ✅ Atomic guarantee
- ❌ External API (Iyzico) XA katılımcısı olamaz
- ❌ Performans kötü, kilitler uzun süre tutulur
- ❌ Modern microservices ekosistemi (Spring Cloud) XA önermez
- ❌ Single point of failure (transaction coordinator)

### Seçenek B: Saga Pattern - Orchestration
- ✅ Akış merkezi bir yerde (orchestrator) tanımlı, izlenmesi kolay
- ✅ Karmaşık business rules orchestrator'da
- ❌ Orchestrator tek bir servis — coupling yaratır
- ❌ Servis sayısı azken (3-4) overhead'i fazla

### Seçenek C: Saga Pattern - Choreography ⭐
- ✅ Event-driven, loose coupling
- ✅ Her servis kendi adımını tanımlar
- ✅ Az servis için pratik
- ❌ Akışı izlemek zor (distributed tracing gerekir)
- ❌ Karmaşık iş mantığında "who does what when" kafa karıştırabilir

## Karar

**Choreography-based Saga Pattern** seçildi.

## Gerekçe

1. **Servis sayısı az (3 servis Saga'ya katılıyor):** orchestrator overhead'i yararından fazla
2. **Loose coupling**: Her servis sadece event tanımına bağımlı, diğerlerini bilmek zorunda değil
3. **External API dahil edilebilir**: Iyzico XA bilmez ama event akışına dahil
4. **Eventual consistency kabul edilebilir**: e-ticaret context'inde saniye altı tutarsızlık tolere edilebilir

## Sonuçları

- **Compensation events** her servis için tanımlandı (örn: PaymentFailedEvent → Stock release)
- **State machine** her aggregate'te zorunlu (OrderStatus, StockReservationStatus)
- **Distributed tracing** ileride eklenmeli (Spring Cloud Sleuth + Zipkin)
- **Saga timeout monitoring** eklenmeli — bir adım stuck kalırsa alarm

## Referanslar

- Chris Richardson, "Microservices Patterns", Bölüm 4
- [microservices.io/patterns/data/saga.html](https://microservices.io/patterns/data/saga.html)
