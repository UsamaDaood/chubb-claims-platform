package com.chubb.claims.security;

import com.chubb.claims.common.ApiException;
import com.chubb.claims.domain.AppUser;
import com.chubb.claims.repo.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DEMO AUTH ONLY. Pick-a-user login with opaque in-memory tokens. This stands in for the
 * corporate IdP (OIDC/JWT); the rest of the system only depends on the {@link AuthUser} it yields.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final Map<String, String> tokenToUserId = new ConcurrentHashMap<>();

    public AuthService(UserRepository users) {
        this.users = users;
    }

    public String login(String userId) {
        AppUser u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Unknown user"));
        String token = UUID.randomUUID().toString();
        tokenToUserId.put(token, u.getId());
        return token;
    }

    public AuthUser resolve(String token) {
        String userId = tokenToUserId.get(token);
        if (userId == null) {
            throw ApiException.unauthorized("Invalid or expired token");
        }
        return users.findById(userId)
                .map(u -> new AuthUser(u.getId(), u.getName(), u.getRole(), u.getMarket()))
                .orElseThrow(() -> ApiException.unauthorized("Unknown user"));
    }
}
