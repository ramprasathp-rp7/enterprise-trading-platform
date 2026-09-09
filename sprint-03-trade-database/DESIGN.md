# Enterprise Trade Database Architecture & Scaling Strategy
---

### 1. Retention Grain & Trade Execution Tracking

#### 1.1 Beyond the Initial Order Intent
When a trading client places an order, the system captures their original instructions—such as requested target price, requested quantity, client-provided idempotency keys, and submission timestamps (`orders.created_on`). However, capturing client intent alone is insufficient for a operational trading system. 

When an order reaches its terminal state (specifically transitioning to `FILLED`), the database must permanently record the exact execution metadata. This data is critical for Best Execution reporting (e.g., RTS 28 / MiFID II compliance), post-trade transaction cost analysis (TCA), and regulatory audit trails:

* **Executed Fill Price (`orders.executed_price`)**: Captures the exact weighted average price at which the trade matched in the market. This often differs slightly from the client's original limit price (`orders.price`) due to market slippage or partial fills.
* **Execution Timestamp (`orders.updated_on`)**: Records the exact microsecond timestamp when the trade engine confirmed the match, establishing latency benchmarks between order ingress (`created_on`) and engine matching (`updated_on`).
* **Position Cost Basis (`positions.average_cost`)**: Calculates and records the account's revised weighted-average purchase price, stored up to 6 decimal places (`NUMERIC(18,6)`). This high precision avoids rounding drift over thousands of sequential position changes.
* **Terminal State Validation (`orders.status`)**: Enforces state machine integrity at the database layer using explicit constraints (`ck_orders_executed_price_iff_filled`). This guarantees that an order cannot be flagged as `FILLED` without an associated fill price, nor can an unexecuted order accidentally record an execution price.

#### 1.2 Population Mechanics & System Components
* **Owning Component**: The **Trade Executor** service (Sprint 7).
* **Transaction Flow & Atomicity**:
  1. **Matching Engine**: The Trade Executor matches an active `NEW` order against current liquidity.
  2. **Atomic Ledger Execution**: To prevent race conditions or partial writes, the engine updates the order ledger and recalculates position balances inside a single database transaction block:

     ```sql
     -- 1. Finalise order state, record fill price, and update engine timestamp
     UPDATE orders 
     SET status = 'FILLED', 
         executed_price = :fill_price, 
         updated_on = now() 
     WHERE id = :order_id AND status = 'NEW';

     -- 2. Upsert position holding and update weighted-average cost basis
     INSERT INTO positions (account_id, instrument_id, quantity, average_cost, last_updated)
     VALUES (:account_id, :instrument_id, :fill_qty, :fill_price, now())
     ON CONFLICT (account_id, instrument_id) DO UPDATE SET
       average_cost = ((positions.quantity * positions.average_cost) + (:fill_qty * :fill_price)) 
                      / (positions.quantity + :fill_qty),
       quantity = positions.quantity + :fill_qty,
       last_updated = now();
     ```

  3. **Trigger Housekeeping**: Automated database triggers (`trg_orders_set_updated` and `trg_positions_set_updated`) intercept the update operations, updating system timestamps consistently without relying on application-level clock sync.

---

### 2. Incremental Extraction Architecture 

Analytical processing, operational risk monitoring, and end-of-day reporting require access to trade history without impacting real-time trade throughput. To achieve this, the nightly pipeline uses a high-water mark timestamp pattern to extract incremental deltas without locking hot operational tables.

#### 2.1 Incremental Delta Extract Query
```sql
SELECT 
    id, 
    account_id, 
    instrument_id, 
    side, 
    quantity, 
    price, 
    executed_price, 
    status, 
    created_on,
    updated_on 
FROM orders
WHERE updated_on >= :last_extract_timestamp 
  AND updated_on <  :current_extract_timestamp;

```

#### 2.2 Database Performance & Reliability Mechanics

* **Index Alignment**: The extraction query runs directly against the dedicated operational B-Tree index `ix_orders_updated_on ON orders (updated_on)`. Windowing on `updated_on` rather than `created_on` is deliberate: an order mutates in place (`NEW` becomes `FILLED` days later), and a `created_on` watermark would never re-read that status change, freezing the analytical store at whatever status an order had when it was first extracted.
* **Database Access Plan**: The PostgreSQL query planner uses a `Bitmap Index Scan` across `ix_orders_updated_on` to aggregate relevant page pointers in memory, followed by a sequential `Bitmap Heap Scan`. This prevents random disk I/O thrashing during large multi-gigabyte nightly batch jobs.
* **At-Least-Once Delivery Guarantees**: The ETL pipeline runner updates its high-water mark timestamp (`:current_extract_timestamp`) in external persistent state only after the downstream analytical warehouse confirms a successful write. If a job fails mid-stream, the next run safely re-fetches the same window without data loss or record corruption.

---

## Growth Projections, Cost Analysis & Archival Position

### 3. Growth Projections, Volumetrics & Financial Cost

#### 3.1 Volume Assumptions

