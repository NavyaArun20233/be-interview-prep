# be-interview-prep

Java 21 / Spring Boot / PostgreSQL backend, built with Maven.

> **Status:** Spring Boot 4.1 service with the Task API (below). Standards for contributors
> and AI agents are in [CLAUDE.md](CLAUDE.md).

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

## Task API

Base path `/api/v1/tasks`. Errors use RFC 9457 Problem Details (`application/problem+json`);
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
