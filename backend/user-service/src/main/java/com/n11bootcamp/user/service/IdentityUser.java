package com.n11bootcamp.user.service;


public record IdentityUser(
    String id,
    String firstName,
    String lastName,
    String email
) {}
