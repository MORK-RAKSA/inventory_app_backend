# Database-owned business settings

Exchange rate, currency mode (manual/live), category lists and base-unit lists live in store_settings. UOM templates live in uom_configs. Products, users, passwords and sessions remain database-owned. Frontend category/unit writes show success only after the server saves; empty lists remain empty. Login no longer creates default accounts or accepts fallback credentials.

Historical Flyway migrations are retained for checksum compatibility. V12 adds persisted currency mode. V13 disables only untouched historical demo credentials and removes unchanged bundled UOM templates on empty catalogs. Existing products, sales and customized accounts remain. A previously configured exchange rate stays in the database.

Before deployment, configure BOOTSTRAP_ADMIN_USERNAME, BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_FULL_NAME and BOOTSTRAP_ADMIN_PASSWORD (at least 12 characters) for your own administrator if no active administrator remains. Choose a new username/email rather than the historical demo accounts. Bootstrap writes this account to the database; subsequent authentication uses the stored account.

Legacy api/index.js files now forward to Spring and contain no independent data store or built-in credentials. If deploying those gateways, set BACKEND_ORIGIN to your Spring origin (scheme and host, without /api); never point a gateway at itself. The root Vercel rewrite can continue forwarding directly to Spring.

Deployment connection secrets, Expo update configuration, UI translations/styles, validation rules, device auth tokens and local activity caches are not business seed data and remain outside store_settings. Language remains a device preference. Product packaging is stored per product. New UOM templates require explicit names and quantities; missing case quantities no longer imply a 24-unit case.
