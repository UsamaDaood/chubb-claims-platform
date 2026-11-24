package com.chubb.claims.security;

import com.chubb.claims.common.ApiException;
import com.chubb.claims.domain.Market;
import com.chubb.claims.domain.Role;

/** The authenticated caller, resolved once per request and injected into controllers. */
public record AuthUser(String id, String name, Role role, Market market) {

    public void require(Role... allowed) {
        for (Role r : allowed) {
            if (r == role) {
                return;
            }
        }
        throw ApiException.forbidden("Role %s is not allowed to perform this action".formatted(role));
    }

    public boolean isStaff() {
        return role == Role.OFFICER || role == Role.MANAGER;
    }
}
