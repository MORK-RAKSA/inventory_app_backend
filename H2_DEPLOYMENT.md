# Persistent H2 deployment

The Spring backend now defaults to file-backed H2, including the prod profile. Flyway uses db/h2; Hibernate validates the schema. Local default: ./data/retailflow.mv.db. Docker default: /var/data/retailflow.mv.db.

For the existing Render service:
1. Attach a persistent disk mounted at /var/data (requires paid compute). The blueprint declares starter compute and a 1 GB disk; applying it incurs Render charges. No hosting changes have been made automatically.
2. Set H2_DATABASE_PATH=/var/data/retailflow, H2_USER=sa and a strong H2_PASSWORD. Keep the password stable after database creation.
3. Remove obsolete DB_URL, DB_USER, DB_PASSWORD, DB_SCHEMA and any SPRING_DATASOURCE_* or SPRING_FLYWAY_* overrides pointing to Prisma. Deploy the updated backend repository.
4. Verify /api/health reports UP and H2. Use a single service instance.

H2 starts with a fresh database and the existing H2 migration seeds. Prisma products, sales and accounts have NOT been migrated. Keep the old database for a later export/import. Existing hardcoded seed-account behavior remains and should be replaced before public use.

Without the persistent disk, Render can erase the database on restart or redeploy. Stop the application before copying the H2 file for a consistent backup; never run two backend processes against the same file.
