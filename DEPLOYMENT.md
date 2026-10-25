# RetailFlow Java backend deployment

## Deployment model

The **only backend** is Spring Boot. Deploy the `Dockerfile` to Render (or another Java/Docker host) with PostgreSQL, and use Vercel solely for proxy rewrites. Remove the old Vercel `api/index.js`; no Node backend is included.

1. Rotate all previously committed database credentials and default admin credentials. Remove exposed credentials from commit history where practical.
2. Set `DB_URL` (JDBC URL), `DB_USER`, `DB_PASSWORD`, `DB_SCHEMA`, `CORS_ALLOWED_ORIGINS` on the Java host. Use a dedicated DB user with schema grants.
3. Temporarily set the four `BOOTSTRAP_ADMIN_*` variables on first deployment with a unique 12+ character password and no existing users. Remove bootstrap variables after success. Existing access/refresh sessions from pre-migration will be invalid.
4. Run Flyway against a backed-up development DB, verify migrations, then stage, then production. `ddl-auto: validate` prevents implicit table changes. Existing Flyway baseline strategy must be verified for the target DB; do not re-baseline databases that already have history.
5. Replace `YOUR-SPRING-BOOT-HOST.example.com` in `vercel.json` with the deployed HTTPS origin, commit to Vercel, and test every API route. This proxy file cannot run a Java server itself.
6. API docs are protected behind Bearer authentication; a browser loading Swagger UI directly cannot automatically supply an Authorization header to all UI asset requests. For practical interactive docs, use authenticated gateway access or a protected non-production docs origin; do not open docs to the public.
7. Test POST /api/auth/login, /api/auth/refresh, /api/users/me, /api/products, /api/sales and /api/health and failure cases (401, 403, 409).

## Development commands

`./gradlew bootRun` for local running after configuring environment variables. `./gradlew test` for tests. `./gradlew bootJar` for a deployable Java artifact.

## Compatibility

Preserved existing /api URL prefixes and access/refresh token response fields, but old sessions are intentionally invalidated. User creation is admin-only. HTTP Basic/Cookie login is not enabled.
