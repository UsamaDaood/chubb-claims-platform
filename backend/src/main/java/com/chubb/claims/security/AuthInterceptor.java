package com.chubb.claims.security;

import com.chubb.claims.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR = AuthUser.class.getName();

    private final AuthService auth;

    public AuthInterceptor(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw ApiException.unauthorized("Missing bearer token");
        }
        request.setAttribute(ATTR, auth.resolve(header.substring(7)));
        return true;
    }
}
