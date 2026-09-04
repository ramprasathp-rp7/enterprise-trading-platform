
# Index Justifications & Query Plans

## Why Indexes Matter

Indexes let Postgres jump straight to matching rows instead of scanning the whole table, and can also pre-sort data so `ORDER BY` / `LIMIT` avoid an in-memory sort. The tradeoffs: each index adds storage overhead and slows down writes (insert/update/delete), so indexes should be targeted at real, frequent query patterns rather than added speculatively. Partial indexes (like `WHERE status = 'NEW'`) keep the index small by only covering the subset of roaddws actually queried often.

---

## 1. `ix_orders_account_open_created_on`
```sql
CREATE INDEX ix_orders_account_open_created_on 
    ON orders (account_id, created_on DESC) 
    WHERE status = 'NEW';
```
**Target Query (Q1):** Active orders for an account, newest first.

**Justification:** Composite leading column `account_id` provides exact lookup. `created_on DESC` avoids in-memory sorting. The partial filter (`WHERE status = 'NEW'`) keeps the index small and fast, since orders rapidly transition out of `NEW` to terminal states.

**EXPLAIN ANALYZE:**
```
Index Scan using ix_orders_account_open_created_on on orders  (cost=0.12..8.14 rows=1 width=368) (actual time=0.033..0.033 rows=0.00 loops=1)
  Index Cond: (account_id = 6)
  Index Searches: 1
  Buffers: shared hit=5
Planning:
  Buffers: shared hit=165 read=28 dirtied=1
Planning Time: 45.037 ms
Execution Time: 4.265 ms
```

---

## 2. `ix_orders_account_created_on`
```sql
CREATE INDEX ix_orders_account_created_on 
    ON orders (account_id, created_on DESC);
```
**Target Query (Q2):** Last 50 orders for an account across any state.

**Justification:** Combines filtering by `account_id` and sorting by `created_on DESC` in a single B-Tree, allowing Postgres to satisfy `ORDER BY ... LIMIT 50` via a fast index scan without a full-set sort.

**EXPLAIN ANALYZE:**
```
Limit  (cost=0.14..8.16 rows=1 width=368) (actual time=0.007..0.007 rows=0.00 loops=1)
  Buffers: shared hit=2
  ->  Index Scan using ix_orders_account_created_on on orders  (cost=0.14..8.16 rows=1 width=368) (actual time=0.003..0.003 rows=0.00 loops=1)
        Index Cond: (account_id = 6)
        Index Searches: 1
        Buffers: shared hit=2
Planning:
  Buffers: shared hit=5
Planning Time: 0.123 ms
Execution Time: 0.025 ms
```

**Also used by the window-function query (top order per instrument, filtered on `status = 'FILLED'`):**
```
WindowAgg  (cost=8.22..8.25 rows=1 width=128) (actual time=0.763..0.764 rows=0.00 loops=1)
  Window: w2 AS (ORDER BY created_on ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
  Buffers: shared hit=11
  ->  Sort  (cost=8.22..8.22 rows=1 width=96) (actual time=0.760..0.761 rows=0.00 loops=1)
        Sort Key: created_on
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared hit=11
        ->  WindowAgg  (cost=8.18..8.21 rows=1 width=96) (actual time=0.033..0.033 rows=0.00 loops=1)
              Window: w1 AS (PARTITION BY instrument_id ORDER BY (((quantity)::numeric * executed_price)) ROWS UNBOUNDED PRECEDING)
              Buffers: shared hit=8
              ->  Sort  (cost=8.18..8.19 rows=1 width=88) (actual time=0.030..0.030 rows=0.00 loops=1)
                    Sort Key: instrument_id, (((quantity)::numeric * executed_price)) DESC
                    Sort Method: quicksort  Memory: 25kB
                    Buffers: shared hit=8
                    ->  Index Scan using ix_orders_account_created_on on orders  (cost=0.14..8.17 rows=1 width=88) (actual time=0.004..0.004 rows=0.00 loops=1)
                          Index Cond: (account_id = 6)
                          Filter: ((status)::text = 'FILLED'::text)
                          Index Searches: 1
                          Buffers: shared hit=2
Planning:
  Buffers: shared hit=27
Planning Time: 0.828 ms
Execution Time: 2.635 ms
```

---

## 3. `ix_orders_created_on`
```sql
CREATE INDEX ix_orders_created_on 
    ON orders (created_on);
```
**Target Query (Q4):** Nightly extract of all orders created since a given timestamp.

**Justification:** Unscoped query across all accounts. Because `ix_orders_account_created_on` starts with `account_id`, it cannot serve global date-range scans. A single-column index on `created_on` allows efficient range scans.

**EXPLAIN ANALYZE** (on this small test dataset the planner still chose a seq scan; the index pays off as table size grows):
```
Seq Scan on orders  (cost=0.00..12.50 rows=67 width=368) (actual time=0.019..0.019 rows=0.00 loops=1)
  Filter: (created_on >= '2026-03-10 08:05:01+00'::timestamp with time zone)
Planning:
  Buffers: shared hit=8
Planning Time: 0.158 ms
Execution Time: 0.700 ms
```

---

## Supporting Plans (Existing Indexes)

**`positions_pkey`** — account position lookup:
```
Bitmap Heap Scan on positions  (cost=4.19..12.66 rows=5 width=40) (actual time=0.647..0.647 rows=0.00 loops=1)
  Recheck Cond: (account_id = 6)
  Buffers: shared hit=2
  ->  Bitmap Index Scan on positions_pkey  (cost=0.00..4.19 rows=5 width=0) (actual time=0.005..0.005 rows=0.00 loops=1)
        Index Cond: (account_id = 6)
        Index Searches: 1
        Buffers: shared hit=2
Planning:
  Buffers: shared hit=28 read=1 dirtied=1
Planning Time: 2.169 ms
Execution Time: 1.354 ms
```

**`uq_accounts_account_id`** — account lookup by external account code:
```
Index Scan using uq_accounts_account_id on accounts  (cost=0.14..8.16 rows=1 width=442) (actual time=1.223..1.224 rows=1.00 loops=1)
  Index Cond: ((account_id)::text = 'ACC-000006'::text)
  Index Searches: 1
  Buffers: shared read=2 dirtied=1
Planning:
  Buffers: shared hit=44 read=2
Planning Time: 5.492 ms
Execution Time: 1.239 ms
```
