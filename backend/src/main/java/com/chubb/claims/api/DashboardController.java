package com.chubb.claims.api;

import com.chubb.claims.api.Dtos.*;
import com.chubb.claims.security.AuthUser;
import com.chubb.claims.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/exposure")
    public ExposureReport exposure(AuthUser actor) {
        return service.exposure(actor);
    }

    @GetMapping("/workload")
    public List<WorkloadRow> workload(AuthUser actor) {
        return service.workload(actor);
    }

    @GetMapping("/officer")
    public OfficerSummary officer(AuthUser actor) {
        return service.officerSummary(actor);
    }
}
