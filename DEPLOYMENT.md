# RetailFlow deployment

Spring Boot is the only backend. **Vercel cannot run Docker containers or a JVM**, so:

```
Browser ──► Vercel (vercel.json rewrite /api/*) ──► Docker container (Spring Boot) ──► PostgreSQL
```

1. **Deploy the Docker image** to a container host (Render via `render.yaml`, or Fly.io / Railway / Cloud Run — anything that runs a Dockerfile) with a PostgreSQL database.
2. **Point Vercel at it**: edit `vercel.json`, replace `YOUR-SPRING-BOOT-HOST.example.com` with the deployed host, deploy this repo (or your frontend repo with the same rewrite) to Vercel. Requests to `/api/*` are then proxied and are same-origin for your frontend (no CORS needed).

## Environment variables (see `.env.example`)

| Variable | Required | Notes |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | yes | No defaults — app refuses to start without them |
| `DB_SCHEMA` | no | `prod_app` in the prod profile |
| `SPRING_PROFILES_ACTIVE` | no | `prod` recommended |
| `CORS_ALLOWED_ORIGINS` | no | Comma-separated; defaults to `*` |
| `RECOVERY_KEY` | no | Enables `/api/auth/reset-password`; disabled when empty |
| `SWAGGER_ENABLED` | no | `false` hides Swagger UI/API docs |
| `R2_*` | no | Image upload returns 501 when unset |
| `BOOTSTRAP_ADMIN_*` | first deploy | Only used when the users table is empty |

Flyway runs migrations on startup; Hibernate only validates (`JPA_DDL_AUTO=validate`).

## Local

```
docker build -t retailflow .
docker run --rm -p 8080:8080 --env-file .env retailflow
curl localhost:8080/api/health
```
Or `gradle bootRun` / `./gradlew bootJar` (Java 21).

## Smoke test after deploy

`GET /api/health` → `POST /api/auth/login` → with `Authorization: Bearer <accessToken>`: `GET /api/users/me`, `/api/products`, `/api/sales`, `/api/settings`, `/api/uom`; expect 401 without a token and 403 on `/api/users` for non-admins.

## Before going public

- **Rotate the database credentials that were hardcoded in the old `application.yml`** (they are still in git history).
- Migration `V8` and `UserServiceImpl` create default `admin` / `morkraksa` ADMIN accounts with a known default password (`admin1234`), and `authenticate()` auto-creates them on login with a default password. Change those passwords or remove this seeding before exposing the API.
