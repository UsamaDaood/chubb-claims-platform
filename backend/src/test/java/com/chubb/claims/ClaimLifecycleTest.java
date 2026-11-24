package com.chubb.claims;

import com.chubb.claims.events.OutboxRelay;
import com.chubb.claims.repo.NotificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** End-to-end through HTTP, the real service layer, H2 and the in-process outbox relay. */
@SpringBootTest
@AutoConfigureMockMvc
class ClaimLifecycleTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OutboxRelay relay;
    @Autowired NotificationRepository notifications;

    private String login(String userId) throws Exception {
        return send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + userId + "\"}"), null, 200).get("token").asText();
    }

    private JsonNode send(MockHttpServletRequestBuilder req, String token, int expected) throws Exception {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        var res = mvc.perform(req).andReturn().getResponse();
        assertEquals(expected, res.getStatus(), "unexpected status; body=" + res.getContentAsString());
        String body = res.getContentAsString();
        return body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    private JsonNode postJson(String url, String token, String body, int expected) throws Exception {
        return send(post(url).contentType(MediaType.APPLICATION_JSON).content(body), token, expected);
    }

    private long submitClaim(String claimantToken, String amount) throws Exception {
        String body = """
                {"type":"MOTOR","description":"Bumper damage","incidentDate":"%s","incidentLocation":"Orchard Rd","estimatedAmount":%s}
                """.formatted(java.time.LocalDate.now().minusDays(1), amount);
        return postJson("/api/claims", claimantToken, body, 201).get("claim").get("id").asLong();
    }

    @Test
    void fullLifecycleWithInfoRequestAndNotifications() throws Exception {
        String alice = login("c-alice"), priya = login("o-priya");
        long id = submitClaim(alice, "5000");

        JsonNode assigned = postJson("/api/claims/" + id + "/assign", priya, "{}", 200);
        assertEquals("IN_REVIEW", assigned.get("claim").get("status").asText());

        postJson("/api/claims/" + id + "/assessment", priya, "{\"reserveAmount\":4500,\"notes\":\"internal\"}", 200);
        JsonNode asked = postJson("/api/claims/" + id + "/info-requests", priya, "{\"question\":\"Photo please\"}", 201);
        assertEquals("INFO_REQUESTED", asked.get("claim").get("status").asText());
        long requestId = asked.get("infoRequests").get(0).get("id").asLong();

        // Claimant sees the open request, but never the internal reserve or internal activity.
        JsonNode asClaimant = send(get("/api/claims/" + id), alice, 200);
        assertEquals("PROVIDE_INFO", asClaimant.get("allowedActions").get(0).asText());
        assertTrue(asClaimant.get("claim").get("reserveAmount").isNull());
        asClaimant.get("activity").forEach(a -> assertFalse(a.get("internal").asBoolean()));

        JsonNode answered = postJson("/api/claims/" + id + "/info-requests/" + requestId + "/response", alice,
                "{\"answer\":\"Attached\"}", 200);
        assertEquals("IN_REVIEW", answered.get("claim").get("status").asText());

        postJson("/api/claims/" + id + "/approve", priya, "{\"approvedAmount\":4800,\"reason\":\"ok\"}", 200);
        JsonNode settled = postJson("/api/claims/" + id + "/settle", priya, "{\"paymentReference\":\"PAY-1\"}", 200);
        assertEquals("SETTLED", settled.get("claim").get("status").asText());

        relay.drain();   // deliver outbox -> notification consumer (normally runs on a 500ms schedule)
        long forClaim = notifications.findTop50ByUserIdOrderByCreatedAtDesc("c-alice").stream()
                .filter(n -> n.getClaimId() != null && n.getClaimId() == id).count();
        assertTrue(forClaim >= 4, "claimant should have submitted/assigned/info/approved/settled notifications");
    }

    @Test
    void secondOfficerCannotTakeAnAlreadyAssignedClaim() throws Exception {
        String alice = login("c-alice"), priya = login("o-priya"), lee = login("o-lee");
        long id = submitClaim(alice, "1000");
        postJson("/api/claims/" + id + "/assign", priya, "{}", 200);
        postJson("/api/claims/" + id + "/assign", lee, "{}", 409);
    }

    @Test
    void officerAuthorityLimitForcesEscalationToManager() throws Exception {
        String alice = login("c-alice"), priya = login("o-priya"), grace = login("m-grace");
        long id = submitClaim(alice, "90000");
        postJson("/api/claims/" + id + "/assign", priya, "{}", 200);
        postJson("/api/claims/" + id + "/approve", priya, "{\"approvedAmount\":60000}", 403);
        JsonNode ok = postJson("/api/claims/" + id + "/approve", grace, "{\"approvedAmount\":60000}", 200);
        assertEquals("APPROVED", ok.get("claim").get("status").asText());
    }

    @Test
    void claimantsCannotSeeEachOthersClaims() throws Exception {
        String alice = login("c-alice"), bob = login("c-bob");
        long id = submitClaim(alice, "800");
        send(get("/api/claims/" + id), bob, 404);
    }

    @Test
    void officersOnlyWorkTheirOwnMarket() throws Exception {
        String alice = login("c-alice"), ahmad = login("o-ahmad");   // ahmad is MY, alice's claim is SG
        long id = submitClaim(alice, "800");
        postJson("/api/claims/" + id + "/assign", ahmad, "{}", 403);
    }

    @Test
    void submissionIsIdempotent() throws Exception {
        String alice = login("c-alice");
        String body = """
                {"type":"MOTOR","description":"Once only","incidentDate":"%s","estimatedAmount":100}
                """.formatted(java.time.LocalDate.now());
        var first = send(post("/api/claims").contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "k-123").content(body), alice, 201);
        var second = send(post("/api/claims").contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "k-123").content(body), alice, 201);
        assertEquals(first.get("claim").get("id").asLong(), second.get("claim").get("id").asLong());
    }

    @Test
    void exposureOnlyForManagersAndTracksOpenClaims() throws Exception {
        String alice = login("c-alice"), grace = login("m-grace");
        send(get("/api/dashboard/exposure"), alice, 403);
        JsonNode before = send(get("/api/dashboard/exposure"), grace, 200);
        long id = submitClaim(alice, "2500");
        JsonNode after = send(get("/api/dashboard/exposure"), grace, 200);
        assertEquals(2500.0, sgd(after) - sgd(before), 0.001);

        String priya = login("o-priya");
        postJson("/api/claims/" + id + "/assign", priya, "{}", 200);
        postJson("/api/claims/" + id + "/reject", priya, "{\"reason\":\"excluded\"}", 200);
        assertEquals(sgd(before), sgd(send(get("/api/dashboard/exposure"), grace, 200)), 0.001);
    }

    private double sgd(JsonNode report) {
        for (JsonNode t : report.get("totals")) {
            if ("SGD".equals(t.get("currency").asText())) {
                return t.get("amount").asDouble();
            }
        }
        return 0;
    }
}
