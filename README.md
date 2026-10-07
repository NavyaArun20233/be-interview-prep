# be-interview-prep

Java 21 / Spring Boot / PostgreSQL backend, built with Maven.

> **Status:** Spring Boot 4.1 service with the Task API, the URL Shortener and JWT
> authentication with USER/ADMIN roles (below).
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
