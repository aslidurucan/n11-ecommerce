package com.n11bootcamp.user.service;

import com.n11bootcamp.user.dto.SignupRequest;
import com.n11bootcamp.user.dto.UpdateProfileRequest;
import com.n11bootcamp.user.dto.UserProfileResponse;
import com.n11bootcamp.user.entity.UserProfile;
import com.n11bootcamp.user.exception.UserNotFoundException;
import com.n11bootcamp.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final IdentityProviderService identityProvider;
    private final UserProfileRepository profileRepository;

    @Override
    @Transactional
    public UserProfileResponse signup(SignupRequest request) {
        String userId = identityProvider.createUser(request);

        UserProfile savedProfile;
        try {
            savedProfile = profileRepository.save(
                    UserProfile.builder()
                            .keycloakId(userId)
                            .phone(request.phone())
                            .build()
            );
        } catch (Exception e) {
            log.error("DB save failed after Keycloak create for userId={}. Compensating.", userId, e);
            tryDeleteIdentityUser(userId);
            throw new IllegalStateException("Registration failed", e);
        }

        log.info("User registered: userId={}", userId);
        return new UserProfileResponse(
                userId,
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phone(),
                savedProfile.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String userId) {
        IdentityUser idUser = identityProvider.getUser(userId);

        UserProfile profile = profileRepository.findById(userId).orElse(null);
        String phone     = profile != null ? profile.getPhone()     : null;
        var    createdAt = profile != null ? profile.getCreatedAt() : null;

        return new UserProfileResponse(
                userId, idUser.firstName(), idUser.lastName(), idUser.email(), phone, createdAt);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        IdentityUser existing = identityProvider.getUser(userId);

        UserProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        identityProvider.updateUser(userId, request);

        try {
            if (request.phone() != null) {
                profile.setPhone(request.phone());
                profileRepository.save(profile);
            }
        } catch (Exception e) {
            log.error("DB update failed for userId={}. Compensating Keycloak update.", userId, e);
            tryRollbackIdentityUser(userId, existing);
            throw new IllegalStateException("Profile update failed", e);
        }

        log.info("Profile updated: userId={}", userId);
        return new UserProfileResponse(
                userId,
                coalesce(request.firstName(), existing.firstName()),
                coalesce(request.lastName(),  existing.lastName()),
                coalesce(request.email(),     existing.email()),
                profile.getPhone(),
                profile.getCreatedAt()
        );
    }


    private static String coalesce(String requestValue, String existingValue) {
        return requestValue != null ? requestValue : existingValue;
    }

    private void tryDeleteIdentityUser(String userId) {
        try {
            identityProvider.deleteUser(userId);
            log.info("Compensation successful: Keycloak user deleted userId={}", userId);
        } catch (Exception ex) {
            log.error("Compensation FAILED for userId={}. Manual cleanup required.", userId);
        }
    }

    private void tryRollbackIdentityUser(String userId, IdentityUser previousState) {
        try {
            UpdateProfileRequest rollbackRequest = new UpdateProfileRequest(
                    previousState.firstName(),
                    previousState.lastName(),
                    previousState.email(),
                    null
            );
            identityProvider.updateUser(userId, rollbackRequest);
            log.info("Compensation successful: Keycloak rolled back for userId={}", userId);
        } catch (Exception ex) {
            log.error("Compensation FAILED for userId={}. Manual cleanup required. " +
                            "Previous state: firstName={}, lastName={}, email={}",
                    userId, previousState.firstName(), previousState.lastName(), previousState.email());
        }
    }
}