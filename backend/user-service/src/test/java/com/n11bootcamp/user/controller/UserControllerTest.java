package com.n11bootcamp.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11bootcamp.user.dto.SignupRequest;
import com.n11bootcamp.user.dto.UpdateProfileRequest;
import com.n11bootcamp.user.dto.UserProfileResponse;
import com.n11bootcamp.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.n11bootcamp.user.config.SecurityConfig;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void signup_whenValidRequest_returns201WithBody() throws Exception {
        SignupRequest request = new SignupRequest("Ali", "Yılmaz", "ali@test.com", "Sifre1234!", "05551234567");
        UserProfileResponse response = buildResponse("kc-uuid-123", "Ali", "Yılmaz", "ali@test.com");

        when(userService.signup(any())).thenReturn(response);

        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keycloakId").value("kc-uuid-123"))
                .andExpect(jsonPath("$.firstName").value("Ali"))
                .andExpect(jsonPath("$.email").value("ali@test.com"));
    }

    @Test
    void signup_whenEmailMissing_returns400() throws Exception {
        SignupRequest request = new SignupRequest("Ali", "Yılmaz", null, "Sifre1234!", null);

        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_whenEmailInvalid_returns400() throws Exception {
        SignupRequest request = new SignupRequest("Ali", "Yılmaz", "gecersiz-email", "Sifre1234!", null);

        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_whenPasswordTooShort_returns400() throws Exception {
        SignupRequest request = new SignupRequest("Ali", "Yılmaz", "ali@test.com", "kisa", null);

        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProfile_whenAuthenticated_returns200WithProfile() throws Exception {
        UserProfileResponse response = buildResponse("kc-uuid-123", "Ali", "Yılmaz", "ali@test.com");
        when(userService.getProfile("kc-uuid-123")).thenReturn(response);

        mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(j -> j.subject("kc-uuid-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keycloakId").value("kc-uuid-123"))
                .andExpect(jsonPath("$.firstName").value("Ali"));
    }

    @Test
    void getProfile_whenNotAuthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_whenAuthenticated_returns200() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Mehmet", null, null, "05557654321");
        UserProfileResponse response = buildResponse("kc-uuid-123", "Mehmet", "Yılmaz", "ali@test.com");

        when(userService.updateProfile(eq("kc-uuid-123"), any())).thenReturn(response);

        mockMvc.perform(patch("/api/users/me")
                        .with(jwt().jwt(j -> j.subject("kc-uuid-123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Mehmet"));
    }

    @Test
    void updateProfile_whenNotAuthenticated_returns401() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Mehmet", null, null, null);

        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    private UserProfileResponse buildResponse(String id, String firstName, String lastName, String email) {
        return new UserProfileResponse(id, firstName, lastName, email, "05551234567", Instant.now());
    }
}