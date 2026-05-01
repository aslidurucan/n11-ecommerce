package com.n11bootcamp.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CardRequest {

    @NotBlank(message = "Kart üzerindeki isim boş olamaz")
    private String holderName;

    @NotBlank(message = "Kart numarası boş olamaz")
    @Pattern(regexp = "\\d{15,19}", message = "Kart numarası 15-19 haneli olmalıdır")
    private String number;

    @NotBlank(message = "Son kullanma ayı boş olamaz")
    @Pattern(regexp = "\\d{2}", message = "Son kullanma ayı 2 haneli olmalıdır (01-12)")
    private String expireMonth;

    @NotBlank(message = "Son kullanma yılı boş olamaz")
    @Pattern(regexp = "\\d{4}", message = "Son kullanma yılı 4 haneli olmalıdır")
    private String expireYear;

    @NotBlank(message = "CVC boş olamaz")
    @Pattern(regexp = "\\d{3,4}", message = "CVC 3 veya 4 haneli olmalıdır")
    private String cvc;
}
