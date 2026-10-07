# be-interview-prep

Java 21 / Spring Boot / PostgreSQL backend, built with Maven.

> **Status:** Spring Boot 4.1 service with the Task API, the URL Shortener, the Product Catalog, the Order
> Service and JWT authentication with USER/ADMIN roles (below).
> Standards for contributors and AI agents are in [CLAUDE.md](CLAUDE.md).

## Prerequisites

- JDK 21
- Docker (integration tests use Testcontainers PostgreSQL)
- No global Maven needed — use the wrapper (`./mvnw`, or `.\mvnw.cmd` on Windows)
- [GitHub CLI](https://cli.github.com) (`gh`) for PRs

## Common commands

| Purpose                       | Command                     |
|-------------------------------|-----------------------------|
| Format                        | `./mvnw spotless:apply`      |
| Unit + slice tests            | `./mvnw test`                |
| Full verification (as CI)     | `./mvnw -B -ntp verify`      |
| Run locally                   | `./mvnw spring-boot:run`     |

Running locally needs a PostgreSQL instance; for example:

```sh
docker run --rm -d --name tdb-postgres -p 5432:5432 \
  -e POSTGRES_DB=tdb -e POSTGRES_USER=tdb -e POSTGRES_PASSWORD=tdb postgres:16
```

Connection settings are supplied via environment variables
(`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`) —
never commit credentials.

The app also **requires** a token-signing secret and refuses to start without one (see
[Authentication](#authentication)):

```sh
export APP_SECURITY_JWT_SECRET="$(openssl rand -base64 48)"
./mvnw spring-boot:run
```

## Authentication

Every endpoint requires `Authorization: Bearer <accessToken>` except registration, login, the
short-link redirect `GET /{code}` and `GET /actuator/health`. Tokens are stateless, HS256-signed
JWTs (no server-side session) that **expire 15 minutes** after login; there are no refresh
tokens — log in again for a new one.

| Method | Path                     | Access        | Description                                                     |
|--------|--------------------------|---------------|-----------------------------------------------------------------|
| `POST` | `/api/v1/auth/register`  | public        | `{email, password}` → `201` + profile `{id, email, role, createdAt}`, `Location: /api/v1/users/me`; `409` if the email exists |
| `POST` | `/api/v1/auth/login`     | public        | `{email, password}` → `200` `{accessToken, tokenType: "Bearer", expiresIn: 900, expiresAt}`; `401` "Invalid email or password" |
| `GET`  | `/api/v1/users/me`       | any logged-in | Own profile                                                      |
| `GET`  | `/api/v1/users`          | ADMIN         | All profiles, paginated (`page`, `size` ≤ 100)                   |

- Roles: `USER` and `ADMIN`. Self-registration always creates a `USER` (a `role` in the body is
  ignored). No token or an invalid/expired token → `401`; a valid token without the required
  role → `403`. Both are Problem Details JSON (`application/problem+json`), never HTML.
- Emails are trimmed and case-insensitive. Passwords: 8–72 characters and at most 72 bytes in
  UTF-8 (bcrypt's limit); stored only as a bcrypt hash and never returned or logged.
- Configuration (environment variables; nothing secret is committed):

  | Variable                       | Required | Meaning |
  |--------------------------------|----------|---------|
  | `APP_SECURITY_JWT_SECRET`      | yes      | HMAC signing key, at least 32 bytes. Generate with `openssl rand -base64 48`. Rotating it invalidates all issued tokens. |
  | `APP_SECURITY_ADMIN_EMAIL`     | no       | With the password below, creates this ADMIN at startup if no account with that email exists (idempotent). |
  | `APP_SECURITY_ADMIN_PASSWORD`  | no       | Password for that admin (same rules as above). Set both or neither. |

```sh
curl -i -X POST http://localhost:8080/api/v1/auth/register   -H 'Content-Type: application/json'   -d '{"email": "ada@example.com", "password": "correct horse battery"}'
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login   -H 'Content-Type: application/json'   -d '{"email": "ada@example.com", "password": "correct horse battery"}' | jq -r .accessToken)
curl -s http://localhost:8080/api/v1/users/me -H "Authorization: Bearer $TOKEN"
curl -i http://localhost:8080/api/v1/users -H "Authorization: Bearer $TOKEN"   # 403 for a USER
```

## Task API

Base path `/api/v1/tasks`; requires a bearer token (any role). Errors use RFC 9457 Problem Details (`application/problem+json`);
validation failures add an `errors: [{field, message}]` array.

| Method   | Path                  | Description                                                    |
|----------|-----------------------|----------------------------------------------------------------|
| `POST`   | `/api/v1/tasks`       | Create a task → `201` + `Location`                            |
| `GET`    | `/api/v1/tasks`       | List tasks, newest first (`?status=TODO\|IN_PROGRESS\|DONE`, `page`, `size` ≤ 100) |
| `GET`    | `/api/v1/tasks/{id}`  | Get one task (`404` if unknown)                                |
| `PUT`    | `/api/v1/tasks/{id}`  | Replace a task (`title` and `status` required)                 |
| `DELETE` | `/api/v1/tasks/{id}`  | Delete a task → `204`                                          |

Fields: `title` (required, ≤ 100 chars), `description` (≤ 1000 chars), `status`
(defaults to `TODO` on create), `dueDate` (`yyyy-MM-dd`, today or later in India time, Asia/Kolkata); `id`,
`createdAt` and `updatedAt` are set by the server.

```sh
curl -i -X POST http://localhost:8080/api/v1/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title": "Prepare for interview", "dueDate": "2030-01-31"}'
```

## URL Shortener

Creating links and reading stats require a bearer token (any role); the redirect `GET /{code}` is public.

| Method | Path                             | Description                                                                 |
|--------|----------------------------------|-----------------------------------------------------------------------------|
| `POST` | `/api/v1/links`                  | Shorten `{"url", "expiresAt"?}` → `201` + `Location` (the stats URL) for a new link, `200` with the existing link for a repeat |
| `GET`  | `/{code}`                        | `302 Found` to the original URL and counts the visit; `404` unknown, `410` expired |
| `GET`  | `/api/v1/links/{code}/stats`     | `{code, shortUrl, originalUrl, visitCount, createdAt, expiresAt, expired}` (also for expired links; `404` unknown) |

- `url`: required, ≤ 2048 chars, absolute `http`/`https` URL with a host; surrounding whitespace is
  stripped, otherwise stored as submitted (never fetched). `expiresAt`: optional ISO-8601 date-time
  with offset, must be in the future.
- Codes are 7 random base62 characters (`[A-Za-z0-9]`, `SecureRandom`), unique by database
  constraint; a collision is retried with a new code.
- Short URLs are `${APP_LINKS_BASE_URL}/{code}` (`app.links.base-url`, default `http://localhost:8080`).
- The redirect is `302`, not `301`, and `Cache-Control: no-store`: a cached permanent redirect would
  bypass the server, so visits could not be counted and expiry could not be enforced.
- **Shortening the same URL twice** with the same expiry (or both without one) returns the existing
  link with `200` instead of creating a new code: callers get a stable code and there are no
  duplicate rows. A different expiry is a different lifetime, so it gets its own link. This is
  enforced by a unique constraint on (SHA-256 of the URL, expiry), so concurrent identical requests
  also get the same code. Since a requested expiry must be in the future, the returned link is never
  expired.
- Visits are counted with one atomic `UPDATE ... SET visit_count = visit_count + 1`, so counts stay
  exact under concurrent traffic. Only successful redirects count.

```sh
curl -i -X POST http://localhost:8080/api/v1/links \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://example.com/some/long/path", "expiresAt": "2030-12-31T23:59:59+05:30"}'
curl -i http://localhost:8080/<code>                 # 302, Location: https://example.com/some/long/path
curl -s http://localhost:8080/api/v1/links/<code>/stats -H "Authorization: Bearer $TOKEN"
```

## Product Catalog

Base path `/api/v1/products`; reads need a bearer token (any role), `PUT`/`DELETE` need ADMIN.
Flyway (`V4`) seeds 100 products on first startup.

| Method   | Path                      | Description                                                         |
|----------|---------------------------|---------------------------------------------------------------------|
| `GET`    | `/api/v1/products`        | Paged list `{content, page, size, totalElements, totalPages}`       |
| `GET`    | `/api/v1/products/{id}`   | One product (cached; `404` if unknown)                              |
| `PUT`    | `/api/v1/products/{id}`   | Replace `{name, category, price, stock, rating}` (ADMIN) → `200`    |
| `DELETE` | `/api/v1/products/{id}`   | Delete (ADMIN) → `204`                                              |

- Filters (all optional, combined with AND): `category` (exact), `minPrice`/`maxPrice` (inclusive;
  `minPrice > maxPrice` → `400`), `inStock=true` (stock > 0), `q` (case-insensitive name contains).
- `sort=field[,asc|desc]` with field in `id, name, category, price, stock, rating, createdAt`
  (default `id`; unknown field → `400`); `id` is always added as a tie-breaker.
- `page` ≥ 0, `size` ≥ 1 (default 20); sizes above 100 are clamped to 100.

```sh
curl -s "http://localhost:8080/api/v1/products?category=BOOKS&minPrice=50&maxPrice=300&inStock=true&q=product&sort=price,desc&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

**Caching.** `GET /api/v1/products/{id}` is cached in an in-process Caffeine cache `products`
(key = id, value = the immutable response DTO; max 1,000 entries, 10-minute TTL as a safety net).
`PUT` replaces the entry and `DELETE` evicts it. The cache manager is transaction-aware, so the
put/evict is applied only after the database transaction commits (a rolled-back update never
reaches the cache). How to see it:

- `ProductApiIT.repeatedLookupsHitTheCacheAndWritesNeverLeaveItStale` spies on the repository and
  asserts two GETs cause one `findById`, and that GET after `PUT`/`DELETE` returns the new state/`404`.
- With `logging.level.com.interviewprep.service.ProductService=DEBUG`, `Loading product {id} from
  database` is logged only on a cache miss.
- `GET /actuator/metrics/cache.gets?tag=result:hit` (and `result:miss`), with a bearer token.

The cache is per instance: with several instances, a write evicts only the local entry and other
instances may serve the old value for up to the TTL; a shared cache (e.g. Redis) or invalidation
messages would be needed then.

## Order Service

Base path `/api/v1/orders`; every endpoint needs a bearer token. Orders reserve stock of the
catalog products (Flyway `V5` adds `orders` and `order_items`).

| Method | Path                           | Description                                                                 |
|--------|--------------------------------|-----------------------------------------------------------------------------|
| `POST` | `/api/v1/orders`               | Place `{items: [{productId, quantity}]}` (1-50 items, quantity 1-1000) → `201` + `Location` |
| `GET`  | `/api/v1/orders/{id}`          | Owner or ADMIN; anyone else gets `404`                                      |
| `POST` | `/api/v1/orders/{id}/cancel`   | Owner or ADMIN → `200`; returns the stock; repeating it changes nothing     |

Response: `{id, status, items: [{productId, productName, quantity, unitPrice}], totalAmount, createdAt}`;
`status` is `PLACED` or `CANCELLED`. Lines for the same product are merged.

**Retries (`Idempotency-Key`).** `POST /api/v1/orders` requires an `Idempotency-Key` header
(1-100 characters of `A-Z a-z 0-9 _ -`; missing or invalid → `400`). Generate one key per
logical order (e.g. a UUID) and reuse it for every retry of that order. Keys are scoped to the
user. A repeat with the same key and the same request (items compared after merging and sorting)
returns the original order with `200` and `Idempotent-Replayed: true`, without reserving stock
again; the same key with a different request → `422`. Simultaneous repeats wait for the first to
finish and then get its order. A request that failed (e.g. `409`) leaves nothing behind, so
retrying it with the same key is evaluated again.

```sh
curl -i -X POST http://localhost:8080/api/v1/orders   -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"   -H "Idempotency-Key: 6f1c2b9e-5d0a-4c1e-9a43-2a7d3e8b1f00"   -d '{"items": [{"productId": 1, "quantity": 2}, {"productId": 2, "quantity": 1}]}'
```

**No overselling.** An order is one transaction: each item is reserved with a single conditional
statement, `UPDATE products SET stock = stock - :qty WHERE id = :id AND stock >= :qty`, in
ascending product id (so concurrent orders cannot deadlock). PostgreSQL serializes concurrent
updates of the same row and re-checks the condition, so stock never goes below zero (a
`CHECK (stock >= 0)` constraint backs this up). If any item cannot be reserved the whole order is
rolled back: unknown product → `404`; not enough stock → `409` with e.g. `Insufficient stock for
product 7: requested 3, available 1`. Cached product entries are evicted after commit, so
`GET /api/v1/products/{id}` shows the new stock. `OrderConcurrencyIT` fires 50 simultaneous orders
at a product with stock 10: exactly 10 succeed and the stock ends at 0.

**Cancel.** `POST /api/v1/orders/{id}/cancel` flips `PLACED` → `CANCELLED` in one conditional
update and only then adds each item's quantity back to stock, so cancelling twice (or
concurrently) restocks once. A product that appears in an order can no longer be deleted
(`DELETE /api/v1/products/{id}` → `409`).

## Development workflow

1. Branch from `main`: `feature/…`, `bugfix/…`, `refactor/…`, `chore/…`, `docs/…`.
2. Implement with tests, following [CLAUDE.md](CLAUDE.md).
3. `./mvnw spotless:apply && ./mvnw -B -ntp verify`.
4. Self-review the diff with [docs/code-review.md](docs/code-review.md)
   (Claude Code: `/self-review`).
5. Conventional commit, push, open a PR (template is pre-filled).
6. CI (`.github/workflows/ci.yml`) must pass; address review comments; merge only when
   all required checks and approvals are in place.

## Repository settings (maintainer, one-time)

Enable branch protection on `main` in GitHub → Settings → Branches: require a pull
request, require the **Build and test** status check, require at least one approval,
and disallow force pushes. These settings cannot be committed to the repository.
