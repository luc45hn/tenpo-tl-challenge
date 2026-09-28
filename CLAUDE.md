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
| Retries | Declarative `@Retryable` (decide at slice 4 whether Spring Framework 7's built-in support makes the `spring-retry` dependency unnecessary; check official docs) | Declarative, cleaner than manual retry logic |
| History | PostgreSQL + Spring Data JPA | Explicit challenge requirement |
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
├── repository      → Spring Data JPA (CallHistoryRepository)
├── model / entity  → JPA entities (CallHistory)
├── dto             → Request/Response DTOs (records)
├── exception       → Custom exceptions + global @ControllerAdvice
└── ratelimit       → Rate limiting filter/interceptor (backed by Redis)
```

## Workflow (important)

- **The author (Lucas) makes all design decisions.** You (Claude Code) implement and suggest,
  but do not assume architecture changes unless explicitly asked to.
- **Short, precise, atomic commits.** One commit = one small, coherent functional unit.
  Conventional format: `feat:`, `test:`, `fix:`, `refactor:`, `docs:`, `chore:`.
- **Incremental tests, not at the end.** Every time a testable functional unit is completed
  (e.g. the simple calculation, the mock client, the cache, the retries, the history, the rate
  limiting), its corresponding tests are added in the same commit or the immediately following
  one — the full test suite is never postponed to the end of development.
- Development in **small vertical slices**, in this suggested order:
  1. Base endpoint + simple calculation (no cache, no external percentage)
  2. Mock external service (random decimal 5–20% percentage), its client, and applying the
     percentage to the calculation
  3. Percentage cache in Redis with 30-minute TTL
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