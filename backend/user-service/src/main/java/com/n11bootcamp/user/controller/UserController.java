package com.n11bootcamp.user.controller;

import com.n11bootcamp.user.dto.SignupRequest;
import com.n11bootcamp.user.dto.UpdateProfileRequest;
import com.n11bootcamp.user.dto.UserProfileResponse;
import com.n11bootcamp.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Users", description = "Kullanıcı kayıt ve profil yönetimi")
public class UserController {

    private final UserService userService;

    @PostMapping("/api/users/signup")
    @Operation(summary = "Yeni kullanıcı kaydı — herkese açık")
    public ResponseEntity<UserProfileResponse> signup(
            @RequestBody @Valid SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.signup(request));
    }

    @GetMapping("/api/users/me")
    @Operation(summary = "Kendi profilimi getir — token gerekli")
    public ResponseEntity<UserProfileResponse> getProfile(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getProfile(jwt.getSubject()));
    }

    @PutMapping("/api/users/me")
    @Operation(summary = "Kendi profilimi güncelle — token gerekli")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(jwt.getSubject(), request));
    }
}
