# Chubb APAC Claims Platform

A claims platform for claimants (report an incident, track it, answer questions, see decisions) and claims staff
(pick up work, assess, approve/reject/settle, see workload and liability exposure). One Angular app serves
three experiences (claimant, officer, manager); one Spring Boot service owns the domain.

- **Backend**: Java 17+, Spring Boot 3.3, JPA/H2, transactional outbox, pluggable in-process / Kafka event transport
- **Frontend**: Angular (standalone, signals, zoneless), role-scoped lazy routes
- Design rationale, trade-offs and next steps: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- AI working journal: [`docs/AI_JOURNAL.md`](docs/AI_JOURNAL.md)

## Verification status (please read)

| Part | Status |
|---|---|
| Angular app | `ng build` passes (production). Not exercised in a browser against the live backend. No frontend unit tests. |
| Domain model / state machine | Compiled and exercised with a throwaway harness mirroring `ClaimStateMachineTest`. |
| Spring Boot backend (controllers, JPA, outbox, tests) | **Written but not compiled or run.** The authoring sandbox could not reach Maven Central. Sources pass a `javac` parse (no syntax errors) but `mvn test` has never been run. Expect to spend a few minutes on first-run fixes. |

First thing to do on your machine: `cd backend && mvn test`.

## Run it

Prerequisites: JDK 17+, Maven 3.9+, Node 20+ (Docker only for the optional Kafka mode).

```bash
# 1. backend  (http://localhost:8080, Swagger UI at /swagger-ui.html)
cd backend
mvn spring-boot:run

# 2. frontend (http://localhost:4200, proxies /api -> :8080)
cd frontend
npm install
npm start
```

Tests: `cd backend && mvn test` (state-machine unit tests + end-to-end lifecycle tests through HTTP/H2/outbox).

### Demo personas (pick one on the login screen)

| Persona | Role | What to try |
|---|---|---|
| Alice Tan (SG), Bob Lim (MY), Chen Wei (HK) | Claimant | Report an incident, watch status and notifications, answer an info request |
| Priya / Lee (SG), Ahmad (MY), Wong (HK) | Officer | My desk, pick up from the queue, set reserve, request info, approve/reject/settle |
| Grace Ho | Manager | Exposure by currency/market/status, team workload, SLA breaches |

Suggested demo: sign in as **Alice**, submit a claim → sign in as **Priya**, pick it up, request information →
back as **Alice** (bell shows the notification), answer it → **Priya** approves → **Grace** sees exposure move.
Try approving above 50,000 as an officer to see the authority-limit escalation, and try picking up the same
claim from two officer sessions to see the 409.

### Kafka mode (optional)

```bash
docker compose up -d                              # single-node KRaft broker on :9092
cd backend && SPRING_PROFILES_ACTIVE=kafka mvn spring-boot:run
```

The default `inprocess` transport runs the same outbox → handler path without a broker.

## Layout

```
backend/   Spring Boot service (api, service, domain, repo, events, security, config)
frontend/  Angular app (core, shared, features/{login,claimant,staff,claim-detail})
docs/      ARCHITECTURE.md, AI_JOURNAL.md
```
