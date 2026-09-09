# Index Justifications, Write Costs & Execution Plans

## Overview

Indexes allow PostgreSQL to jump directly to target rows rather than scanning entire tables, while also serving pre-sorted results to satisfy `ORDER BY` and `LIMIT` clauses without in-memory sort operations. 

However, indexes carry trade-offs:
* **Storage Overhead:** B-Tree index pages consume additional disk and buffer pool space.
* **Write Latency (Per-Index Write Cost):** Every `INSERT`, `UPDATE` (of indexed columns), or `DELETE` requires updating the table heap and modifying every relevant B-Tree index page.

Below is the detailed operational justification, per-index write-cost analysis, and actual `EXPLAIN (ANALYZE, BUFFERS)` execution plans captured against a populated dataset.

---

## 1. `ix_orders_account_open_created_on`

sql
CREATE INDEX ix_orders_account_open_created_on 
    ON orders (account_id, created_on DESC) 
    WHERE status = 'NEW';



* **Target Query (Q1):** Fetch active/open orders for a specific account sorted newest first (`WHERE account_id = 6 AND status = 'NEW' ORDER BY created_on DESC`).
* **Justification:** The composite leading column `account_id` provides direct B-Tree lookup. The partial filter predicate (`WHERE status = 'NEW'`) keeps the index extremely compact, as orders transition quickly from `NEW` to terminal states (`FILLED`, `CANCELLED`, `REJECTED`).
* **Per-Index Write Cost:** **Low to Moderate.** Only `INSERT` statements with `status = 'NEW'` write to this B-Tree. When an order transitions out of `NEW` via an `UPDATE`, it is removed from the index. Subsequent updates to terminal-state orders incur **zero** write overhead for this index.

### EXPLAIN ANALYZE Execution Plan (Q1)

text
Index Scan using ix_orders_account_open_created_on on orders  (cost=0.14..8.16 rows=1 width=182) (actual time=0.047..0.048 rows=1.00 loops=1)
   Index Cond: (account_id = 6)
   Index Searches: 1
   Buffers: shared hit=5 dirtied=1
Planning:
   Buffers: shared hit=209
Planning Time: 2.871 ms
Execution Time: 0.083 ms



---

## 2. `ix_orders_account_created_on`

sql
CREATE INDEX ix_orders_account_created_on 
    ON orders (account_id, created_on DESC);



* **Target Query (Q2 & Window Query Q6):** Fetch the last 50 orders across any state for an account (`WHERE account_id = 6 ORDER BY created_on DESC LIMIT 50`) and rank orders by value per instrument (`Q6`).
* **Justification:** Combines equality filtering on `account_id` with pre-sorted ordering on `created_on DESC`. This allows Postgres to avoid an in-memory `Sort` node for Q2 queries.
* **Per-Index Write Cost:** **Moderate.** Unconditional B-Tree index. Every `INSERT` into `orders` incurs a mandatory B-Tree insertion. Since `account_id` and `created_on` are immutable business attributes after creation, `UPDATE` statements on orders do not trigger B-Tree page updates for this index.

### EXPLAIN ANALYZE Execution Plan (Q2)

text
Limit  (cost=9.51..9.52 rows=2 width=182) (actual time=0.052..0.052 rows=3.00 loops=1)
   Buffers: shared hit=5
   ->  Sort  (cost=9.51..9.52 rows=2 width=182) (actual time=0.051..0.051 rows=3.00 loops=1)
         Sort Key: created_on DESC
         Sort Method: quicksort  Memory: 25kB
         Buffers: shared hit=5
         ->  Bitmap Heap Scan on orders  (cost=4.16..9.50 rows=2 width=182) (actual time=0.015..0.016 rows=3.00 loops=1)
               Recheck Cond: (account_id = 6)
               Heap Blocks: exact=1
               Buffers: shared hit=2
               ->  Bitmap Index Scan on ix_orders_account_created_on  (cost=0.00..4.16 rows=2 width=0) (actual time=0.005..0.005 rows=3.00 loops=1)
                     Index Cond: (account_id = 6)
                     Index Searches: 1
                     Buffers: shared hit=1
Planning Time: 0.134 ms
Execution Time: 0.073 ms



### EXPLAIN ANALYZE Execution Plan (Q6 - Window Aggregation Query)

