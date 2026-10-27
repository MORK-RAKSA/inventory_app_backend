# GitHub-only build workflow

`.github/workflows/backend.yml` tests, packages, and verifies the backend on pull requests and pushes to main. It can also run manually from Actions.

The workflow requires no Render deploy hook. It runs Gradle formatting checks and tests, builds a Docker image, checks H2 startup, and uploads the verified `app.jar` as an Actions artifact retained for 14 days. Download it from the successful workflow run under Artifacts.

GitHub Actions does not host the backend after the job ends. The Docker container used for verification is temporary and its database is discarded. Artifacts contain application code, not a production database. To serve mobile/web clients, run the JAR with Java 21 or the Docker image on a persistent host.

To activate this change, commit and push the updated workflow. Re-running an older run uses its old workflow and may still request the Render secret.

The existing render.yaml is an optional hosting configuration only; this workflow does not use it. For a future Render deployment see H2_DEPLOYMENT.md and DATABASE_SETTINGS.md.
