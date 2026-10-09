# Deployment verification (9 October 2026)

Evidence that the repository meets the deployment requirements in
[`deployment-rehearsal.md`](deployment-rehearsal.md). Each row names the requirement, where the evidence is,
the command that checks it, and the outcome. Commands run from the repository root in Git Bash unless noted.

## Results

| # | Requirement | Evidence | Command | Outcome |
|---|---|---|---|---|
| 1 | Required top-level folders exist and can be found | `Config/application.yaml`, `Contracts/*.yaml`, `Database/postgres/{auth-service,order-service}/{apply.sh,migrations,seed}`, `Database/duckdb/analytics/analytics-schema.sql`, `Infrastructure/Kafka/scripts/`, `Services/{auth,order,executor}-service`, `Frontend/trading-ui`, `ETL` | `for d in <paths>; do [ -e "$d" ] && echo OK $d \|\| echo MISSING $d; done` | **Pass**: 24 of 24 present |
| 2 | `.env` has exactly the keys of `.env.example` | `.env`, `.env.example`, `.example.env` | `diff <(keys .env) <(keys .env.example)` (`keys` lists `NAME=` lines, names only) | **Pass**: 39 = 39 for both templates, after the fixes below |
| 3 | No unresolved placeholders (`path/to`, `<your_…>`, `your_…_here`, `example.invalid`) in runtime env/config | `.env`, `Config/application.yaml`, both services' `application.properties`, auth `src/config/*.ts`, frontend `core/config/*.ts`, `playwright.config.ts`, ETL `config.py` | `grep -nEi 'path/to\|<your_\|your_[a-z0-9_]*_here\|example\.invalid' <files>` | **Pass**: none, after the fixes below. Placeholders remain only in the templates, by design |
| 4 | No hardcoded external URLs or secret fallbacks in runtime code, other than documented exceptions | Runtime `src/main`, auth `src`, frontend `src/app`, `scripts`, ETL `src` | `grep -rnEo 'https?://…'` and a search for secret defaults | **Pass**: no external hosts hardcoded. The `localhost` fallbacks, the order service's JWT secret fallback and the preferences development key are listed as accepted local-only exceptions in `deployment-rehearsal.md` §6 |
| 7a | Auth service up | `GET http://localhost:3000/docs/json` | `curl -s -o /dev/null -w "%{http_code}" …` | **Pass**: 200 |
| 7b | Order service healthy | `GET http://localhost:8081/health` | `curl -s http://localhost:8081/health` | **Pass**: `status: ok`, postgres ok, fauxnance ok (quota 2000) |
| 7c | Executor service running | Port 8082 answers; orders leave NEW | `curl http://localhost:8082/`; `select status, count(*) from orders where created_on > now() - interval '30 minutes' group by status` | **Pass**: port answers (no health endpoint, see gaps); all 3 smoke orders settled (REJECTED: limit below the ask), none stuck at NEW |
| 7d | UI served | `GET http://localhost:4200/` | `curl …` | **Pass**: 200 |
| 7e | Login flow with config-driven URLs | `e2e/sign-in.spec.ts`, `e2e/login.spec.ts` (URLs from `Config/application.yaml` via `playwright.config.ts`) | `npx playwright test e2e/sign-in.spec.ts e2e/login.spec.ts --project=chromium` | **Pass**: 11 of 11 |
| 7f | Order flow with config-driven URLs | `e2e/place-order.spec.ts`, `e2e/blotter.spec.ts` | `npx playwright test e2e/place-order.spec.ts e2e/blotter.spec.ts --project=chromium` | **Pass**: place-order 6 of 6; blotter 5 passed, 1 skipped (refresh test runs only while an order is NEW) |

## Fixes made during verification

| Item | Problem | Fix |
|---|---|---|
| 2 | `.env` had `E2E_BASE_URL`, `E2E_AUTH_API`, `E2E_TRADE_API`, `PORT` and `KAFKA_BOOTSTRAP_SERVERS` that the templates did not; the templates had `PREFERENCES_CONTACT_KEY` that `.env` did not | Templates list `PORT` and the `E2E_*` URLs as empty optional overrides (empty falls back to the shared config). `.env` gained an empty `PREFERENCES_CONTACT_KEY` (same behaviour as unset: the published development key, so stored contact details stay readable). `KAFKA_BOOTSTRAP_SERVERS` is commented out in `.env`: nothing reads it since the shared-config change |
| 3 | `order-service/application.properties` defaulted the database password to `<your_password_here>`; `executor-service/application.properties` defaulted `fauxnance.base-url` to `https://fauxnance.example.invalid` | Both are now required (`${PGPASSWORD}`, `${FAUXNANCE_BASE_URL}`), so a missing value stops the service at startup instead of failing at the first query or quote |
| 4 | `environment.production.ts` hardcoded `https://api.trading-platform.example.com/{auth,trade}` | The production build takes its URLs from `Config/application.yaml` like every other build; set `services.auth` and `services.order` there for the target environment |
| 4 | The localhost fallbacks were not documented | Listed in `deployment-rehearsal.md` §6 with their code points |
| 7f | The order-ticket validation journey assumed empty fields, but the E2E account's default-quantity preference prefills the quantity | The test clears quantity and price before checking validation |

## Deployment readiness follow-up (same day)

| Item | Result |
|---|---|
| `Database/schema.sql` and `Database/seed_data.sql` | Generated by `Database/generate-sql.sh` from every migration and seed file; run on an empty database with `psql -v ON_ERROR_STOP=1`: no errors, the same 24 tables as the live database, all seed data loaded |
| Every environment variable listed | `.env`, `.env.example` and `.example.env` hold the same 74 keys, covering every setting the services, the ETL and the frontend config read |
| Hardcoded secrets removed | The order service's JWT secret fallback and the preferences development key are gone: `JWT_SECRET`, `JWT_ISSUER` and `PREFERENCES_CONTACT_KEY` are required at startup |
| Hardcoded hosts and URLs removed | Database host/port/name, Kafka host/port, service ports and URLs, the CORS origin and the Fauxnance URL all come from `Config/application.yaml` or `.env`; a missing value is a startup or build error |
| Fauxnance API URL in the config folder | `fauxnance.url` in `Config/application.yaml`, overridable with `FAUXNANCE_BASE_URL` |
| Generated test output | `playwright-report/` and `test-results/` untracked and ignored |
| Fresh clone of `main` builds | order-service, executor-service and frontend built; auth-service failed on `main` (lock file missing `yaml`), fixed locally, to be merged |
| Tests after the changes | order-service 704, executor-service 186, auth-service 142 (unit), frontend 832, all passing; production frontend build passes |

## Remaining gaps

- **The executor has no health endpoint.** Its health shows only through orders leaving NEW.
- **The services must be restarted** to run with the required-value configuration; unit tests do not start
  the Spring context, so the first restart is the check that every required value is present.
