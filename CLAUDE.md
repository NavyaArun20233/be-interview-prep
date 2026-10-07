# CLAUDE.md

Instructions for AI coding agents (and humans) working in this repository.
Human-oriented setup lives in [README.md](README.md); the review rubric lives in
[docs/code-review.md](docs/code-review.md).

## Repository status

The repository contains a single Spring Boot service (base package `com.interviewprep`)
with the Task API and the URL Shortener (organized by layer: controller, service, repository, entity, dto, …)
plus this harness (instructions, CI, PR template, review guide). The stack is fixed:

| Concern            | Choice                                                        |
|--------------------|---------------------------------------------------------------|
| Language           | Java 21 (LTS)                                                  |
| Framework          | Spring Boot 4.1 (pinned in `pom.xml`; Jackson 3, Testcontainers 2) |
| Build              | Maven via the wrapper (`./mvnw`) — never rely on a global `mvn` |
| Database           | PostgreSQL                                                     |
| Migrations         | Flyway (`src/main/resources/db/migration`)                     |
| Tests              | JUnit 5, AssertJ, Mockito, Spring Boot Test, Testcontainers    |
| Formatting         | Spotless (`./mvnw spotless:apply` / `spotless:check`)          |
| Hosting / CI       | GitHub (`NavyaArun20233/be-interview-prep`) / GitHub Actions      |

**Before trusting anything in this file, check it against the repo.** If the code
contradicts this document, the code wins — flag the discrepancy and update this file
in the same PR.

### Bootstrapping the project (first code PR only)

The first PR that adds code must create a build that satisfies the CI contract
(`./mvnw -B -ntp verify` runs every gate):

