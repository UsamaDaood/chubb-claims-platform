# Architecture and decisions

## 1. Core entities

```
AppUser (claimant | officer | manager, home market)
Claim  ──< ClaimActivity   (append-only audit trail / timeline; internal entries hidden from claimants)
       ──< InfoRequest     (officer's question -> claimant's answer)
OutboxMessage              (events waiting to be delivered)
Notification               (read model built by the event consumer)
```

`Claim` is the aggregate root and the only thing that mutates lifecycle state. Lifecycle (single source of truth:
`ClaimStatus.canMoveTo`):

```
SUBMITTED -> IN_REVIEW -> APPROVED -> SETTLED
                |  ^
                v  |
          INFO_REQUESTED
IN_REVIEW -> REJECTED
```

Money: each of the six markets settles in its own currency (`Market.currency()`). Amounts are `BigDecimal`
with scale 2; **exposure is never summed across currencies** (no FX source was assumed).

## 2. One service or many?

**One deployable (modular monolith), with seams.** Reasoning:

- Every use case reads or writes the same aggregate and must atomically update state, audit trail and outbox.
  Splitting claims/workflow/audit across services would turn a single local transaction into distributed
  coordination for no benefit at this scale and in a 2-3 hour sprint.
- Packages (`api`, `service`, `domain`, `repo`, `events`, `security`) keep boundaries explicit.
- The seam I would cut first is already visible: **notifications** consume events only and know nothing about the
  claims DB beyond the event payload. Next would be a **reporting/exposure projection** fed from the same topic.
  Both can be extracted without touching the write path because the contract is the event, not a shared table.

## 3. Synchronous vs event-driven

| Concern                                                                   | Mechanism                                          | Why                                                                                                                                                                                              |
| ------------------------------------------------------------------------- | -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Submit, pick up, assess, request info, answer, approve, reject, settle    | **REST** (command, immediate response)             | The user is waiting; needs validation errors, authorisation outcomes and the new state back in the same round trip. A command over Kafka has no natural reply.                                   |
| Lists, detail, dashboards                                                 | **REST** (query)                                   | Request/response reads.                                                                                                                                                                          |
| "Something happened" (submitted, assigned, info requested, approved, ...) | **Kafka topic `claims.events`**, keyed by claim id | Facts that other consumers react to later and independently. Key = claim id keeps one claim's events ordered on one partition; consumers can be added or replayed without changing the producer. |

**Async processing path** (the "how do you handle async" answer):

1. A command transaction updates the claim, appends to `claim_activity` and inserts an `outbox_message`
   in the **same DB transaction** (`OutboxService.record` is `Propagation.MANDATORY`). This removes the dual-write
   problem: we can never commit a state change and lose its event, or publish an event for a rolled-back change.
2. `OutboxRelay` polls unpublished rows in id order and hands them to an `EventPublisher`
   (`InProcessEventPublisher` by default, `KafkaEventPublisher` under the `kafka` profile, which waits for the broker ack
   before marking the row published). It stops at the first failure to preserve order.
3. Delivery is **at-least-once**. The consumer (`ClaimEventHandler`) is **idempotent**: unique
   `(user_id, event_id)` plus an existence check.
4. The UI sees the outcome by polling (claims every 15s, notifications every 10s). SSE/WebSocket push is the
   obvious upgrade; polling keeps the sprint build simple and proxy-friendly.

Transport is a config switch, not a code fork: both paths call the same `ClaimEventHandler.handle`.
Reviewers can run everything with no broker, and flip to Kafka with `SPRING_PROFILES_ACTIVE=kafka`.

## 4. One Angular codebase, two user types

- A single SPA; `roleGuard` plus lazy `loadChildren` give claimants (`/claims/**`) and staff (`/staff/**`) separate
  bundles and navigation. Login routes each role to its own home (`Auth.home()`).
- **Guards are UX, not security.** Every endpoint re-checks role, market and ownership on the server.
- The shared claim detail page renders actions from **`allowedActions` computed by the server**, so the UI never
  re-implements the state machine or authorisation. When rules change, only the backend changes.
- State is signals; components `await` a typed `Api` wrapper. Errors surface the RFC 7807 `detail` string.

