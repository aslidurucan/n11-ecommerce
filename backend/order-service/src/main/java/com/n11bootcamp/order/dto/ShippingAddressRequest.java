package com.n11bootcamp.order.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShippingAddressRequest {

    @NotBlank(message = "Ad boş olamaz")
    private String firstName;

    @NotBlank(message = "Soyad boş olamaz")
    private String lastName;

    @Email(message = "Geçerli bir email adresi giriniz")
    @NotBlank(message = "Email boş olamaz")
    private String email;

    @NotBlank(message = "Telefon boş olamaz")
    private String phone;

    @NotBlank(message = "Adres boş olamaz")
    private String address;

    @NotBlank(message = "Şehir boş olamaz")
    private String city;

    @NotBlank(message = "Ülke boş olamaz")
    private String country;
}
