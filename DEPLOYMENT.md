# GitHub Actions deployment to Render

The workflow `.github/workflows/backend.yml` belongs at the root of the separate `inventory_app_backend` repository (this backend folder).

## One-time setup

1. In the existing Render service, confirm it deploys this repository's `main` branch with Docker and no root-directory prefix.
2. Set Render **Auto-Deploy → Off**. The blueprint also specifies this, but editing YAML does not update a manually configured service automatically.
3. Copy Render **Settings → Deploy Hook**. In GitHub **Settings → Secrets and variables → Actions → New repository secret**, save it as `RENDER_DEPLOY_HOOK_URL`. Never commit the URL.
4. Configure the H2 persistent disk, database password and real administrator on Render as described in [H2_DEPLOYMENT.md](H2_DEPLOYMENT.md) and [DATABASE_SETTINGS.md](DATABASE_SETTINGS.md). Application secrets stay in Render, not in the workflow.
5. Commit and push the workflow, blueprint and application changes to `main`.

Pull requests run formatting checks, tests, JAR packaging, a Docker build and an H2 HTTP health check. Pushes to main run the same checks then request deployment of that exact commit. You can also use Actions → Backend CI and Render deployment → Run workflow on main.

The deploy step confirms Render accepted the request; it does not wait for the live deployment to finish. Check Render's deployment logs and `/api/health` afterward. A failed workflow never triggers the deployment job. Render still builds/runs the application; GitHub Actions orchestrates verification and deployment.

If a workflow fails, inspect its failing step and the uploaded test report. Do not bypass checks by triggering a manual Render deploy of unverified changes.

## Local verification

```sh
bash ./gradlew spotlessCheck test bootJar
docker build -t retailflow .
docker run --rm -p 8080:8080 -v retailflow-data:/var/data --env-file .env retailflow
```

Use Java 21. A persistent volume is required for local Docker data retention; on Render, attach the persistent disk at `/var/data`.