## 5. The contract and who owns it

The backend owns the contract: REST DTOs in `Dtos.java`, published as OpenAPI at `/v3/api-docs` (Swagger UI at
`/swagger-ui.html`), errors as RFC 7807 problem details, event payload as `ClaimDomainEvent`.
The Angular models in `core/models.ts` mirror it by hand today; generating them from the OpenAPI document
(openapi-typescript) is the first thing I would automate to prevent drift.

## 6. What an officer needs / what exposure means

**Officer** (`/staff/desk`, `/staff/queue`): their own desk sorted oldest first; ageing in days; SLA state
(5 days to decision, configurable, clock considered paused while waiting on the claimant); tiles for in review,
SLA breached, due within 24h, waiting on claimant, approved-to-settle, decided in 30 days; the unassigned queue for
their market with one-click pick-up (a lost race returns 409 and the list refreshes).

**Outstanding liability exposure**: for every _open_ claim (submitted, in review, info requested, approved but not
yet paid) take the best available figure: **approved amount, else officer reserve, else claimant's estimate**.
Aggregated in the database (`ClaimRepository.exposure`), grouped by market/type/status and totalled **per currency**.
The manager also sees how many claims are unassigned, past SLA and have no reserve yet (the three things that make
the number unreliable). Rejected and settled claims carry no exposure.

## 7. Correctness and safety decisions

- **Concurrency**: `@Version` on `Claim` plus a state guard in `pickUp`. Two officers racing to pick up one claim:
  exactly one wins, the other gets 409 (`ApiExceptionHandler` maps optimistic-lock failures too).
- **Idempotent submit**: optional `Idempotency-Key` header, unique column; the Angular form generates one key per form
  instance so retries and double-clicks can't create duplicates.
- **Authorisation** (server side, per request): claimants see only their own claims (404 rather than 403 so ids can't
  be probed); officers are limited to their market and to claims they are assigned; managers see all markets.
  Officers can approve up to a configurable authority limit (50,000); above it a manager must decide.
- **Data minimisation**: reserve amounts and internal assessment notes never leave the server for claimant callers.
- Validation with Bean Validation; one error shape (problem details) for the entire API.

## 8. Shortcuts taken under time pressure (all deliberate)

| Shortcut                                                          | What I'd do instead                                                                          |
| ----------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Demo "pick a user" auth with in-memory tokens                     | OIDC/JWT against the corporate IdP; roles from claims                                        |
| H2 in-memory + `ddl-auto: create-drop`                            | Postgres + Flyway migrations (indexes are already declared on the entities)                  |
| Queue market/type filtering and workload aggregation done in Java | SQL aggregates / paging; fine at demo scale, not at production scale                         |
| Single relay instance polling every 500ms                         | `SELECT ... FOR UPDATE SKIP LOCKED` (or Debezium CDC) for multiple instances                 |
| Polling for freshness                                             | SSE/WebSocket push driven by the same events                                                 |
| No attachments (photos, quotes, police reports)                   | Object storage + signed URLs; an `Attachment` entity and upload endpoint                     |
| No pagination, search or manager drill-down                       | Paged list endpoints with filters; click-through from exposure rows                          |
| SLA pause is a simplification (no due-date extension)             | Explicit SLA clock entity with pause/resume intervals                                        |
| Exposure not FX-converted                                         | Reporting-currency view with an agreed rate source                                           |
| Hand-mirrored TypeScript models                                   | Generate from OpenAPI in CI                                                                  |
| No frontend unit/e2e tests                                        | Component tests for the detail page's action rendering, Playwright happy path                |
| Single topic, JSON string payloads                                | Schema registry (Avro/JSON Schema), a DLT for poison messages, per-consumer topics as needed |

## 9. Test strategy

- `ClaimStateMachineTest`: pure domain rules, no framework.
- `ClaimLifecycleTest`: full HTTP -> service -> H2 -> outbox -> consumer path: the happy path with an info-request
  loop and notifications, the pick-up race (409), authority-limit escalation, claimant isolation, market isolation,
  idempotent submit, and exposure moving as claims open and close.
