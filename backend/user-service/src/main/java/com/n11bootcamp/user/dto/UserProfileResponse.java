package com.n11bootcamp.user.dto;

import java.time.Instant;

public record UserProfileResponse(
    String keycloakId,
    String firstName,
    String lastName,
    String email,
    String phone,
    Instant createdAt
) {}