* **Active Trading Accounts**: 100,000 retail trading accounts.
* **Order Activity**: An average of 50 orders per account daily = **5,000,000 orders / day**.
* **Storage Footprint Per Record (PostgreSQL Tuple + Index Overhead)**:
* `accounts`: ~150 bytes per row (infrequent writes)
* `instruments`: ~120 bytes per row (mostly static reference data)
* `positions`: ~100 bytes per row (bounded by `accounts × instruments`)
* `orders`: ~220 bytes row data + ~100 bytes index overhead = **~320 bytes total / order**



#### 3.2 Projected Data Capacity Trajectory

| Operational Horizon | Daily Order Volume | Daily Storage Growth | Cumulative Storage (Data + Indexes) |
| --- | --- | --- | --- |
| **1 Day** | 5,000,000 | ~1.6 GB | **~1.6 GB** |
| **1 Month (30 Days)** | 150,000,000 | ~48 GB | **~48 GB** |
| **1 Year (365 Days)** | 1,825,000,000 | ~584 GB | **~584 GB** |
| **5 Years (Regulatory Minimum)** | 9,125,000,000 | ~2.92 TB | **~2.92 TB** |

#### 3.3 Write Overhead & Infrastructure Storage Costs

* **Disk IOPS Load**: Every order insertion updates 3 core indexes (`ix_orders_account_created_on`, `ix_orders_created_on`, and `uq_orders_idempotency_key`). At 5,000,000 orders per day (averaging ~60 TPS with peak market-open spikes near 1,200 TPS), write workloads fit comfortably within standard provisioned cloud SSD storage (such as AWS `gp3` with 3,000 baseline IOPS).
* **Cost Optimization Strategy**: High-performance primary OLTP storage (NVMe/SSD) costs approximately $0.12/GB/month (~$350/month for 2.9 TB). Migrating historical orders older than 90 days off primary database drives into compressed Parquet files on object storage (S3 Standard / Glacier) reduces storage costs to ~$0.023/GB/month (~$65/month for 2.9 TB). This strategy cuts ongoing cloud storage costs by **over 80%**.

---

### 4. Architectural Position: Declarative Partitioning & Archival Policy

#### Architectural Decision

**Native PostgreSQL table partitioning is not required for Sprint 3. However, an automated 90-day hot/cold data archival strategy must be established.**

#### 4.1 Why Declarative Partitioning is Deferred

1. **Single-Table B-Tree Performance**: PostgreSQL handles single B-Tree indexed tables up to **100 million rows (~30–50 GB)** with minimal performance loss.
2. **Global Constraint Limitations**: PostgreSQL requires that unique keys match the underlying table partitioning strategy. Requiring our idempotency constraint to use `(idempotency_key, created_on)` limits its ability to block duplicate requests across multi-day retries.
3. **Partition Trigger Threshold**: Declarative range partitioning (`orders` partitioned monthly by `created_on`) should be deferred to a future migration (`002_partition_orders.sql`) when the live table size reaches **100 GB (~2 to 3 months of live trading history)**.

#### 4.2 Hot/Cold Data Retention Policy

* **Hot Operational Database Window (PostgreSQL)**: Retains **90 Days (~450,000,000 rows / ~144 GB)**.
* More than 99% of regular user traffic hits orders created within the last 30 days.
* The partial index `ix_orders_account_open_created_on` (`WHERE status = 'NEW'`) stays lightweight because completed/filled orders drop off automatically, keeping active matching speeds consistent over time.


* **Cold Analytical Storage Window (Data Lake / Parquet)**: Retains **7 Years** to meet legal and financial compliance requirements. The nightly ETL pipeline continually backs up historical data to cold storage, allowing older rows to be pruned from the primary database instance without losing historical records.

#### 4.3 ADR: Denormalized aggregate fields in accounts.cash_balance and positions.quantity/average_cost
`orders` is the source of truth for all trading activity. In theory, an account's cash balance and a position's quantity/average cost could be derived at query time by aggregating executed orders for a given account/instrument. Strict 3NF would say these derived values shouldn't be stored as independent columns, since they're functionally determined by data in another table. We store `cash_balance`, `positions.quantity`, and `positions.average_cost` as materialized, denormalized fields rather than computing them on every read.

* **Read performance**: `balance` and `position` lookups are extremely hot paths (checked on every order placement for margin/buying-power checks); recomputing via aggregation over orders on every read would be too slow at scale.
* **Concurrency/correctness**: using accounts.version for optimistic locking on updates to cash_balance is far simpler than trying to lock and recompute an aggregate consistently.
* **Auditability**: `orders` remains the immutable event log; these fields are a cache/projection of it, not a replacement for it.

### 5. Database Setup

```bash
cd sprint-03-trade-database
./apply.sh
```

### 6. Probes Check

```bash
cd sprint-03-trade-database
./scripts/check.sh
```

### 7. Explain Analyze

```bash
psql -h localhost -p 5432 -U postgres -d enterprise_trading_platform -f sprint-03-trade-database/queries/queries.sql
```