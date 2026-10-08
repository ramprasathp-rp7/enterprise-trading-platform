-- Migration 018: Channels for price alert notifications
--
-- A fired price alert is now sent on the channels the customer chose in their alert preferences
-- (PreferenceResolver), like order notifications: EMAIL and SMS go to the sender stubs (logged, not
-- delivered yet) and IN_APP shows it in the UI bell. `channels` records which ones were used for a
-- firing; the bell's feed (GET /price-alerts/triggers) only returns triggers where in_app is true.
-- Existing triggers were in-app only, which the defaults reflect.

ALTER TABLE price_alert_triggers
    ADD COLUMN channels VARCHAR(40) NOT NULL DEFAULT 'IN_APP',
    ADD COLUMN in_app   BOOLEAN     NOT NULL DEFAULT true;

COMMENT ON COLUMN price_alert_triggers.channels IS
    'Channels the customer was notified on for this firing, from their alert preferences (EMAIL, SMS, IN_APP).';
COMMENT ON COLUMN price_alert_triggers.in_app IS
    'True when IN_APP was one of the channels; only these triggers appear in the UI notification bell.';
