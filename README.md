# RetailFlow backend — Spring Boot 3.5

Spring MVC (not WebFlux), Java 21, Gradle Groovy DSL, file-backed H2, Flyway and `application.yml`.

## Source packages
- `controller`: HTTP endpoints and bean validation
- `service`: transactional business logic
- `dto`: request models
- `util`: reusable money/currency conversions
- `config`: CORS
- `globalExceptional`: global exception handling (named as requested)
- `product`, `sales`, `settings`: JPA entities and repositories

API routes remain `/api/products`, `/api/sales`, and `/api/settings`.

## Run
No PostgreSQL server is required. With JDK 21, start the backend from this `backend` directory (so the relative `data` folder is stable):

```bash
./gradlew bootRun
```

The starter uses local development DB defaults: do not use these credentials in production.

**Reference:** https://github.com/MORK-RAKSA/wft-core — treated as a utility/architecture inspiration, not imported as an unverified binary dependency. The reference repository targets older Spring dependencies and Java 17; its code has not been copied.

**Note:** build/test still require a working Gradle environment and dependency downloads; no runtime verification is claimed.


## API changes in this revision
| Endpoint | Notes |
|---|---|
| `POST /api/inventory/batches/commit` | **Atomic batch save.** Body `{items:[{productId,quantityAdded} \| {name,barcode,category,price,currency,stock,lowStockThreshold}]}`. One transaction: any invalid line rolls everything back. A "new" line whose barcode already exists becomes a restock; the same new barcode twice is summed. Returns `{created, restocked, products}`. |
| `PUT /api/products/{id}` | Now **applies `stock`** (difference logged in `stock_movements` as `MANUAL_EDIT`), takes a row lock, and never wipes the barcode. A barcode can only be set if the product has none. |
| `DELETE /api/products/{id}` | Now returns **204 No Content**. (An empty `200` made the app's `response.json()` throw `JsonParseError`.) |
| `POST /api/products/{id}/image` | multipart, part name `file` (JPEG/PNG/WebP ≤ 5 MB, real format sniffed). Stores in Cloudflare R2, saves `imageUrl` on the product. **501** when R2 is not configured. |
| `DELETE /api/products/{id}/image` | Removes the image. |

Errors are always JSON `{error, code, status, path, timestamp}` with proper statuses (400 validation / malformed JSON, 404, 409 duplicate barcode or stale data, 413 big file).

Migration `V2__batch_images.sql` adds `image_url`/`image_key` and makes the barcode unique **only among active products**, so an archived item no longer blocks re-adding its barcode.

## Cloudflare R2 images (optional)
1. R2 → create a bucket; enable the public `r2.dev` URL (or attach a custom domain).
2. R2 → Manage API tokens → token with *Object Read & Write* on that bucket.
3. Set `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET`, `R2_PUBLIC_URL` (see `.env.example`). If any is empty, storage stays off and the app keeps photos on the phone.

The AWS SDK is pinned to 2.28.x on purpose: 2.30+ adds default checksums that R2 rejects.

`gradle build` runs Spotless; if formatting fails run `gradle spotlessApply`.

## Persistent local database
The default data source is a file-backed H2 database at `backend/data/retailflow.mv.db` (relative to the working directory used to start Spring Boot). Flyway applies `classpath:db/h2` migrations on first run. Do not delete the `data/` directory; it contains your inventory, transactions, settings, and authentication sessions. For backups stop the server and copy the `data/` directory to a safe location.

**Important:** Switching from an existing PostgreSQL installation does not automatically copy existing records. Export/migrate them first. Product photos are **not** stored in H2: they remain on the device unless optional Cloudflare R2 is configured. H2 file databases are intended for one backend process, not multiple independent servers sharing the same database file.
