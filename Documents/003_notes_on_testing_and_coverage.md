# Testing and test coverage

How to run the tests and coverage reports for every part of the platform, where the reports end up, and what
the coverage is today. Commands are run from the repository root unless stated otherwise. Where Windows `cmd`
needs a different form, both are shown.

## Coverage today (9 October 2026)

| Service | Tests | Lines | Statements / instructions | Branches | Tool |
|---|---|---|---|---|---|
| Frontend (`Frontend/trading-ui`) | 832 | 96.8% | 96.2% statements | 89.5% | Vitest + V8 |
| auth-service | 138 unit | 99.8% | 99.8% statements | 91.1% | Jest |
| order-service | 704 | 96.3% | 96.5% instructions | 80.1% | JUnit 5 + JaCoCo |
| executor-service | 187 | 96.8% | 96.6% instructions | 91.1% | JUnit 5 + JaCoCo |

Line coverage is the headline figure: the share of executable source lines at least one test ran. Branch
coverage (both sides of every `if`, `?:` and `switch`) is lower everywhere and is the next thing to raise.

## Before you start

- **Unit tests need nothing running.** They use mocks and in-memory fakes: no database, Kafka or Fauxnance.
- **Integration and end-to-end tests need the platform running** (PostgreSQL, Kafka, the three services and,
  for E2E, the UI). They are called out below.
- The services read `../../.env` and `../../Config/application.yaml`. Unit tests do not need either.

## Frontend (Angular)

```bash
cd Frontend/trading-ui
npm ci                          # first time, or after package.json changes
npx ng test --watch=false       # run every spec once
npm test                        # watch mode while developing
npm run test:coverage           # every spec once, with coverage
```

Run one spec file:

```bash
npx ng test --watch=false --include src/app/features/instruments/pages/instruments/instruments.component.spec.ts
```

Coverage report:

- Summary in the terminal at the end of the run
- HTML: `Frontend/trading-ui/coverage/sprint-09-trading-ui/index.html`
- `lcov.info` and `coverage-final.json` beside it, for CI tools

Settings are in `angular.json` under `test.options`: coverage covers `src/app/**/*.ts`, excludes the generated
clients (`src/app/api/generated/**`) and the specs, and **fails the run below 80%** for statements, branches,
functions or lines.

`npm test` and `npm run test:coverage` first run `npm run sync-config`, which needs the `yaml` package. If it
reports `Cannot find package 'yaml'`, run `npm install`.

### End-to-end (Playwright)

Needs the UI, auth-service, order-service, executor-service, PostgreSQL and Kafka running. URLs default to
`Config/application.yaml`; `E2E_BASE_URL`, `E2E_AUTH_API` and `E2E_TRADE_API` override them.

```bash
cd Frontend/trading-ui
npx playwright install              # first time only: Chromium, Firefox and WebKit
npm run e2e                         # headless
npm run e2e:ui                      # interactive runner
```

Every journey runs in Chromium, Firefox and WebKit. A failure saying `Executable doesn't exist at
...ms-playwright/firefox-...` means that browser is not installed; run `npx playwright install`. To run one
browser only: `npx playwright test --project=chromium`.

The journeys and their data are described in `Frontend/trading-ui/docs/E2E_TESTING.md`.

## auth-service (NestJS)

```bash
cd Services/auth-service
npm ci
npx jest --testPathIgnorePatterns integration                 # unit specs only
npx jest --testPathIgnorePatterns integration --coverage      # unit specs with coverage
npm run test:cov                                               # everything, with coverage
```

Coverage report: `Services/auth-service/coverage/lcov-report/index.html` (plus `lcov.info`,
`coverage-summary.json` and `clover.xml` in `coverage/`).

**`src/auth/auth.integration.spec.ts` is an integration test.** It registers a real user against the
auth-service on port 3000 and calls the Trade API on 8081, so it fails unless both are running, and it
writes to the auth database. `npm test` and `npm run test:cov` include it; use `--testPathIgnorePatterns
integration` when you only want unit tests. Its login step fails with 401 when account 6 already has a
user from an earlier run.

## order-service (Spring Boot)

```bash
cd Services/order-service
mvn test                                   # every test, then the JaCoCo report
mvn test -Dtest=PriceAlertServiceTest      # one class
mvn test -Dtest='Fauxnance*'               # a pattern
```

Windows `cmd` needs the pattern quoted with double quotes: `mvn test -Dtest="Fauxnance*"`.

Coverage report (JaCoCo runs in the `test` phase, so `mvn test` is enough):

- HTML: `Services/order-service/target/site/jacoco/index.html`
- `jacoco.csv` and `jacoco.xml` beside it

The tests use Mockito, MockMvc in standalone mode and hand-written fakes for the MyBatis mappers (for example
`FakePriceAlertStore`). None of them start the Spring context, so a wiring mistake such as a bean with two
unannotated constructors is **not** caught by `mvn test`; start the service (`mvn spring-boot:run`) after
changing constructors or configuration classes.

## executor-service (Spring Boot)

```bash
cd Services/executor-service
mvn test           # every test, then the JaCoCo report
mvn verify         # the same, plus packaging
```

Coverage report: `Services/executor-service/target/site/jacoco/index.html`.

SonarQube analysis for this service is described in `002_notes_on_SonarQube_quality_gate.md`.

## ETL (Python)

```bash
cd ETL
python -m venv .venv
.venv/bin/pip install -e ".[dev]"                     # Windows: .venv\Scripts\pip install -e ".[dev]"
.venv/bin/python -m pytest                            # Windows: .venv\Scripts\python -m pytest
.venv/bin/python -m pytest --cov=trading_analytics_etl --cov-report=html
```

The HTML report is written to `ETL/htmlcov/index.html`.

## Running everything

From the repository root, in Git Bash:

```bash
(cd Frontend/trading-ui && npx ng test --watch=false --coverage) &&
(cd Services/auth-service && npx jest --testPathIgnorePatterns integration --coverage) &&
(cd Services/order-service && mvn -q test) &&
(cd Services/executor-service && mvn -q test)
```

## Finding what is not covered

- **Frontend and auth-service:** open the HTML report and sort by "Uncovered lines", or read `lcov.info`
  (`DA:<line>,0` marks an uncovered line).
- **Java services:** open `target/site/jacoco/index.html`, drill into a package and class; red lines were
  never run, yellow lines had a branch that was never taken. `jacoco.csv` lists missed lines per class.

## Writing tests that count

- Test behaviour through the public API. Coverage that only executes code without asserting on the result
  hides regressions instead of catching them.
- A class that only delegates (most controllers) is covered by calling it with mocked services and checking
  the response and the access check (see `DelegatingControllersTest`, `AccountScopedControllersTest`).
- Exception mapping is table-driven in `GlobalExceptionHandlerMappingTest`: adding a domain exception means
  adding a row there.
- For Angular components, prefer stub stores built from signals (see `price-alerts-panel.component.spec.ts`)
  over the real HTTP stack.
- Startup code is kept thin so it can be tested: auth-service's `main.ts` exports `configureApp` and
  `bootstrap`, and only starts a server when run directly.
