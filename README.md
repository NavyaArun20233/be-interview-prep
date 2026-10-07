# be-interview-prep

Java 21 / Spring Boot / PostgreSQL backend, built with Maven.

> **Status:** the development harness (standards, CI, PR template, review guide) is in
> place; application code has not been added yet. The first code PR must follow the
> bootstrap requirements in [CLAUDE.md](CLAUDE.md#bootstrapping-the-project-first-code-pr-only).

## Prerequisites

- JDK 21
- Docker (integration tests use Testcontainers PostgreSQL)
- No global Maven needed — use the wrapper (`./mvnw`, or `.\mvnw.cmd` on Windows)
- [GitHub CLI](https://cli.github.com) (`gh`) for PRs

## Common commands

| Purpose                       | Command                     |
|-------------------------------|-----------------------------|
| Format                        | `./mvnw spotless:apply`      |
| Unit tests                    | `./mvnw test`                |
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
