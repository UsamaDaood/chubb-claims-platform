package com.chubb.claims.api;

import com.chubb.claims.api.Dtos.*;
import com.chubb.claims.repo.UserRepository;
import com.chubb.claims.security.AuthService;
import com.chubb.claims.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final UserRepository users;

    public AuthController(AuthService auth, UserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    /** Demo only: lists seeded users so the login screen can offer a picker. */
    @GetMapping("/users")
    public List<UserView> users() {
        return users.findAll().stream()
                .map(u -> new UserView(u.getId(), u.getName(), u.getRole(), u.getMarket())).toList();
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        String token = auth.login(req.userId());
        AuthUser u = auth.resolve(token);
        return new LoginResponse(token, new UserView(u.id(), u.name(), u.role(), u.market()));
    }

    @GetMapping("/me")
    public UserView me(AuthUser actor) {
        return new UserView(actor.id(), actor.name(), actor.role(), actor.market());
    }
}
