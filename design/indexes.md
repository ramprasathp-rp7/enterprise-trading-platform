## Index Justifications

### 1. `ix_orders_account_open_created_on`
```sql
CREATE INDEX ix_orders_account_open_created_on 
    ON orders (account_id, created_on DESC) 
    WHERE status = 'NEW';
```
* **Target Query (Q1):** Active orders for an account, newest first.
* **Justification:** Composite leading column `account_id` provides exact lookup. `created_on DESC` avoids in-memory sorting. The partial filter (`WHERE status = 'NEW'`) ensures the index remains small and fast, as orders rapidly transition out of `NEW` to terminal states.

### 2. `ix_orders_account_created_on`
```sql
CREATE INDEX ix_orders_account_created_on 
    ON orders (account_id, created_on DESC);
```
* **Target Query (Q2):** Last 50 orders for an account across any state.
* **Justification:** Combines filtering by `account_id` and sorting by `created_on DESC` in a single B-Tree, allowing PostgreSQL to satisfy `ORDER BY ... LIMIT 50` via a fast index scan without full-set sorting.

### 3. `ix_orders_created_on`
```sql
CREATE INDEX ix_orders_created_on 
    ON orders (created_on);
```
* **Target Query (Q4):** Nightly extract of all orders created since a given timestamp.
* **Justification:** Unscoped query across all accounts. Because `ix_orders_account_created_on` starts with `account_id`, it cannot serve global date-range scans. A single-column index on `created_on` allows efficient range scans.

---