package com.chubb.claims.api;

import com.chubb.claims.api.Dtos.NotificationView;
import com.chubb.claims.security.AuthUser;
import com.chubb.claims.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationView> list(AuthUser actor) {
        return service.list(actor);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(AuthUser actor, @PathVariable Long id) {
        service.markRead(actor, id);
    }
}
