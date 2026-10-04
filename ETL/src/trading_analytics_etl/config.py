"""Configuration read from the environment. No credentials or paths are
hardcoded, matching the operational services' pattern of reading everything
from .env / the process environment.

Postgres connection deliberately uses the libpq-standard variable names
(PGHOST, PGPORT, PGUSER, PGPASSWORD, POSTGRES_DB / PGDATABASE) already used
elsewhere in this repo's .env (psql reads these automatically), rather than
inventing a second, redundant DB_* naming just for this package. psycopg is
libpq-based, so passing no explicit host/user/etc. to psycopg.connect()
already picks these up on its own -- pg_dsn below is built explicitly
anyway, so this package works the same whether or not that auto-pickup is
relied on elsewhere.
"""
from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _find_repo_root() -> Path:
    """Find the repository root by walking up from this module until we find .env or .git"""
    current = Path(__file__).resolve()
    while current != current.parent:
        current = current.parent
        if (current / ".env").exists() or (current / ".git").exists():
            return current
    # Fallback to current working directory if not found
    return Path.cwd()


@dataclass(frozen=True)
class Config:
    pg_host: str
    pg_port: int
    pg_db: str
    pg_user: str
    pg_password: str

    duckdb_path: str
    schema_sql_path: str

    staging_dir: str

    @property
    def pg_dsn(self) -> str:
        return (
            f"host={self.pg_host} port={self.pg_port} dbname={self.pg_db} "
            f"user={self.pg_user} password={self.pg_password}"
        )

    @classmethod
    def from_env(cls) -> "Config":
        repo_root = _find_repo_root()
        
        # Helper function to resolve relative paths from repo root
        def resolve_path(env_var: str, default: str) -> str:
            path_str = os.environ.get(env_var, default)
            path_obj = Path(path_str)
            # If the path is relative and doesn't exist from cwd, try from repo root
            if not path_obj.is_absolute() and not path_obj.exists():
                repo_path = repo_root / path_str
                if repo_path.exists():
                    return str(repo_path)
            return path_str
        
        return cls(
            pg_host=os.environ.get("PGHOST", "localhost"),
            pg_port=int(os.environ.get("PGPORT", "5432")),
            # POSTGRES_DB is what this repo's .env actually sets; PGDATABASE
            # is the libpq-standard fallback name for the same thing.
            pg_db=os.environ.get("POSTGRES_DB", os.environ.get("PGDATABASE", "enterprise_trading_platform")),
            pg_user=os.environ.get("PGUSER", "postgres"),
            # This repo's .env sets both PGPASSWORD and DB_PASSWORD to the
            # same value; prefer PGPASSWORD as the libpq-standard one, fall
            # back to DB_PASSWORD if only that's set.
            pg_password=os.environ.get("PGPASSWORD", os.environ.get("DB_PASSWORD", "")),
            duckdb_path=resolve_path("ANALYTICS_DUCKDB_PATH", "Database/duckdb/analytics/analytics_store.duckdb"),
            # contracts/analytics-schema.sql is binding and lives at the repo
            # root's Database/duckdb/analytics/ folder. Overridable in
            # case your checkout places it elsewhere.
            schema_sql_path=resolve_path(
                "ANALYTICS_SCHEMA_PATH", "Database/duckdb/analytics/analytics-schema.sql"
            ),
            staging_dir=resolve_path("ANALYTICS_STAGING_DIR", ".staging"),
        )