CREATE OR REPLACE FUNCTION purge_old_candle_data()
RETURNS TABLE(deleted_count BIGINT, instrument_id_sample BIGINT) AS $$
DECLARE
    v_deleted_count BIGINT;
    v_sample_id BIGINT;
BEGIN
    DELETE FROM candle_data
    WHERE date < CURRENT_DATE - INTERVAL '90 days';

    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;

    SELECT instrument_id INTO v_sample_id
    FROM candle_data
    WHERE date >= CURRENT_DATE - INTERVAL '90 days'
    LIMIT 1;

    RETURN QUERY SELECT v_deleted_count, COALESCE(v_sample_id, 0::BIGINT);
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION purge_old_candle_data IS
    'Removes candle data older than 90 days to maintain 3-month rolling window. '
    'Called weekly by application scheduler.';
