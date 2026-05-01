package com.n11bootcamp.user.service;

import com.n11bootcamp.user.dto.SignupRequest;
import com.n11bootcamp.user.dto.UpdateProfileRequest;
import com.n11bootcamp.user.dto.UserProfileResponse;
import com.n11bootcamp.user.entity.UserProfile;
import com.n11bootcamp.user.exception.UserNotFoundException;
import com.n11bootcamp.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private IdentityProviderService identityProvider;

    @Mock
    private UserProfileRepository profileRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void signup_whenSuccess_createsKeycloakUserAndReturnsResponse() {
        SignupRequest request = buildSignupRequest("ali@test.com", "Ali", "Yılmaz", "05551234567");
        UserProfile savedProfile = buildProfile("kc-uuid-123", "05551234567");

        when(identityProvider.createUser(request)).thenReturn("kc-uuid-123");
        when(profileRepository.save(any(UserProfile.class))).thenReturn(savedProfile);

        UserProfileResponse result = userService.signup(request);

        assertThat(result.keycloakId()).isEqualTo("kc-uuid-123");
        assertThat(result.email()).isEqualTo("ali@test.com");
        assertThat(result.firstName()).isEqualTo("Ali");
        assertThat(result.phone()).isEqualTo("05551234567");
        verify(identityProvider).createUser(request);
        verify(profileRepository).save(any(UserProfile.class));
    }

    @Test
    void signup_whenDbFails_compensatesAndThrowsIllegalStateException() {
        SignupRequest request = buildSignupRequest("ali@test.com", "Ali", "Yılmaz", null);

        when(identityProvider.createUser(request)).thenReturn("kc-uuid-123");
        when(profileRepository.save(any(UserProfile.class)))
                .thenThrow(new RuntimeException("DB bağlantı hatası"));

        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Registration failed");

        verify(identityProvider).deleteUser("kc-uuid-123");
    }

    @Test
    void signup_whenDbFailsAndCompensationAlsoFails_stillThrowsIllegalStateException() {
        SignupRequest request = buildSignupRequest("ali@test.com", "Ali", "Yılmaz", null);

        when(identityProvider.createUser(request)).thenReturn("kc-uuid-123");
        when(profileRepository.save(any(UserProfile.class)))
                .thenThrow(new RuntimeException("DB hatası"));
        doThrow(new RuntimeException("Keycloak silinemedi"))
                .when(identityProvider).deleteUser("kc-uuid-123");

        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getProfile_whenDbProfileExists_returnsFullProfileWithPhone() {
        IdentityUser idUser = new IdentityUser("kc-uuid-123", "Ali", "Yılmaz", "ali@test.com");
        UserProfile profile = buildProfile("kc-uuid-123", "05551234567");

        when(identityProvider.getUser("kc-uuid-123")).thenReturn(idUser);
        when(profileRepository.findById("kc-uuid-123")).thenReturn(Optional.of(profile));

        UserProfileResponse result = userService.getProfile("kc-uuid-123");

        assertThat(result.firstName()).isEqualTo("Ali");
        assertThat(result.email()).isEqualTo("ali@test.com");
        assertThat(result.phone()).isEqualTo("05551234567");
        assertThat(result.createdAt()).isNotNull();
    }

    @Test
    void getProfile_whenNoDbProfile_returnsResponseWithNullPhone() {
        IdentityUser idUser = new IdentityUser("kc-uuid-123", "Ali", "Yılmaz", "ali@test.com");

        when(identityProvider.getUser("kc-uuid-123")).thenReturn(idUser);
        when(profileRepository.findById("kc-uuid-123")).thenReturn(Optional.empty());

        UserProfileResponse result = userService.getProfile("kc-uuid-123");

        assertThat(result.keycloakId()).isEqualTo("kc-uuid-123");
        assertThat(result.phone()).isNull();
        assertThat(result.createdAt()).isNull();
    }

    @Test
    void updateProfile_whenSuccess_returnsUpdatedProfile() {
        IdentityUser existing = new IdentityUser("kc-uuid-123", "Ali", "Yılmaz", "ali@test.com");
        UpdateProfileRequest request = new UpdateProfileRequest("Mehmet", null, null, "05557654321");
        UserProfile profile = buildProfile("kc-uuid-123", "05551234567");

        when(identityProvider.getUser("kc-uuid-123")).thenReturn(existing);
        when(profileRepository.findById("kc-uuid-123")).thenReturn(Optional.of(profile));
        when(profileRepository.save(profile)).thenReturn(profile);

        UserProfileResponse result = userService.updateProfile("kc-uuid-123", request);

        assertThat(result.firstName()).isEqualTo("Mehmet");
        assertThat(result.lastName()).isEqualTo("Yılmaz");
        assertThat(result.email()).isEqualTo("ali@test.com");
        assertThat(result.phone()).isEqualTo("05557654321");
        verify(identityProvider).updateUser("kc-uuid-123", request);
        verify(profileRepository).save(profile);
    }

    @Test
    void updateProfile_whenPhoneIsNull_doesNotSaveProfile() {
        IdentityUser existing = new IdentityUser("kc-uuid-123", "Ali", "Yılmaz", "ali@test.com");
        UpdateProfileRequest request = new UpdateProfileRequest("Mehmet", null, null, null);
        UserProfile profile = buildProfile("kc-uuid-123", "05551234567");

        when(identityProvider.getUser("kc-uuid-123")).thenReturn(existing);
        when(profileRepository.findById("kc-uuid-123")).thenReturn(Optional.of(profile));

        userService.updateProfile("kc-uuid-123", request);

        verify(profileRepository, never()).save(any());
    }

    @Test
    void updateProfile_whenProfileNotInDb_throwsUserNotFoundException() {
        IdentityUser existing = new IdentityUser("kc-uuid-123", "Ali", "Yılmaz", "ali@test.com");
        UpdateProfileRequest request = new UpdateProfileRequest("Mehmet", null, null, null);

        when(identityProvider.getUser("kc-uuid-123")).thenReturn(existing);
        when(profileRepository.findById("kc-uuid-123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile("kc-uuid-123", request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("kc-uuid-123");
    }

    @Test
    void updateProfile_whenAllFieldsNull_keepsExistingValues() {
        IdentityUser existing = new IdentityUser("kc-uuid-123", "Ali", "Yılmaz", "ali@test.com");
        UpdateProfileRequest request = new UpdateProfileRequest(null, null, null, null);
        UserProfile profile = buildProfile("kc-uuid-123", "05551234567");

        when(identityProvider.getUser("kc-uuid-123")).thenReturn(existing);
        when(profileRepository.findById("kc-uuid-123")).thenReturn(Optional.of(profile));

        UserProfileResponse result = userService.updateProfile("kc-uuid-123", request);

        assertThat(result.firstName()).isEqualTo("Ali");
        assertThat(result.lastName()).isEqualTo("Yılmaz");
        assertThat(result.email()).isEqualTo("ali@test.com");
    }

    private SignupRequest buildSignupRequest(String email, String firstName, String lastName, String phone) {
        return new SignupRequest(firstName, lastName, email, "Sifre1234!", phone);
    }

    private UserProfile buildProfile(String keycloakId, String phone) {
        return UserProfile.builder()
                .keycloakId(keycloakId)
                .phone(phone)
                .createdAt(Instant.now())
                .build();
    }
}
