#!/usr/bin/env bash

set -euo pipefail

# Resolves repo root (enterprise-trading-platform) from scripts/
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$ROOT_DIR"

fail() {
    echo "Failure: check.sh: $*" >&2
    exit 1
}

info() {
    echo "Info:  check.sh: $*"
}

pass() {
    echo "Passed: check.sh: $*"
}

# 1. Load manifest.env from project root
MANIFEST_FILE="$ROOT_DIR/manifest.env"
[[ -f "$MANIFEST_FILE" ]] || fail "manifest.env not found at $MANIFEST_FILE"

info "Loading $MANIFEST_FILE..."
set -a
# shellcheck disable=SC1090
source "$MANIFEST_FILE"
set +a

# 2. Validate required manifest variables
[[ -n "${APPLY_COMMAND:-}" ]] || fail "APPLY_COMMAND is not set in manifest.env"
[[ -n "${ACCOUNTS_TABLE:-}" ]] || fail "ACCOUNTS_TABLE is not set in manifest.env"
[[ -n "${ACCOUNT_STATUS_COLUMN:-}" ]] || fail "ACCOUNT_STATUS_COLUMN is not set in manifest.env"
[[ -n "${ORDERS_TABLE:-}" ]] || fail "ORDERS_TABLE is not set in manifest.env"
[[ -n "${ORDER_IDEMPOTENCY_KEY_COLUMN:-}" ]] || fail "ORDER_IDEMPOTENCY_KEY_COLUMN is not set in manifest.env"

pass "manifest.env variables verified."

# 3. Load .env for database credentials (if present)
if [[ -f "$ROOT_DIR/.env" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ROOT_DIR/.env"
    set +a
fi

TARGET_DB="${TARGET_DATABASE:-${POSTGRES_DB:-enterprise_trading_platform}}"

# 4. Execute APPLY_COMMAND
info "Executing APPLY_COMMAND: $APPLY_COMMAND"
eval "$APPLY_COMMAND" || fail "APPLY_COMMAND failed to execute schema/seed."
pass "Schema and seed applied successfully."

# Helper function to run probes and verify SQLSTATE
run_probe_test() {
    local probe_file="$1"
    local expected_sqlstate="$2"
    local probe_name
    probe_name="$(basename "$probe_file")"

    [[ -f "$probe_file" ]] || fail "Missing probe file: $probe_file"

    info "Running probe $probe_name (expecting SQLSTATE $expected_sqlstate)..."

    local output
    output=$(psql -v ON_ERROR_STOP=1 -d "$TARGET_DB" -f "$probe_file" 2>&1) && {
        fail "Probe $probe_name succeeded unexpectedly! Expected SQLSTATE $expected_sqlstate."
    }

    if echo "$output" | grep -q "$expected_sqlstate"; then
        pass "Probe $probe_name correctly failed with SQLSTATE $expected_sqlstate."
    else
        echo "$output" >&2
        fail "Probe $probe_name failed, but SQLSTATE $expected_sqlstate was not present in output."
    fi
}

# 5. Run Probes against probes/ directory
run_probe_test "probes/duplicate-idempotency-key.sql" "23505"
run_probe_test "probes/orphan-foreign-key.sql" "23503"

echo ""
echo "================================================================="
pass "ALL HARNESS CHECKS PASSED!"
echo "================================================================="