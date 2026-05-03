package com.n11bootcamp.user.service;

import com.n11bootcamp.user.dto.SignupRequest;
import com.n11bootcamp.user.dto.UpdateProfileRequest;
import com.n11bootcamp.user.exception.UserAlreadyExistsException;
import com.n11bootcamp.user.exception.UserNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAdminService implements IdentityProviderService {

    private final Keycloak keycloak;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Value("${keycloak.admin.default-role:USER}")
    private String defaultRole;

    @Override
    public String createUser(SignupRequest request) {
        UserRepresentation user = buildUserRepresentation(request);
        Response response = keycloak.realm(realm).users().create(user);

        if (response.getStatus() == Status.CONFLICT.getStatusCode()) {
            throw new UserAlreadyExistsException(request.email());
        }
        if (response.getStatus() != Status.CREATED.getStatusCode()) {
            log.error("Keycloak user creation failed: HTTP {}", response.getStatus());
            throw new IllegalStateException("Keycloak user creation failed: HTTP " + response.getStatus());
        }

        String userId = extractCreatedUserId(response);

        try {
            resetPassword(userId, request.password());
        } catch (Exception e) {
            log.error("Password set failed for userId={}. Rolling back.", userId, e);
            tryDeleteUser(userId);
            throw new IllegalStateException("Password assignment failed, user creation rolled back", e);
        }

        try {
            assignRole(userId, defaultRole);
        } catch (Exception e) {
            log.error("Role '{}' assignment failed for userId={}. Rolling back.", defaultRole, userId, e);
            tryDeleteUser(userId);
            throw new IllegalStateException("Role assignment failed, user creation rolled back", e);
        }

        log.info("Keycloak user created: userId={}, email={}", userId, request.email());
        return userId;
    }

    @Override
    public IdentityUser getUser(String userId) {
        try {
            UserRepresentation rep = keycloak.realm(realm).users().get(userId).toRepresentation();
            if (rep == null) {
                throw new UserNotFoundException(userId);
            }
            return new IdentityUser(rep.getId(), rep.getFirstName(), rep.getLastName(), rep.getEmail());
        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Keycloak getUser failed for userId={}: {}", userId, e.getMessage(), e);
            throw new IllegalStateException("Identity provider unavailable", e);
        }
    }

    @Override
    public void updateUser(String userId, UpdateProfileRequest request) {
        try {
            UserResource userResource = keycloak.realm(realm).users().get(userId);
            UserRepresentation user = userResource.toRepresentation();

            if (request.firstName() != null) user.setFirstName(request.firstName());
            if (request.lastName()  != null) user.setLastName(request.lastName());
            if (request.email()     != null) user.setEmail(request.email());

            userResource.update(user);
            log.info("Keycloak user updated: userId={}", userId);
        } catch (Exception e) {
            log.error("Keycloak updateUser failed for userId={}: {}", userId, e.getMessage(), e);
            throw new IllegalStateException("Identity provider unavailable", e);
        }
    }

    @Override
    public void deleteUser(String userId) {
        try {
            keycloak.realm(realm).users().delete(userId);
            log.info("Keycloak user deleted: userId={}", userId);
        } catch (Exception e) {
            log.error("Keycloak deleteUser failed for userId={}: {}", userId, e.getMessage(), e);
            throw new IllegalStateException("Failed to delete user from identity provider", e);
        }
    }


    private void tryDeleteUser(String userId) {
        try {
            keycloak.realm(realm).users().delete(userId);
            log.info("Keycloak rollback successful: user deleted userId={}", userId);
        } catch (Exception ex) {
            log.error("Keycloak rollback FAILED for userId={}. Manual cleanup required.", userId, ex);
        }
    }

    private UserRepresentation buildUserRepresentation(SignupRequest request) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.email());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(true);
        user.setEmailVerified(true);
        return user;
    }

    private void resetPassword(String userId, String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        keycloak.realm(realm).users().get(userId).resetPassword(credential);
        log.info("Password set via resetPassword for userId={}", userId);
    }

    private void assignRole(String userId, String roleName) {
        RoleRepresentation role = keycloak.realm(realm).roles().get(roleName).toRepresentation();
        keycloak.realm(realm).users().get(userId).roles().realmLevel().add(List.of(role));
    }

    private String extractCreatedUserId(Response response) {
        URI location = response.getLocation();
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
