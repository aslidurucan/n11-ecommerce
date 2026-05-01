package com.n11bootcamp.user.service;

import com.n11bootcamp.user.dto.SignupRequest;
import com.n11bootcamp.user.dto.UpdateProfileRequest;

public interface IdentityProviderService {
    String createUser(SignupRequest request);
    IdentityUser getUser(String userId);
    void updateUser(String userId, UpdateProfileRequest request);
    void deleteUser(String userId);
}
