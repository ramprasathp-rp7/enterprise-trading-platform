Order Executor — Double-Debit Replay Verification

Purpose
- Explain how the order-executor service prevents double-debit on replayed `orders` messages.
- Provide the end-to-end manual flow and commands to reproduce and verify the safeguard.

Assumptions
- Kafka is running inside a Linux host container named `kafka` and is reachable from that host at `localhost:9092`.
- You have SSH access to the Kafka/Linux host (used by the provided replay script).
- `manifest.env` (or `.env`) contains `EC2_USER`, `EC2_PRIVATE_IP`, `SSH_PORT`, `KAFKA_CONTAINER_NAME`.
- Postgres DB and `order-executor-app` use the migrations/seed data included in this repo.
- You run commands from repository root on your Windows machine via Git Bash / WSL.

High level protection design
1. The message consumer (`OrderPlacedListener`) hands a parsed `OrderPlaced` envelope to `OrderExecutorService.process()`.
2. `process()` now locks the `orders` row (SELECT ... FOR UPDATE) and checks the `status` before performing settlement.
3. Settlement (debiting account, updating position) is performed only after the row is locked and the status is still `NEW`.
4. `updateOrderStatus(...)` in `OrderMapper` uses a conditional SQL `WHERE id = ? AND status = 'NEW'`. The code checks returned row count:
   - If rows == 1: this delivery performed the transition and should publish the `trade-events` message.
   - If rows == 0: another delivery already processed this order — skip settlement and publishing.

Files changed (key points)
- `OrderMapper.updateOrderStatus(...)` — now updates only when status is `NEW` and returns affected rows.
- `OrderMapper.findOrderStatusForUpdate(...)` — new method to lock the order row before processing.
- `OrderExecutorService.process(...)` — marked `@Transactional`; reads `status FOR UPDATE`, performs settlement, then uses conditional update and checks rows before publish.
- `scripts/replay_single_order.sh` — helper to replay a specific partition+offset from `orders` topic preserving key.

Manual verification flow (commands)
1. Start services
- Start Kafka and DB as your environment requires.
- check the account balance first and note down.
- Build and run `trade-api` (from `Services/order-service`):

```powershell
cd Services/order-service
mvn spring-boot:run
```

2. POST an order via the Trade API (example)
- Use the API to create a new order (replace `idempotencyKey` as desired):

```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "accountId":"1",
    "symbol":"INFY.NS",
    "side":"BUY",
    "quantity":1,
    "price":1100.00,
    "idempotencyKey":"test-order-fee-016"
  }'
```

- Note the returned `orderId` in the response JSON and the `idempotencyKey`.

3. Identify the topic partition+offset for that order (optional)
- On the Kafka host or inside the container list topics / consume to find the offset. Example (container):

```bash
docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders --from-beginning --max-messages 20 --property print.key=true --property key.separator=$'\t'
```

It is also given when an order is published in Orders topic

- Use the consumed output to find the message with your `orderId` (note partition and offset).


4. Replay the same message (using `replay_single_order.sh`)
- Example (run from repo root):

```bash
bash Services/order-service/scripts/replay_single_order.sh --partition <PARTITION> --offset <OFFSET> --topic orders --replay-topic orders
```

- Observe `order-executor-app` logs: should show either `Skipping fill update: order not NEW (already processed)` or `Skipping settlement: order not NEW` depending on timing.

5. Run the Order Executor Service

6. Verify no extra debit and no second `trade-events` message
- Confirm account balance unchanged after replay.
- Confirm `trade-events` topic has only one event for your `orderId` (use a consumer to inspect recent messages).

Notes & Troubleshooting
- If your environment uses a different Kafka container name or tool paths, edit `scripts/replay_single_order.sh` accordingly.
- If `kafka-console-consumer.sh` does not support `--offset` (older versions), consume `--from-beginning` and filter by offset manually.
- If a replay still causes a debit, verify logs show `rows == 1` on the first delivery and `rows == 0` on the replay; if settlement still ran on the replay, we need to ensure the `@Transactional` annotation is picked up and the DB supports `SELECT ... FOR UPDATE` (Postgres does).

Further improvements
- Publish `cashDelta` and position information in the `trade-events` payload to make verification easier.
- Add an integration test that inserts a NEW order row, runs two concurrent calls to `process()` and asserts only one changed the DB and only one event published.

EOF
