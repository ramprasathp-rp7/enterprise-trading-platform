# Watchlist module

Customer watchlists live in this package inside the Trade REST API (order-service). Price alerts are a sub-module in [`alerts/`](alerts/README.md).

The contract is `Frontend/trading-ui/contracts/watchlist-api.yaml`. The Angular client in `src/app/api/generated/watchlist` is generated from it with `npm run generate:watchlist`.

## Layout

| Package | Contents |
|---|---|
| `controller` | `WatchlistController`, routes under `/api/v1/accounts/{accountId}/watchlists` |
| `service` | `WatchlistService`, which holds the business rules |
| `mapper` | `WatchlistMapper` (MyBatis annotations) |
| `model` | `Watchlist`, `WatchlistItem` |
| `dto` | Request and response records |
| `exception` | `WatchlistNotFoundException` (WL-404), `WatchlistConflictException` (WL-409) |
| `alerts` | Price alerts, documented in [`alerts/README.md`](alerts/README.md) |

## Routes

| Method | Path | Result |
|---|---|---|
| GET | `/api/v1/accounts/{accountId}/watchlists` | 200, all lists with their items |
| POST | `/api/v1/accounts/{accountId}/watchlists` | 201 |
| GET | `/api/v1/accounts/{accountId}/watchlists/{watchlistId}` | 200 |
| DELETE | `/api/v1/accounts/{accountId}/watchlists/{watchlistId}` | 204 |
| POST | `/api/v1/accounts/{accountId}/watchlists/{watchlistId}/items` | 201 |
| DELETE | `/api/v1/accounts/{accountId}/watchlists/{watchlistId}/items/{watchlistItemId}` | 204 |

Every route calls `AccountAccessAuthorizer.assertTokenCanAccess(accountId)`. A token whose `accountId` claim does not match the path account gets ACC-403.

## Tables

`watchlists` and `watchlist_items` are created by `Database/postgres/order-service/migrations/013_create_watchlists.sql`.

- Names are unique per account, ignoring case.
- A symbol appears at most once per list.
- Deleting a list cascades to its items.

## Live prices

The watchlist API stores symbols only. The UI joins each symbol to its latest quote with `GET /api/v1/market-data/quotes/{symbol}/latest`, which is served from the order service's `MarketDataCache`.
