package com.n11bootcamp.notification.service;

import com.n11bootcamp.notification.event.OrderCancelledEvent;
import com.n11bootcamp.notification.event.OrderCompletedEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Thymeleaf-based HTML mail service.
 *
 * <p><b>Hata yönetimi:</b> sendHtml() metodu hata durumunda exception fırlatır
 * (önceki implementation swallow ediyordu — mail kaybı bug'ı). Caller (RabbitListener)
 * exception görünce mesajı NACK edip DLQ'ya gönderir → Spring AMQP retry mekanizması
 * devreye girer.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThymeleafMailService implements MailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${notification.mail.from}")
    private String fromAddress;

    @Override
    public void sendOrderCompleted(OrderCompletedEvent event) {
        Context ctx = new Context(Locale.forLanguageTag("tr"));
        ctx.setVariable("orderId", event.orderId());
        ctx.setVariable("totalAmount", event.totalAmount());
        ctx.setVariable("occurredAt", event.occurredAt());

        sendHtml(event.userEmail(), "Siparişiniz Onaylandı #" + event.orderId(),
            "emails/order-completed", ctx);
    }

    @Override
    public void sendOrderCancelled(OrderCancelledEvent event) {
        Context ctx = new Context(Locale.forLanguageTag("tr"));
        ctx.setVariable("orderId", event.orderId());
        ctx.setVariable("reason", event.reason());
        ctx.setVariable("occurredAt", event.occurredAt());

        sendHtml(event.userEmail(), "Siparişiniz İptal Edildi #" + event.orderId(),
            "emails/order-cancelled", ctx);
    }

    /**
     * HTML mail gönderir. Hata durumunda RuntimeException fırlatır
     * — RabbitListener bunu görüp DLQ'ya gönderir.
     *
     * @throws IllegalStateException mail gönderme başarısız (SMTP, template, vs.)
     */
    private void sendHtml(String to, String subject, String template, Context ctx) {
        try {
            String html = templateEngine.process(template, ctx);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Mail sent: to={}, subject={}", to, subject);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send mail to {}: {}", to, e.getMessage(), e);
            throw new IllegalStateException(
                "Failed to send notification email to " + to + ": " + e.getMessage(), e);
        }
    }
}
