-- Migration 021: Order notifications
--
-- The Trade Executor publishes the outcome of every order (ORDER_FILLED, ORDER_REJECTED, and
-- ORDER_CANCELLED when it applies) to the `trade-events` Kafka topic. The Trade REST API consumes
-- that topic (consumer group `order-notifications`) and records one row per event here, enriched
-- with the order's symbol, side, quantity and limit price. The UI reads the rows through
-- GET /api/v1/accounts/{accountId}/order-notifications to fill the notification bell, so every
-- order success or failure the customer sees comes from `trade-events`.
--
-- Kafka delivers at least once: event_id (the envelope's eventId) is unique, so a redelivered
-- event is ignored instead of notifying twice.
--
-- Channels follow the customer's alert preferences (PreferenceResolver): `channels` records which
-- ones were used for the event (e.g. 'EMAIL,IN_APP'). EMAIL and SMS are handed to sender stubs
-- (logged, not delivered yet); the UI bell only shows rows where in_app is true.

CREATE TABLE order_notifications (
    notification_id  BIGSERIAL       PRIMARY KEY,
    event_id         VARCHAR(64)     NOT NULL,
    account_id       BIGINT          NOT NULL,
    order_id         UUID            NOT NULL,
    event_type       VARCHAR(20)     NOT NULL,
    status           VARCHAR(12)     NOT NULL,
    symbol           VARCHAR(20),
    side             VARCHAR(4),
    quantity         INTEGER,
    limit_price      NUMERIC(18, 6),
    executed_price   NUMERIC(18, 6),
    reason           TEXT,
    occurred_at      TIMESTAMPTZ     NOT NULL,
    channels         VARCHAR(40)     NOT NULL DEFAULT 'IN_APP',
    in_app           BOOLEAN         NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_order_notifications_event UNIQUE (event_id),
    CONSTRAINT ck_order_notifications_event_type CHECK (event_type IN ('ORDER_FILLED', 'ORDER_REJECTED', 'ORDER_CANCELLED')),
    CONSTRAINT ck_order_notifications_status CHECK (status IN ('FILLED', 'REJECTED', 'CANCELLED'))
);

-- The UI polls "everything in-app for this account after the last id I have"
CREATE INDEX ix_order_notifications_account ON order_notifications (account_id, notification_id) WHERE in_app;

COMMENT ON TABLE order_notifications IS
    'One row per order outcome consumed from the trade-events topic; read by the UI notification bell.';
COMMENT ON COLUMN order_notifications.event_id IS
    'eventId of the trade-events message envelope; unique so a redelivered event is stored once.';
COMMENT ON COLUMN order_notifications.channels IS
    'Channels the customer was notified on for this event, from their alert preferences (EMAIL, SMS, IN_APP).';
COMMENT ON COLUMN order_notifications.in_app IS
    'True when IN_APP was one of the channels; only these rows appear in the UI notification bell.';
COMMENT ON COLUMN order_notifications.occurred_at IS
    'When the order was executed (payload executedOn), or the event time when that is missing.';
