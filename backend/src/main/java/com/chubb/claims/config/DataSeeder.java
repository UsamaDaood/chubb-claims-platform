package com.chubb.claims.config;

import com.chubb.claims.api.Dtos.*;
import com.chubb.claims.domain.*;
import com.chubb.claims.repo.ClaimRepository;
import com.chubb.claims.repo.UserRepository;
import com.chubb.claims.security.AuthUser;
import com.chubb.claims.service.ClaimService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Demo data. Claims are created through the real service layer (so the audit trail, outbox events and
 * notifications are genuine) and then backdated so the dashboards have ageing / SLA breaches to show.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final ClaimRepository claimRepo;
    private final ClaimService service;
    private final Clock clock;

    public DataSeeder(UserRepository users, ClaimRepository claimRepo, ClaimService service, Clock clock) {
        this.users = users;
        this.claimRepo = claimRepo;
        this.service = service;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        users.save(new AppUser("c-alice", "Alice Tan", Role.CLAIMANT, Market.SG));
        users.save(new AppUser("c-bob", "Bob Lim", Role.CLAIMANT, Market.MY));
        users.save(new AppUser("c-chen", "Chen Wei", Role.CLAIMANT, Market.HK));
        users.save(new AppUser("o-priya", "Priya Nair (SG officer)", Role.OFFICER, Market.SG));
        users.save(new AppUser("o-lee", "Lee Jun (SG officer)", Role.OFFICER, Market.SG));
        users.save(new AppUser("o-ahmad", "Ahmad Razak (MY officer)", Role.OFFICER, Market.MY));
        users.save(new AppUser("o-wong", "Wong Mei (HK officer)", Role.OFFICER, Market.HK));
        users.save(new AppUser("m-grace", "Grace Ho (Claims manager)", Role.MANAGER, null));

        AuthUser alice = user("c-alice"), bob = user("c-bob"), chen = user("c-chen");
        AuthUser priya = user("o-priya"), lee = user("o-lee"), ahmad = user("o-ahmad");

        // SG: unassigned and past SLA (shows up red in the queue)
        backdate(submit(alice, ClaimType.MOTOR, "Rear-ended at a traffic light on PIE; bumper and boot damaged.", "12000"), 7);
        // SG: in review with reserve set
        long c2 = submit(alice, ClaimType.PROPERTY, "Burst pipe flooded the living room and damaged flooring.", "85000");
        service.assign(priya, c2);
        service.assess(priya, c2, new AssessmentRequest(new BigDecimal("80000"), "Loss adjuster estimate received"));
        backdate(c2, 3);
        // SG: waiting on the claimant
        long c3 = submit(alice, ClaimType.MOTOR, "Windscreen cracked by road debris.", "3500");
        service.assign(priya, c3);
        service.requestInfo(priya, c3, "Please provide the workshop quotation and a photo of the damage.");
        backdate(c3, 2);
        // SG: approved, awaiting payment
        long c4 = submit(alice, ClaimType.MOTOR, "Side-swiped while parked; door panel dented.", "6200");
        service.assign(lee, c4);
        service.approve(lee, c4, new ApproveRequest(new BigDecimal("5800"), "Within policy limits"));
        backdate(c4, 4);
        // SG: rejected
        long c5 = submit(alice, ClaimType.PROPERTY, "Water stain on ceiling.", "1500");
        service.assign(lee, c5);
        service.reject(lee, c5, "Gradual deterioration is excluded under the policy.");
        backdate(c5, 6);
        // SG: fresh
        submit(alice, ClaimType.MOTOR, "Scratched bumper in a car park.", "900");

        // MY
        backdate(submit(bob, ClaimType.MOTOR, "Collision at a junction in Petaling Jaya.", "9000"), 1);
        long c8 = submit(bob, ClaimType.PROPERTY, "Fire damaged the kitchen.", "40000");
        service.assign(ahmad, c8);
        backdate(c8, 6);   // in review and already past SLA

        // HK: market with a single officer, one claim waiting
        submit(chen, ClaimType.MOTOR, "Minor collision in Kowloon.", "4200");
    }

    private AuthUser user(String id) {
        AppUser u = users.findById(id).orElseThrow();
        return new AuthUser(u.getId(), u.getName(), u.getRole(), u.getMarket());
    }

    private long submit(AuthUser claimant, ClaimType type, String description, String amount) {
        return service.submit(claimant, new SubmitClaimRequest(type, description,
                LocalDate.now(clock).minusDays(1), "Singapore", new BigDecimal(amount)), null).claim().id();
    }

    private long backdate(long claimId, int days) {
        Claim c = claimRepo.findById(claimId).orElseThrow();
        c.backdate(clock.instant().minus(days, ChronoUnit.DAYS));
        claimRepo.save(c);
        return claimId;
    }
}
