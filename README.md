# RetailFlow — Spring Boot Backend

Java 21 / Spring Boot 3.5.6 / Gradle / persistent H2 / Flyway, deployed as a Docker image. Legacy Node.js API handlers also exist in the workspace; they are separate from this Spring application. The root Vercel configuration declares an `/api` proxy to Render.

## Layout

- `src/main/java/com/retailflow/controller` — HTTP endpoints
- `dto` — API payloads and validation
- `config` — filters, CORS, Swagger and bootstrap
- `entity` — Hibernate entities
- `repository` — Spring Data repositories
- `service` — existing domain services (product, sales, inventory, settings)
- `service/contract` — Java service interfaces (`interface` is a reserved Java keyword)
- `service/implement` — implementation classes, including user auth
- `service/validation` — location reserved for cross-field business validators
- `exception` — exception classes and global error handler
- `util` — utilities
- `src/main/resources/db/migration` — versioned PostgreSQL Flyway DDL

## New authentication

Users are persisted in `app_users` with username, email, full name, BCrypt password, role and enabled flag. Sessions are stored in `auth_sessions` with user relationship. Existing unowned tokens cannot be refreshed after migration. The bootstrap ADMIN is created only if all `BOOTSTRAP_ADMIN_*` fields are explicitly set and the database has no users. The user-create endpoint is ADMIN-only.

**No public signup endpoint.** API paths: POST `/api/auth/login`, POST `/api/auth/refresh`, POST `/api/auth/logout`, GET `/api/users/me`, GET `/api/users` (admin), POST `/api/users` (admin). Legacy product, inventory, sales, settings and UOM endpoints retain their paths.

## Build and hosting

See [H2_DEPLOYMENT.md](H2_DEPLOYMENT.md) for the current database and Render disk setup. Prisma data is not automatically migrated.

See [DEPLOYMENT.md](DEPLOYMENT.md). Configure all secrets externally; never use the original committed secrets. Gradle compilation and end-to-end integration tests must be run in an environment with the Gradle distribution/dependencies and a test PostgreSQL instance. This refactor is not production-verified.

## Java conventions

- Use Lombok `@RequiredArgsConstructor` with `private final` dependencies for services, controllers, and injectable components. Lombok is configured as a compile-only dependency and annotation processor.
- Use Jakarta packages for persistence, validation, servlet APIs, and lifecycle annotations. Keep Spring `@Transactional` for transaction options such as `readOnly`.
- Keep request payloads as Java records with Jakarta validation. Keep business logic in services; UOM writes run in a service transaction.
- `R2StorageClient` owns optional image storage configuration and closes its SDK client on shutdown.
- Run `./gradlew spotlessApply test bootJar` to format, test, and package. The focused service tests use mocked repositories; they do not verify PostgreSQL migrations or a live deployment.
