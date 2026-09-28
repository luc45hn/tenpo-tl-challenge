# CLAUDE.md — `tenpo-tl-challenge` project context

This file is automatically read by Claude Code at the start of each session in this repository.
It contains architecture decisions and conventions that are already **closed** — do not question
them or re-propose them as alternatives. Your role is to implement following these guidelines,
not to re-discuss them unless explicitly asked to.

## Project language

**All project artifacts must be in English**: code, comments, commit messages, README, Javadoc,
variable/method/class names, exception messages, API responses, Swagger documentation, etc.
This is a deliberate choice to match the conventions of international projects. Conversations
between the author and Claude Code (in the terminal, while working) can remain in Spanish if
more comfortable — but anything that ends up committed to the repository must be written in
English.

## Project context

REST API in Spring Boot (Java 21) for Tenpo's technical challenge (Technical Lead role).
Full functional requirements are in `docs/challenge.pdf` (or `CHALLENGE.md` if transcribed).

Functional summary:
- Endpoint that adds `num1 + num2` and applies a dynamic percentage obtained from a mocked
  external service.
- The mock service returns a **random percentage between 5% and 20%** on each real invocation
  (not fixed), so cache behavior can be verified realistically.
- The percentage is cached with a 30-minute TTL; if the external service fails, the last cached
  value is used; if there is no cached value, the API responds with an appropriate HTTP error.
