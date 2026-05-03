package com.n11bootcamp.order.service.payment;

import com.iyzipay.Options;
import com.iyzipay.model.*;
import com.iyzipay.request.CreatePaymentRequest;
import com.n11bootcamp.order.dto.CardRequest;
import com.n11bootcamp.order.entity.Order;
import com.n11bootcamp.order.entity.OrderItem;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "payment.mock", havingValue = "false", matchIfMissing = true)
@Slf4j
public class IyzicoPaymentServiceImpl implements IyzicoPaymentService {

    private static final String CB_NAME = "iyzico";

    @Value("${iyzico.api-key}")    private String apiKey;
    @Value("${iyzico.secret-key}") private String secretKey;
    @Value("${iyzico.base-url}")   private String baseUrl;

    @Value("${iyzico.buyer.default-identity-number:11111111111}")
    private String defaultIdentityNumber;

    @Value("${iyzico.buyer.default-ip:127.0.0.1}")
    private String defaultBuyerIp;

    @PostConstruct
    void validateConfig() {
        if ("127.0.0.1".equals(defaultBuyerIp) && !baseUrl.contains("sandbox")) {
            log.warn("[IYZICO] iyzico.buyer.default-ip is '127.0.0.1' but baseUrl does not look like sandbox: {}. " +
                     "Set IYZICO_BUYER_DEFAULT_IP env var to the real buyer IP for production.", baseUrl);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "chargeFallback")
    public IyzicoPaymentService.PaymentResult charge(Order order, CardRequest card) {
        Options options = buildOptions();
        CreatePaymentRequest request = buildPaymentRequest(order, card);

        try {
            Payment payment = Payment.create(request, options);
            return interpretPaymentResponse(payment, order.getId());
        } catch (Exception e) {
            log.error("Iyzico exception for order {}: {}", order.getId(), e.getMessage(), e);
            return IyzicoPaymentService.PaymentResult.failure(e.getMessage());
        }
    }

    public IyzicoPaymentService.PaymentResult chargeFallback(Order order, CardRequest card, Throwable ex) {
        log.error("[CB OPEN] Iyzico circuit breaker OPEN for order {} — {}", order.getId(), ex.getMessage());
        return IyzicoPaymentService.PaymentResult.failure("Payment service temporarily unavailable. Please try again later.");
    }

    private Options buildOptions() {
        Options options = new Options();
        options.setApiKey(apiKey);
        options.setSecretKey(secretKey);
        options.setBaseUrl(baseUrl);
        return options;
    }

    private CreatePaymentRequest buildPaymentRequest(Order order, CardRequest card) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setLocale(Locale.TR.getValue());
        request.setConversationId(UUID.randomUUID().toString());
        request.setPrice(order.getTotalAmount());
        request.setPaidPrice(order.getTotalAmount());
        request.setCurrency(Currency.TRY.name());
        request.setInstallment(1);
        request.setBasketId("ORDER-" + order.getId());
        request.setPaymentChannel(PaymentChannel.WEB.name());
        request.setPaymentGroup(PaymentGroup.PRODUCT.name());
        request.setPaymentCard(buildPaymentCard(card));
        request.setBuyer(buildBuyer(order));
        request.setShippingAddress(buildAddress(order));
        request.setBillingAddress(buildAddress(order));
        request.setBasketItems(buildBasketItems(order.getItems()));
        return request;
    }

    private PaymentCard buildPaymentCard(CardRequest card) {
        PaymentCard paymentCard = new PaymentCard();
        paymentCard.setCardHolderName(card.getHolderName());
        paymentCard.setCardNumber(card.getNumber());
        paymentCard.setExpireMonth(card.getExpireMonth());
        paymentCard.setExpireYear(card.getExpireYear());
        paymentCard.setCvc(card.getCvc());
        paymentCard.setRegisterCard(0);
        return paymentCard;
    }

    private Buyer buildBuyer(Order order) {
        Buyer buyer = new Buyer();
        buyer.setId(order.getUserId());
        buyer.setName(order.getShipFirstName());
        buyer.setSurname(order.getShipLastName());
        buyer.setGsmNumber(order.getShipPhone());
        buyer.setEmail(order.getShipEmail());
        buyer.setIdentityNumber(defaultIdentityNumber);
        buyer.setRegistrationAddress(order.getShipAddress());
        buyer.setIp(defaultBuyerIp);
        buyer.setCity(order.getShipCity());
        buyer.setCountry(order.getShipCountry());
        return buyer;
    }

    private Address buildAddress(Order order) {
        Address address = new Address();
        address.setContactName(order.getShipFirstName() + " " + order.getShipLastName());
        address.setCity(order.getShipCity());
        address.setCountry(order.getShipCountry());
        address.setAddress(order.getShipAddress());
        return address;
    }

    private List<BasketItem> buildBasketItems(List<OrderItem> orderItems) {
        return orderItems.stream().map(this::buildBasketItem).toList();
    }

    private BasketItem buildBasketItem(OrderItem item) {
        BasketItem bi = new BasketItem();
        bi.setId("PRODUCT-" + item.getProductId());
        bi.setName(item.getProductName());
        bi.setCategory1("General");
        bi.setItemType(BasketItemType.PHYSICAL.name());
        bi.setPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        return bi;
    }

    private IyzicoPaymentService.PaymentResult interpretPaymentResponse(Payment payment, Long orderId) {
        if ("success".equals(payment.getStatus())) {
            log.info("Iyzico SUCCESS — paymentId={}, orderId={}", payment.getPaymentId(), orderId);
            return IyzicoPaymentService.PaymentResult.success(payment.getPaymentId());
        } else {
            log.warn("Iyzico FAILED — code={}, msg={}", payment.getErrorCode(), payment.getErrorMessage());
            return IyzicoPaymentService.PaymentResult.failure(payment.getErrorMessage());
        }
    }
}
