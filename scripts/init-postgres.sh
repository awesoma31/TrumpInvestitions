#!/usr/bin/env bash
# Idempotent postgres bootstrap: creates missing users and databases.
# Safe to run on a volume that already has partial state (e.g. after adding a new service).
set -euo pipefail

CONTAINER="${POSTGRES_CONTAINER:-postgres}"

psql() {
  docker exec "$CONTAINER" psql -U postgres "$@"
}

db_exists() {
  psql -tAc "SELECT 1 FROM pg_database WHERE datname = '$1'" | grep -q 1
}

role_exists() {
  psql -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$1'" | grep -q 1
}

create_role() {
  local name=$1 password=$2
  if role_exists "$name"; then
    echo "  role '$name' already exists, skipping"
  else
    psql -c "CREATE USER $name WITH PASSWORD '$password';"
    echo "  role '$name' created"
  fi
}

create_db() {
  local name=$1 owner=$2
  if db_exists "$name"; then
    echo "  database '$name' already exists, skipping"
  else
    psql -c "CREATE DATABASE $name OWNER $owner;"
    echo "  database '$name' created"
  fi
}

echo "=== Initializing PostgreSQL ==="
create_role trading trading
create_db   trading trading
create_role auth    auth
create_db   auth_gateway auth
echo "=== Done ==="
