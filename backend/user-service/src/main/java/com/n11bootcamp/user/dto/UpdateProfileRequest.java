package com.n11bootcamp.user.dto;

import jakarta.validation.constraints.Email;

public record UpdateProfileRequest(
    String firstName,
    String lastName,

    @Email(message = "Geçerli bir email adresi giriniz")
    String email,

    String phone
) {}