- Maven wrapper committed (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`).
- `spring-boot-starter-web`, `-validation`, `-data-jpa`, `-actuator`; `flyway-core` +
  `flyway-database-postgresql`; `postgresql` driver.
- Test deps: `spring-boot-starter-test`, `spring-boot-testcontainers`,
  `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`.
- `maven-surefire-plugin` runs `*Test` (unit/slice); `maven-failsafe-plugin` runs `*IT`
  (integration) in `integration-test`/`verify`.
- `spotless-maven-plugin` with `check` bound to the `verify` phase.
- `spring.jpa.hibernate.ddl-auto=validate` and `spring.jpa.open-in-view=false`.

## Architecture

Single-module Spring Boot service, **package-by-layer** under `com.interviewprep`. Each
layer package holds the classes of every feature (task, link, …); a new feature adds its
classes to the matching layers, not a new top-level package:

```
src/main/java/com/interviewprep/
  Application.java          # @SpringBootApplication entry point
  controller/               # REST controllers: HTTP only (mapping, validation, DTO <-> service)
    TaskController, ShortLinkController, ShortLinkRedirectController
  service/                  # business logic, transaction boundaries
    TaskService, ShortLinkService, ShortCodeGenerator
  repository/               # Spring Data JPA interfaces
    TaskRepository, ShortLinkRepository
  entity/                   # JPA entities and their enums
    Task, TaskStatus, ShortLink
  dto/                      # request/response records; one sub-package per feature
    PageResponse            #   shared DTOs at the root
    task/  link/
  exception/                # GlobalExceptionHandler, base + feature exceptions
  config/                   # @Configuration and @ConfigurationProperties
  validation/               # custom Bean Validation constraints
src/main/resources/
  application.yml           # defaults; secrets only via env vars
  db/migration/V<n>__<desc>.sql # Flyway
src/test/java/com/interviewprep/
  controller/  service/  config/  validation/   # *Test = unit / @WebMvcTest slice, same package as the class
  integration/                                  # *IT = full app + Testcontainers PostgreSQL
```

Layer rules:

- **Controller** → calls services only. No repositories, no entities in signatures, no
  business logic, no `@Transactional`.
- **Service** → owns business rules and transactions. Returns DTOs or domain objects;
  never `ResponseEntity` or anything HTTP-specific.
- **Repository** → data access only. No business logic.
- **Entity** → persistence model. Never serialized directly to API clients.
- Dependencies flow inward: controller → service → repository. Code for one feature may
  call another feature's **service**, never its repository.
- `exception`, `config`, `validation` and shared `dto` classes must not depend on
  `controller`/`service`; generic bases (e.g. `ResourceNotFoundException`) are extended by
  feature exceptions (`TaskNotFoundException`).

## Coding standards

- **Naming:** `PascalCase` classes, `camelCase` members, `UPPER_SNAKE` constants.
  Suffixes: `*Controller`, `*Service`, `*Repository`, `*Request`, `*Response`,
  `*Exception`, `*Config`. Tests: `<Class>Test` (unit/slice), `<Feature>IT` (integration).
- **DI:** constructor injection only, `private final` fields. No field `@Autowired`.
  Single constructor needs no annotation.
- **DTOs:** Java `record`s in `<feature>/dto`. Map explicitly (static factory or small
  mapper); no reflection-based mappers unless already adopted.
- **Validation:** Bean Validation on request DTOs (`@NotBlank`, `@Size`, …) + `@Valid` in
  controllers. Business invariants are checked in services and raise domain exceptions.
- **Exceptions:** throw specific unchecked exceptions from services
  (`TaskNotFoundException`). Translate to HTTP in one `@RestControllerAdvice`. Never
  swallow exceptions; never catch `Exception` except in that advice.
- **Logging:** SLF4J (`private static final Logger log = LoggerFactory.getLogger(...)`).
  Parameterized messages (`log.info("Created task {}", id)`). Never log secrets, tokens,
  passwords or full request bodies containing personal data. `ERROR` = needs attention,
  `WARN` = recoverable anomaly, `INFO` = business events, `DEBUG` = diagnostics.
- **Transactions:** `@Transactional` on service methods (or class) only.
  `@Transactional(readOnly = true)` for reads. No remote/HTTP calls inside a transaction.
- **Nulls:** use `Optional` for absent single results from repositories/services; don't
  pass or return `null` collections.
- **Config:** typed `@ConfigurationProperties` records over scattered `@Value`. Secrets
  only from environment variables — never committed.
- Keep changes minimal and consistent with surrounding code. No drive-by refactoring.

## API standards

- Base path `/api/v1`. Plural, kebab-case resource nouns: `/api/v1/tasks`,
  `/api/v1/tasks/{id}/sub-tasks`. JSON fields in `camelCase`.
- Methods / status codes:

  | Operation        | Method   | Success                 |
  |------------------|----------|-------------------------|
  | List / get       | `GET`    | `200`                    |
  | Create           | `POST`   | `201` + `Location` header |
  | Full replace     | `PUT`    | `200` (or `204`)          |
  | Partial update   | `PATCH`  | `200`                    |
  | Delete           | `DELETE` | `204`                    |

  Errors: `400` validation/malformed, `401` unauthenticated, `403` forbidden, `404`
  not found, `409` conflict/duplicate, `422` business rule violated, `500` unexpected
  (never leak stack traces or SQL).
- Lists are paginated (`page`, `size`, `sort`) with a bounded max `size`.
- **Error format:** RFC 9457 Problem Details (`ProblemDetail`,
  `application/problem+json`):

  ```json
  {
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "Task 42 not found",
    "instance": "/api/v1/tasks/42",
    "errors": [{ "field": "title", "message": "must not be blank" }]
  }
  ```
  `errors` is present only for validation failures.
- **Compatibility:** don't remove/rename fields, change types, or tighten validation on
  existing endpoints without a new version or an explicit, documented decision.

## Security

No authentication is configured yet. When Spring Security is added: deny by default,
authorize every endpoint explicitly, test both allowed and forbidden paths, and never
weaken an existing rule as a side effect of another change.

## Database standards

- **Migrations:** Flyway only; `ddl-auto=validate` (Hibernate never changes schema).
  Files `V<n>__<snake_case_description>.sql`. **Never edit a migration that has been
  merged** — add a new one. Make destructive changes in steps (add → backfill → switch →
  drop in a later release). Large-table index creation: consider `CREATE INDEX
  CONCURRENTLY` (in its own non-transactional migration).
- **Entities:** `id BIGINT GENERATED ALWAYS AS IDENTITY` (or UUID if the feature needs
  it); `created_at`/`updated_at TIMESTAMPTZ`; `@Version` for optimistic locking where
  concurrent updates are possible. Associations `LAZY` by default; no
  `CascadeType.ALL`/`REMOVE` without a reason. Enums as `@Enumerated(STRING)`.
  Tables/columns `snake_case`, tables plural.
- **Repositories:** extend `JpaRepository`. Prefer derived queries; use JPQL `@Query`
  for anything non-trivial; native SQL only when necessary and covered by an IT.
  Always bind parameters — never concatenate SQL.
- **N+1:** fetch associations needed by a use case with `JOIN FETCH` or
  `@EntityGraph`; use DTO projections for read-only lists; never return lazy collections
  through the API.
- **Indexes:** every foreign key and every column used in frequent `WHERE`/`ORDER BY`
  gets an index in the same migration that introduces the query. Unique business keys
  get a unique constraint (not just an application check).

## Testing standards

| Kind              | Use for                                         | Tooling                                    | Name   |
|-------------------|-------------------------------------------------|--------------------------------------------|--------|
| Unit              | service logic, mappers, validators              | JUnit 5 + Mockito + AssertJ, no Spring     | `*Test` |
| Controller slice  | request mapping, validation, status codes, error body | `@WebMvcTest` + `MockMvc`, service mocked | `*Test` |
| Repository        | custom queries, constraints, migrations         | `@DataJpaTest` + Testcontainers PostgreSQL (`@AutoConfigureTestDatabase(replace = NONE)`) | `*IT` |
| Integration / API | full stack through HTTP to real PostgreSQL      | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers `@ServiceConnection` | `*IT` |
| End-to-end        | critical user journeys only, sparingly           | same as integration                         | `*IT` |

Rules:
- **Never use H2** or another in-memory DB as a PostgreSQL substitute.
- Every bug fix gets a test that fails without the fix.
- Every new endpoint: slice test (happy path + validation + not-found/error) and at least
  one IT. Every migration is exercised by at least one IT.
- Deterministic: no `Thread.sleep`, no real clock (inject `Clock`), no order dependence,
  no external network beyond Testcontainers. Each test sets up its own data.

## Commands

| Purpose                         | Command                               |
|---------------------------------|---------------------------------------|
| Format code                     | `./mvnw spotless:apply`                |
| Compile                         | `./mvnw -q compile`                    |
| Unit + slice tests              | `./mvnw test`                          |
| Single test                     | `./mvnw test -Dtest=TaskServiceTest`   |
| **Full gate (CI equivalent)**   | `./mvnw -B -ntp verify`                |
| Single IT                       | `./mvnw verify -Dit.test=TaskIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` |
| Run locally                     | `./mvnw spring-boot:run` (needs PostgreSQL; see README) |

On Windows PowerShell use `.\mvnw.cmd`. Integration tests require Docker running.
Until `pom.xml` exists these commands do not work — say so rather than pretending.

## Workflow for every task

Implementation work (steps 1–9) can be delegated to the project agent
`senior-java-developer` (local `.claude/agents/`, git-ignored); it stops after
committing locally, and the orchestrator/user handles push, PR and merge (steps 10–13).

1. **Understand** — restate expected behavior; list affected modules, API changes, DB
   changes, dependencies, risks. Ask if a decision can't be inferred.
2. **Git preflight** — `git status`, `git branch --show-current`. Stop if there are
   unexpected uncommitted changes. Branch from up-to-date `main`:
   `feature/…`, `bugfix/…`, `refactor/…`, `chore/…`, `docs/…` (short kebab-case).
3. **Inspect** — read the relevant code and tests before editing.
4. **Plan** — a short, concrete plan (files to touch, tests to add).
5. **Implement** — smallest change that satisfies the requirement; follow this file.
6. **Test** — add/update tests; run the targeted tests.
7. **Quality** — `./mvnw spotless:apply`, then `./mvnw -B -ntp verify`.
8. **Self-review** — run `/self-review` (or follow
   [docs/code-review.md](docs/code-review.md)) over the full diff; fix all P0–P2.
9. **Commit** — conventional commit (below), staged files reviewed with
   `git diff --cached`.
10. **Push** — `git push -u origin <branch>`.
11. **PR** — `gh pr create` using `.github/pull_request_template.md`, every section
    filled in (write "None" rather than deleting). **Problem**, **Approach**,
    **Decisions & Trade-offs** and **How to Test** are mandatory and must be specific.
    Do not add tool attribution lines (e.g. "Generated with Claude Code") to PR descriptions.
12. **Follow up** — `gh pr checks <n> --watch`; read review comments; fix, re-run step 7,
    push new commits (no force-push).
13. **Merge** — only per "Merge rules" below.

## Git safety rules

- Never commit to `main` directly unless explicitly instructed.
- Never run `git reset --hard`, `git clean -fd`, `git checkout -- .`, `git push --force`
  (or `--force-with-lease`), `git rebase` of pushed branches, or delete branches without
  explicit authorization. Never force-push `main`.
- Never overwrite or revert changes you didn't make. Stage files explicitly
  (`git add <path>`), not `git add -A`, unless you've reviewed every path.
- Never commit secrets, `.env*`, credentials, keys, `target/`, IDE files, logs, or
  unrelated changes. `.gitignore` covers the common cases — still check `git diff --cached`.
- Never skip hooks (`--no-verify`) or bypass signing.

## Commit convention

[Conventional Commits](https://www.conventionalcommits.org): `<type>(<optional scope>): <imperative summary>`,
≤72 chars, types `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`, `ci`,
`perf`. One coherent change per commit; body explains *why* when not obvious.

```
feat(task): add task filtering API
fix(task): return 404 for missing task
```

## Merge rules

Merge (`gh pr merge <n> --squash --delete-branch`) **only** when all are true:
- every required CI check is green (`gh pr checks <n>`),
- no unresolved P0/P1/P2 review findings or open review threads,
- required approvals are present (`gh pr view <n> --json reviewDecision`),
- the user has asked for the merge.

Never use `--admin`, never disable checks or protection, never merge because "tests pass
locally". If you lack permission, stop and report exactly what is outstanding. Never claim
a push, PR, review or merge happened unless the command succeeded.

## Definition of done

Requirement implemented · follows this architecture · tests added/updated and passing ·
`./mvnw -B -ntp verify` green · diff self-reviewed with no open P0–P2 · no unrelated
changes or secrets · branch pushed · PR created with template filled · CI green · review
comments resolved · approvals present · merged per merge rules (or remaining steps
reported).
