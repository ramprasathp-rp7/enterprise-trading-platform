# Trading UI (Angular)

The browser app for customers and admins. Angular 21 standalone components with signals; every backend call
goes through a client generated from an OpenAPI contract.

Dev server: **http://localhost:4200** (from `Config/application.yaml`, `frontend.port`).

## What it contains

| Folder (`src/app/…`) | What it does |
|---|---|
| `features/dashboard` | Cash, portfolio value, net-worth chart, holdings and watchlist cards |
| `features/order-ticket` | Placing limit orders: instrument search, live price, fees, budget warning |
| `features/blotter` | Order history with status badges, filters and cancelling NEW orders |
| `features/positions` | Holdings with live prices, P&L and filters |
| `features/instruments` | Every instrument with market data, filters, sorting, Buy/Sell and symbol discovery |
| `features/watchlist` | Watchlists with live prices, sorting and filters, and price alerts |
| `features/notifications` | Notification history with a detail panel for each notification |
| `features/settings` | Preferences: theme, order budget, market, default quantity, alert channels |
| `features/strategies`, `features/advice` | Automated strategies and buy/sell/hold signals |
| `features/transactions`, `features/account` | Deposits, withdrawals, statements |
| `features/admin` | Analytics, fee management, user registration |
| `features/sign-in` | Login and registration pages |
| `core` | Auth (tokens, guards, interceptor, refresh), notifications bell, live quotes, theme, account settings |
| `layout` | Customer and admin shells, navbar, sidebars, footer |
| `shared` | Currency formatting, statement PDF, motion directives (price flash, panel fade) |
| `api/generated` | Generated clients, one per contract in [`contracts/`](contracts/) |

## Setup

### Prerequisites

- Node.js 20.19+, 22.12+ or 24+, and npm 10+
- Java 11+ only if you regenerate API clients (the OpenAPI generator runs on the JVM)
- The backend running: auth-service (3000), order-service (8081) and executor-service (8082)

### Install and run

```bash
cd Frontend/trading-ui
npm ci
npm start          # syncs the platform config, then serves on http://localhost:4200
```

`npm start`, `npm run build` and `npm test` first run `npm run sync-config`, which writes
`src/app/core/config/platform-config.generated.ts` from `Config/application.yaml`. After changing a
port in that file, run `npm run sync-config` (or just `npm start`) again.

### Build

```bash
npm run build      # production build into dist/
```

### Regenerating API clients

The clients in `src/app/api/generated` are generated, committed and never edited by hand. After changing a
contract in `contracts/`:

```bash
npm run generate:<name>   # e.g. generate:instruments, generate:account-settings
npm run generate          # every client
```

## Tests

```bash
npm test                  # unit tests (Vitest), watch mode
npx ng test --watch=false # unit tests once
npm run test:coverage     # unit tests with coverage (HTML in coverage/sprint-09-trading-ui/)
npm run e2e               # Playwright journeys; needs the full backend running
```

The coverage thresholds in `angular.json` fail the run below 80%. See
[`docs/E2E_TESTING.md`](docs/E2E_TESTING.md) for the end-to-end journeys and
[`Documents/003_notes_on_testing_and_coverage.md`](../../Documents/003_notes_on_testing_and_coverage.md) for
every service's test and coverage commands.
