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
    public UserProfileResponse signup(SignupRequest request) {
        String userId = identityProvider.createUser(request);

        UserProfile savedProfile;
        try {
            savedProfile = saveUserProfile(userId, request.phone());
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

    @Transactional
    protected UserProfile saveUserProfile(String userId, String phone) {
        return profileRepository.save(
                UserProfile.builder()
                        .keycloakId(userId)
                        .phone(phone)
                        .build()
        );
    }

    @Override
    public UserProfileResponse getProfile(String userId) {
        IdentityUser idUser = identityProvider.getUser(userId);

        UserProfile profile = profileRepository.findById(userId).orElse(null);
        String phone     = profile != null ? profile.getPhone()     : null;
        var    createdAt = profile != null ? profile.getCreatedAt() : null;

        return new UserProfileResponse(
                userId, idUser.firstName(), idUser.lastName(), idUser.email(), phone, createdAt);
    }

    @Override
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        IdentityUser existing = identityProvider.getUser(userId);

        if (!profileRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        identityProvider.updateUser(userId, request);

        UserProfile profile;
        try {
            profile = persistPhoneUpdate(userId, request.phone());
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

    @Transactional
    protected UserProfile persistPhoneUpdate(String userId, String newPhone) {
        UserProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (newPhone != null) {
            profile.setPhone(newPhone);
            profileRepository.save(profile);
        }
        return profile;
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
