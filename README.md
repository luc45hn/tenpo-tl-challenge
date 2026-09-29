# Tenpo Technical Lead Challenge

A REST API built with Spring Boot and Java 21 that adds two numbers and applies a dynamic
percentage obtained from an external service, with caching, retries, an asynchronous call
history, distributed rate limiting and RFC 9457 error responses.

Docker Hub image: [`lucashn81/tenpo-tl-challenge`](https://hub.docker.com/r/lucashn81/tenpo-tl-challenge)
(published for `linux/amd64` and `linux/arm64`).

## Requirements

- Docker and Docker Compose, to run the application and its dependencies.
- JDK 21, only if you want to build or run the tests locally instead of through Docker.

## Running it

### Quick start (pulls the published image)

```bash
docker compose up -d
docker compose ps   # wait until all three services report "healthy"
```

This starts PostgreSQL, Redis and the API, downloading the image from Docker Hub. To build the
image locally instead, add `--build`:

```bash
docker compose up --build -d
```

The API is then available at `http://localhost:8080`. Postgres and Redis are **not** published to
the host in this file, so they never collide with services you may already have running locally.

### Local development

To run the application with your own JDK (for example from an IDE) while still using
containerized dependencies, start only Postgres and Redis with the development override, which
publishes their ports to the host:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres redis
./mvnw spring-boot:run
```

Do not start the `api` service this way — it would collide with the local application on port
8080.

### Stopping

```bash
docker compose down          # stops the containers, keeps the data
docker compose down -v       # also removes the Postgres and Redis volumes
```

## Running the tests

```bash
./mvnw clean test
```

The suite needs a running Docker daemon: several tests use Testcontainers to run real PostgreSQL
and Redis instances. Tests that need Docker are skipped automatically when it is not available,
but running the full suite requires it.

## Trying the endpoints

### Swagger UI

With the application running, open `http://localhost:8080/swagger-ui.html` for interactive
documentation of every endpoint, parameter and possible error response. The raw OpenAPI document
is at `/v3/api-docs`.

### Calculate

```bash
curl "http://localhost:8080/api/v1/calculate?num1=5&num2=5"
# {"result":11.00}
```

`num1` and `num2` are required numbers. An invalid or missing parameter returns a 400 problem
detail; if the external percentage service is unavailable and no cached value exists, it returns
503.

### Call history

```bash
curl "http://localhost:8080/api/v1/history?page=0&size=20"
```

Returns the recorded API calls, newest first, with pagination metadata. `size` accepts 1 to 100.
Calls to this endpoint are not themselves recorded.

### Rate limiting

The whole API accepts at most 3 requests per minute in total (not per client). A 4th request
within the same minute returns:

```bash
curl -i "http://localhost:8080/api/v1/calculate?num1=1&num2=1"
# HTTP/1.1 429
# Retry-After: 42
# {"detail":"Rate limit exceeded: at most 3 requests per minute. Try again in 42 seconds.", ...}
```

### Health

```bash
curl "http://localhost:8080/actuator/health"
```

Reflects the real status of Postgres and Redis, and is used by the container's own healthcheck.

## Technical decisions

### Spring MVC over WebFlux

The API is built on classic Spring MVC rather than WebFlux. The whole system is capped at 3
requests per minute, which makes real concurrency trivial for any model — the concurrency problem
WebFlux exists to solve doesn't arise here. Given that, the cost of going reactive (the learning
curve, R2DBC instead of Spring Data JDBC, a larger surface for subtle bugs) has no real
counterpart benefit in this context. If the rate limit were substantially raised for production
use, this would be worth reevaluating — it's a contextual decision, not a dogmatic one.

### Vavr for functional error handling

[Vavr](https://vavr.io) is used for `Try` and `Option` throughout the code that can fail: the
external HTTP client, the retry helper, the percentage cache and the rate limiter all return a
`Try` or `Option` instead of throwing, which keeps failure handling explicit and composable
instead of relying on nested try/catch blocks. This fits a functional style translated to Java
21, using immutable records and pure functions wherever practical.

### Percentage cache with a stale fallback

The external percentage is cached in Redis as a single entry holding the value and the instant it
was fetched, with **no Redis expiry**. Freshness (30 minutes by default) is decided by the
application, comparing against the stored timestamp, rather than relying on Redis to expire the
key — because the entry needs to survive past its freshness window to serve as the last known
value when the external service is down. If Redis itself is unreachable, the cache is treated as
empty rather than failing the request.

### Retries without exception-driven annotations

The external client never throws (see Vavr above), so exception-driven retry annotations like
`@Retryable` don't fit this design. Instead, a small, framework-free helper retries a
`Try`-returning operation recursively: up to 3 attempts in total with a short pause between them,
only for transient failures (connection errors, timeouts and 5xx responses) — a 4xx or a
malformed body is never retried, since repeating it cannot help.

### Asynchronous call history

Every call to `/api/v1/**` is recorded, except the history endpoint itself (reading history
never creates history) and the internal mock endpoint. A servlet filter captures the request and
response and hands the record to a dedicated executor with a bounded queue, so persisting it never
adds latency to the response. If the queue is full or the insert fails, the record is dropped and
logged — recording is best effort and must never break or slow down the API.

### Distributed rate limiting

The 3-requests-per-minute limit is enforced globally (not per client, matching the challenge's
wording) using a sliding window log in Redis: a sorted set checked and updated atomically by a
single Lua script, so the limit holds correctly across multiple replicas even under concurrent
load. The script uses Redis's own clock rather than the application's, so replicas with skewed
clocks can't disagree. If Redis fails, requests are let through and a warning is logged —
availability over strictness, consistent with the percentage cache.

### RFC 9457 error responses

Every error — ours and the framework's alike (404, 405, 406, validation errors) — is returned as
an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem detail
(`application/problem+json`), with a single exception advice extending Spring's
`ResponseEntityExceptionHandler`. Client errors are descriptive; unexpected server errors always
return a generic message and are logged once with their full stack trace, so internal details are
never leaked in a response.

### Working with an AI coding agent

Most of the implementation was developed interactively with Claude Code, guided by a
[`CLAUDE.md`](CLAUDE.md) file in the repository root that records the architecture decisions,
package structure and workflow conventions once, so they don't need to be repeated in every
prompt. Design decisions were made by the author; the agent implemented them in small, tested,
reviewed increments, with each commit corresponding to one coherent unit of work.

## Known limitations

- **Rate limiting is global, not per client.** This matches the challenge's stated requirement,
  but a production system would typically limit per client or per IP.
- **If Redis is unavailable, the rate limit is not enforced** and requests are let through rather
  than rejected (availability over strictness).
- **The percentage fallback has no age limit.** If the external service stays down indefinitely,
  the last known percentage keeps being served, however old it is.
- **The call history queue lives in memory.** A crash can lose pending, not-yet-persisted records;
  a durable queue (a broker or a Redis stream) would be the production-grade choice.
- **A malformed URL path** (rejected by Tomcat before Spring sees the request) falls back to
  Spring Boot's default error format rather than the API's problem detail schema, since it never
  reaches the application's exception handling.
- **The malformed-percent-encoding handler depends on a Tomcat-specific exception type.** Switching
  the embedded server (to Jetty or Undertow, for example) would need that handler revisited.