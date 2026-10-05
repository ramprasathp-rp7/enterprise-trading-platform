#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
MIGRATIONS_DIR="$SCRIPT_DIR/migrations"
SEED_DIR="$SCRIPT_DIR/seed"
ENV_FILE="$SCRIPT_DIR/../../../.env"

fail() {
    echo "apply.sh: $*" >&2
    exit 1
}

if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

# Resolve TARGET_DATABASE: an already-exported value wins outright; otherwise fall back to POSTGRES_DB, now loaded from .env above.
: "${TARGET_DATABASE:=${POSTGRES_DB:-}}"

[[ -n "${TARGET_DATABASE:-}" ]] || fail "TARGET_DATABASE is not set and POSTGRES_DB was not found in $ENV_FILE"

echo "apply.sh: target database: $TARGET_DATABASE"

# Fail loudly if the folders this script depends on don't exist, rather than silently applying nothing.
[[ -d "$MIGRATIONS_DIR" ]] || fail "missing migrations directory: $MIGRATIONS_DIR"
[[ -d "$SEED_DIR" ]]       || fail "missing seed directory: $SEED_DIR"

run_sql_file() {
    local file="$1"
    echo "apply.sh: applying $(basename -- "$file")"
    # ON_ERROR_STOP=1 is not optional: without it psql reports an error and keeps going, so a half-applied file would otherwise exit 0.
    psql -v ON_ERROR_STOP=1 --no-psqlrc -d "$TARGET_DATABASE" -f "$file"
}

# Migrations, in filename order.
shopt -s nullglob
migration_files=("$MIGRATIONS_DIR"/*.sql)
shopt -u nullglob
[[ ${#migration_files[@]} -gt 0 ]] || fail "no .sql files found in $MIGRATIONS_DIR"

for f in "${migration_files[@]}"; do
    run_sql_file "$f"
done

# Seed data, in filename order, only after every migration has run.
shopt -s nullglob
seed_files=("$SEED_DIR"/*.sql)
shopt -u nullglob
[[ ${#seed_files[@]} -gt 0 ]] || fail "no .sql files found in $SEED_DIR"

for f in "${seed_files[@]}"; do
    run_sql_file "$f"
done

echo "apply.sh: done — $TARGET_DATABASE is migrated and seeded."