- Retries on external service failure: maximum of 3 attempts.
- Call history persisted in PostgreSQL, logged **asynchronously** (must not affect the main
  endpoint's latency) and with **pagination** on its query. If logging fails, it must not impact
  the response of the invoked endpoint.
- Rate limiting: maximum of 3 RPM across the whole API. Exceeding the limit responds with 429
  and a descriptive message.
- Proper handling of 4XX/5XX HTTP errors with clear messages.
- Designed for **multiple replicas**: both the percentage cache and rate limiting must be shared
  across instances (not held in local memory).

## Architecture decisions (closed)

| Item | Decision | Brief rationale |
|---|---|---|
| Framework version | Spring Boot 4.1.x (latest patch), Java 21 | Only lines still under OSS support should be used; 3.5.x reached end of OSS support in June 2026. Never pick an unsupported version |
| Build tool | Maven | More universal so third parties can clone and run with no friction |
| Concurrency model | Classic Spring MVC (not WebFlux) | The 3 RPM limit makes reactive complexity unnecessary; full justification in the README |
| Cache / Rate limiting | Redis from day one | Needed anyway to support multiple replicas; avoids a later refactor |
| HTTP client | `RestClient` (Spring 6.1+) | Modern recommended replacement for `RestTemplate` (in maintenance mode); synchronous, consistent with classic MVC |
| Retries | Small functional helper that retries a Vavr `Try` (no `spring-retry`, no annotations) | The client returns a `Try` and never throws, so exception-driven annotations do not fit; keeps the style consistent and testable without Spring |
| History | PostgreSQL + Spring Data JDBC (immutable records, no Hibernate), schema managed with Flyway | PostgreSQL is the explicit challenge requirement; JDBC maps records directly and fits an append-only table and the functional style |
| API documentation | springdoc-openapi (Swagger UI) | Explicit challenge requirement |
| Error handling / functional style | **Vavr** (`Try`, `Either`) for failure-prone flows (external call, retries) | Author's background is Scala/functional; avoids nested try/catch |

## Code style

- Favor a **functional style within Java 21**:
  - `record` for DTOs and immutable models where it makes sense.
  - Avoid setters and unnecessary mutability.
  - Separate pure functions (calculation: sum + percentage application) from side effects
    (external HTTP call, persistence, cache).
  - Use `Optional` for return values that may not find a result (not on entity fields).
  - Use Streams instead of imperative loops for collection transformations.
  - Use Vavr `Try`/`Either` to model failures from the external service and retries, instead of
    nested try/catch or exceptions used for control flow.
- Use Java 21 sealed interfaces / pattern matching where they add clarity (e.g. success/failure
  result types for cases not covered by Vavr).

## Package structure

```
com.tenpo.challenge
├── config          → RestClient bean, RetryConfig, RateLimiter config, OpenAPI config, Redis config
├── controller      → REST endpoints (CalculationController, HistoryController)
├── service         → Business logic (CalculationService, PercentageService, HistoryService)
├── client          → Mock external service client (PercentageClient)
├── cache           → PercentageCache port and its Redis implementation
├── retry           → Functional helper that retries a Try-returning operation
├── repository      → Spring Data JDBC (CallHistoryRepository)
├── model / entity  → Immutable records persisted with Spring Data JDBC (CallHistory)
├── filter          → Servlet filters (call history recording)
├── dto             → Request/Response DTOs (records)
├── exception       → Custom exceptions + global @ControllerAdvice
└── ratelimit       → Rate limiting filter/interceptor (backed by Redis)
```

## Workflow (important)

- **The author (Lucas) makes all design decisions.** You (Claude Code) implement and suggest,
  but do not assume architecture changes unless explicitly asked to.
- **Short, precise, atomic commits.** One commit = one small, coherent functional unit.
  Conventional format: `feat:`, `test:`, `fix:`, `refactor:`, `docs:`, `chore:`.
- **Never run git commands** (no add, commit, mv, reset, stash, restore, checkout, etc.). The
  author handles all git operations. Rename files with plain `mv`. When a task is done, list the
  files created or modified and propose commit messages, nothing more.
- **Commit messages describe the change**, never the internal working vocabulary: no mentions of
  slices, steps or numbering.
- **Incremental tests, not at the end.** Every time a testable functional unit is completed
  (e.g. the simple calculation, the mock client, the cache, the retries, the history, the rate
  limiting), its corresponding tests are added in the same commit or the immediately following
  one — the full test suite is never postponed to the end of development.
- Development in **small vertical slices**, in this suggested order:
  1. Base endpoint + simple calculation (no cache, no external percentage)
  2. Mock external service (random decimal 5–20% percentage), its client, and applying the
     percentage to the calculation
  3. Percentage cache in Redis (30-minute freshness, last known value as fallback)
  4. Retries on external service failure
  5. Asynchronous history + persistence in Postgres (with pagination)
  6. Distributed rate limiting (Redis)
  7. Global HTTP error handling
  8. Docker Compose, Swagger, final README

## External percentage service (mock)

- The mock lives inside this same app as `GET /mock/percentage` and returns a random **decimal**
  percentage between 5.00 and 20.00 (2 decimals) as JSON. It is consumed by `PercentageClient`
  through `RestClient` over real HTTP, so retries and failures can be exercised realistically.
  Base URL and timeouts are configurable in `application.yml`.
- `/mock/**` is not part of the public API: rate limiting (slice 6) and call history (slice 5)
  apply only to `/api/v1/**`, never to `/mock/**`.
- `PercentageClient` returns a Vavr `Try<BigDecimal>` and never throws. Vavr enters the pom in
  this slice.
- Calculation: `(num1 + num2) * (1 + percentage / 100)`, rounded to 2 decimals with `HALF_UP`
  only at the end. `CalculationService.calculate` stays a pure function that receives the
  already-resolved percentage; effects (HTTP call, later cache and retries) live in
  `PercentageService`.

## Percentage cache (Redis)

- The percentage is stored in Redis as a **single entry holding the value and the instant it was
  fetched**, with no Redis expiry. Freshness (30 minutes, configurable in `application.yml`) is
  decided in the application by comparing against an injected `Clock`, as a pure function. The
  entry is kept after it stops being fresh so it can serve as the last known value.
- Flow in `PercentageService`: fresh entry -> return it without calling the external service;
  otherwise call the client; on success store and return; on failure return the stored entry even
  if stale; with no entry at all, fail (the existing 503). Only successful fetches are stored.
- Redis failures degrade gracefully: a failing read is treated as an empty cache and a failing
  write is logged and ignored. The cache must never break the endpoint. Malformed entries are
  treated as absent.
- Storage sits behind a `PercentageCache` interface (Redis implementation) so the service logic
  is tested with an in-memory fake and a controllable clock. The Redis implementation is tested
  with Testcontainers, skipped automatically when Docker is not available. No distributed lock:
  with the global 3 RPM limit concurrent refreshes are irrelevant (mention in the README).

## Retries

- At most 3 attempts in total (the original call plus up to two retries), configurable in
  `application.yml` (`percentage.retry.max-attempts`, default 3, at least 1), with a short fixed
  pause between attempts (`percentage.retry.delay`, default 200ms).
- Only transient failures are retried: connection errors, timeouts and 5xx responses. 4xx
  responses and malformed or invalid bodies fail immediately, since repeating them cannot help.
- Retries wrap the client call inside `PercentageService`, so they only happen when the cache is
  not fresh. When the attempts are exhausted the existing fallback applies (stale entry,
  otherwise the 503).
- Implemented as a small Spring-free helper that retries a `Try`-returning operation given the
  maximum attempts, a "should retry" predicate and a pause function. The pause is injected so
  tests never sleep. Worst case wait: read timeout times attempts, plus the pauses.

## Call history

- Every call to `/api/v1/**` is recorded, except calls to the history endpoint itself (reading
  the history never creates history) and anything under `/mock/**`.
- Each record holds: instant (UTC), HTTP method and path, query string, response status and
  response body (the result, or the problem detail that was returned).
- Captured by a servlet filter that wraps the response to read its body (always copy the body
  back to the real response). The filter runs first in the chain, so responses produced by later
  filters (rate limiting) and error responses (400, 429, 503, 5xx) are recorded too.
- Recording is asynchronous and best effort: the record is built in the request thread and only
  the insert goes to a dedicated executor with a bounded queue. If the queue is full the record is
  dropped and a WARN is logged; any failure while recording is logged and swallowed. Recording
  must never slow down or break the endpoint. On shutdown the executor waits for pending records.
  Known limitation for the README: the queue lives in memory, so a crash can lose pending records
  (a durable queue would be the production choice).
- Storage: PostgreSQL with Spring Data JDBC, records as immutable domain objects, schema managed
  by Flyway migrations. Repository tests use Testcontainers with Postgres, skipped automatically
  when Docker is not available.
- Query: `GET /api/v1/history?page=0&size=20`, newest first, maximum size 100 (an invalid page or
  size is a 400). It responds with the items and the paging metadata (page, size, total elements,
  total pages) in our own DTO, not Spring's `Page`.

## Docker Compose

Three services: `postgres`, `redis`, `api` (with healthchecks and corresponding `depends_on`).

## README writing guidance (talking points, not final text)

The points below are **talking points to develop in your own prose** when drafting the final
README — they are notes for you (Claude Code) to remember what needs to be justified and why,
not text to paste verbatim. The actual README must read as a coherent, professionally written
document, not a bullet list of these notes.

- **Spring MVC vs. WebFlux**: explain that the 3 RPM limit across the whole system makes real
  concurrency trivial for any model, so the cost of WebFlux (learning curve, R2DBC instead of
  JPA, larger bug surface) has no real counterpart benefit in this context. Optionally note that
  if the rate limit were substantially raised in the future, it would be worth reevaluating the
  model — it's a contextual decision, not a dogmatic one.
- **Vavr**: explain why it was chosen (author's functional background, handling failures without
  nested try/catch) and what it specifically replaces in the code.
- **Workflow with Claude Code**: briefly mention it as part of the development process (Context
  Engineering via this same file), without going into excessive detail.