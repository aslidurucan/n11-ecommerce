package com.n11bootcamp.user.service;

import com.n11bootcamp.user.dto.SignupRequest;
import com.n11bootcamp.user.dto.UpdateProfileRequest;
import com.n11bootcamp.user.dto.UserProfileResponse;

public interface UserService {
    UserProfileResponse signup(SignupRequest request);
    UserProfileResponse getProfile(String userId);
    UserProfileResponse updateProfile(String userId, UpdateProfileRequest request);
}