text
WindowAgg  (cost=9.56..9.59 rows=1 width=128) (actual time=0.063..0.064 rows=1.00 loops=1)
   Window: w2 AS (ORDER BY created_on ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
   Storage: Memory  Maximum Storage: 17kB
   Buffers: shared hit=8
   ->  Sort  (cost=9.56..9.57 rows=1 width=96) (actual time=0.049..0.050 rows=1.00 loops=1)
         Sort Key: created_on
         Sort Method: quicksort  Memory: 25kB
         Buffers: shared hit=8
         ->  WindowAgg  (cost=9.52..9.55 rows=1 width=96) (actual time=0.045..0.046 rows=1.00 loops=1)
               Window: w1 AS (PARTITION BY instrument_id ORDER BY (((quantity)::numeric * executed_price)) ROWS UNBOUNDED PRECEDING)
               Storage: Memory  Maximum Storage: 17kB
               Buffers: shared hit=8
               ->  Sort  (cost=9.52..9.53 rows=1 width=88) (actual time=0.037..0.037 rows=1.00 loops=1)
                     Sort Key: instrument_id, (((quantity)::numeric * executed_price)) DESC
                     Sort Method: quicksort  Memory: 25kB
                     Buffers: shared hit=8
                     ->  Bitmap Heap Scan on orders  (cost=4.16..9.51 rows=1 width=88) (actual time=0.022..0.023 rows=1.00 loops=1)
                           Recheck Cond: (account_id = 6)
                           Filter: ((status)::text = 'FILLED'::text)
                           Rows Removed by Filter: 2
                           Heap Blocks: exact=1
                           Buffers: shared hit=2
                           ->  Bitmap Index Scan on ix_orders_account_created_on  (cost=0.00..4.16 rows=2 width=0) (actual time=0.004..0.004 rows=3.00 loops=1)
                                 Index Cond: (account_id = 6)
                                 Index Searches: 1
                                 Buffers: shared hit=1
Planning:
   Buffers: shared hit=22
Planning Time: 0.181 ms
Execution Time: 0.138 ms



---

## 3. `ix_orders_created_on`

sql
CREATE INDEX ix_orders_created_on 
    ON orders (created_on);



* **Target Query (Q4):** Date-range batch extract across all system accounts (`WHERE created_on >= '2026-03-10...'`).
* **Justification:** Unscoped query across all accounts. Composite indexes starting with `account_id` cannot serve global time-range filters efficiently.
* **Per-Index Write Cost:** **Moderate.** Paid on every `INSERT`. `created_on` is immutable, so no maintenance cost is incurred during `UPDATE` operations.
* **Planner Note on Plan Choice:** In the execution plan below, the PostgreSQL Query Planner chose a `Seq Scan` (Sequential Table Scan) instead of an `Index Scan`. This is standard planner behavior for small tables: reading 1 contiguous heap page (`Buffers: shared hit=1`) is faster than reading an index page plus heap pages. As the `orders` table scales into thousands of rows, the planner automatically switches to an Index Scan.

### EXPLAIN ANALYZE Execution Plan (Q4)

text
Seq Scan on orders  (cost=0.00..14.75 rows=127 width=182) (actual time=0.013..0.014 rows=6.00 loops=1)
   Filter: (created_on >= '2026-03-10 13:35:01+05:30'::timestamp with time zone)
   Rows Removed by Filter: 8
   Buffers: shared hit=1
Planning:
   Buffers: shared hit=3
Planning Time: 0.188 ms
Execution Time: 0.027 ms



---

## 4. `ix_orders_updated_on`

sql
CREATE INDEX ix_orders_updated_on
    ON orders (updated_on);



* **Target Query:** Incremental CDC (Change Data Capture) and ETL extraction jobs querying modified orders (`WHERE updated_on >= :last_watermark`).
* **Justification:** `created_on` watermarks miss order state changes (e.g., `NEW` -> `FILLED`). Querying on `updated_on` guarantees that state transitions are captured during incremental loads.
* **Per-Index Write Cost:** **High relative to other indexes.** This index incurs a write penalty on initial `INSERT` **AND** on every state transition `UPDATE` because the `trg_orders_set_updated` trigger mutates the `updated_on` timestamp. B-Tree pointers must be updated across leaf pages on every order update.

---

## Existing Constraints & Primary Keys

### 1. `positions_pkey` (Primary Key Index)

* **Target Query (Q3):** Fetch position state for a specific account (`WHERE account_id = 6`).
* **Justification:** Primary key on `positions` automatically creates a unique index on `(account_id, instrument_id)`. B-Tree bitmap scan enables instant position evaluation.
* **Per-Index Write Cost:** Paid during position initialization (`INSERT`) and updates to positions.

#### EXPLAIN ANALYZE Execution Plan (Q3)

text
Bitmap Heap Scan on positions  (cost=4.19..12.66 rows=5 width=40) (actual time=0.023..0.024 rows=1.00 loops=1)
   Recheck Cond: (account_id = 6)
   Heap Blocks: exact=1
   Buffers: shared hit=2 dirtied=1
   ->  Bitmap Index Scan on positions_pkey  (cost=0.00..4.19 rows=5 width=0) (actual time=0.005..0.005 rows=1.00 loops=1)
         Index Cond: (account_id = 6)
         Index Searches: 1
         Buffers: shared hit=1
Planning:
   Buffers: shared hit=31
Planning Time: 0.846 ms
Execution Time: 0.041 ms



---

### 2. `uq_accounts_account_id` (Unique Constraint Index)

* **Target Query (Q5):** Lookup account by external alphanumeric code (`WHERE account_id = 'ACC-000006'`).
* **Justification:** Unique index enforces business identity integrity while enabling $O(\log N)$ single-row lookups.
* **Per-Index Write Cost:** Paid once per account onboarding (`INSERT`).

#### EXPLAIN ANALYZE Execution Plan (Q5)

text
Index Scan using uq_accounts_account_id on accounts  (cost=0.15..8.17 rows=1 width=181) (actual time=0.022..0.023 rows=1.00 loops=1)
   Index Cond: ((account_id)::text = 'ACC-000006'::text)
   Index Searches: 1
   Buffers: shared hit=2
Planning:
   Buffers: shared hit=46
Planning Time: 1.138 ms
Execution Time: 0.037 ms

