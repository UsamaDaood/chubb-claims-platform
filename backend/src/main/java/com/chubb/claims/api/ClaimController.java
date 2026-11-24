package com.chubb.claims.api;

import com.chubb.claims.api.Dtos.*;
import com.chubb.claims.domain.ClaimType;
import com.chubb.claims.security.AuthUser;
import com.chubb.claims.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService service;

    public ClaimController(ClaimService service) {
        this.service = service;
    }

    // ---- claimant
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimDetail submit(AuthUser actor, @Valid @RequestBody SubmitClaimRequest req,
                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return service.submit(actor, req, idempotencyKey);
    }

    @GetMapping("/mine")
    public List<ClaimSummary> mine(AuthUser actor) {
        return service.mine(actor);
    }

    @PostMapping("/{id}/info-requests/{requestId}/response")
    public ClaimDetail respond(AuthUser actor, @PathVariable Long id, @PathVariable Long requestId,
                               @Valid @RequestBody InfoResponse body) {
        return service.provideInfo(actor, id, requestId, body.answer());
    }

    // ---- staff
    @GetMapping("/queue")
    public List<ClaimSummary> queue(AuthUser actor, @RequestParam(required = false) ClaimType type) {
        return service.queue(actor, type);
    }

    @GetMapping("/assigned")
    public List<ClaimSummary> assigned(AuthUser actor) {
        return service.assigned(actor);
    }

    @PostMapping("/{id}/assign")
    public ClaimDetail assign(AuthUser actor, @PathVariable Long id) {
        return service.assign(actor, id);
    }

    @PostMapping("/{id}/assessment")
    public ClaimDetail assess(AuthUser actor, @PathVariable Long id, @Valid @RequestBody AssessmentRequest body) {
        return service.assess(actor, id, body);
    }

    @PostMapping("/{id}/info-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimDetail requestInfo(AuthUser actor, @PathVariable Long id, @Valid @RequestBody InfoRequestCreate body) {
        return service.requestInfo(actor, id, body.question());
    }

    @PostMapping("/{id}/approve")
    public ClaimDetail approve(AuthUser actor, @PathVariable Long id, @Valid @RequestBody ApproveRequest body) {
        return service.approve(actor, id, body);
    }

    @PostMapping("/{id}/reject")
    public ClaimDetail reject(AuthUser actor, @PathVariable Long id, @Valid @RequestBody RejectRequest body) {
        return service.reject(actor, id, body.reason());
    }

    @PostMapping("/{id}/settle")
    public ClaimDetail settle(AuthUser actor, @PathVariable Long id, @RequestBody(required = false) SettleRequest body) {
        return service.settle(actor, id, body == null ? null : body.paymentReference());
    }

    // ---- shared
    @GetMapping("/{id}")
    public ClaimDetail get(AuthUser actor, @PathVariable Long id) {
        return service.get(actor, id);
    }
}
