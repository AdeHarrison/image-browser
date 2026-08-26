#!/usr/bin/env bash
#
# Creates a scoped, non-superuser Postgres role for the app to connect as, confined to its
# own schema -- instead of the app using the Postgres admin/superuser role.
#
# docker-compose.yml's postgres service creates one admin role on first init, via
# POSTGRES_USER/POSTGRES_PASSWORD (superuser by default with the official postgres image).
# Running the app as that same role means a leaked app credential (config file, log, bug)
# hands over the whole Postgres instance -- every database, every role, cluster-wide admin.
#
# Run this ONCE, after "docker compose up -d postgres" has bootstrapped the admin role, to
# create a second role that:
#   - Can log in with its own password, mandatory here (no default is ever set)
#   - Owns a dedicated schema ("image_schema" by default) inside the image_browser database
#     -- AviationImageRepository / AdminConfigRepository's CREATE/DROP TABLE calls still work
#     unmodified, because this role's search_path defaults to that schema, so their
#     unqualified table names ("images", "app_config") resolve inside it automatically
#   - Has NO access to the "public" schema (revoked cluster-wide from PUBLIC -- safe here
#     since only the admin role and this app role ever use this database, and superusers
#     bypass privilege checks regardless)
#   - Has NO superuser/createdb/createrole bit, and cannot touch any other database or role
#
# Afterwards, point the app at APP_IB_USER/APP_IB_PASSWORD (docker-compose.yml's
# image-browser service) or SPRING_DATASOURCE_USERNAME/PASSWORD (local, non-docker
# mvn spring-boot:run) -- never at the admin role.
#
# Usage:
#   ./scripts/create-app-role.sh -u <admin-user> -p <admin-password> -a <app-user> -w <app-password> [-d <database>] [-s <schema>]
#
# Example:
#   ./scripts/create-app-role.sh -u postgres -p <admin-pw> -a image_browser_app -w <app-pw>

set -euo pipefail

DATABASE="image_browser"
SCHEMA="image_schema"

usage() {
    echo "Usage: $0 -u <admin-user> -p <admin-password> -a <app-user> -w <app-password> [-d <database>] [-s <schema>]" >&2
    exit 1
}

while getopts "u:p:a:w:d:s:h" opt; do
    case "$opt" in
        u) ADMIN_USER="$OPTARG" ;;
        p) ADMIN_PASSWORD="$OPTARG" ;;
        a) APP_USER="$OPTARG" ;;
        w) APP_PASSWORD="$OPTARG" ;;
        d) DATABASE="$OPTARG" ;;
        s) SCHEMA="$OPTARG" ;;
        h) usage ;;
        *) usage ;;
    esac
done

if [[ -z "${ADMIN_USER:-}" || -z "${ADMIN_PASSWORD:-}" || -z "${APP_USER:-}" || -z "${APP_PASSWORD:-}" ]]; then
    usage
fi

escape_sql_literal() {
    printf '%s' "$1" | sed "s/'/''/g"
}

run_psql() {
    local sql="$1"
    docker compose exec -T -e PGPASSWORD="$ADMIN_PASSWORD" postgres \
        psql -U "$ADMIN_USER" -d "$DATABASE" -v ON_ERROR_STOP=1 -c "$sql"
}

ESCAPED_PASSWORD="$(escape_sql_literal "$APP_PASSWORD")"

echo "Creating role '$APP_USER' (login only -- no superuser/createdb/createrole)..."
run_psql "CREATE ROLE \"$APP_USER\" WITH LOGIN PASSWORD '$ESCAPED_PASSWORD' NOSUPERUSER NOCREATEDB NOCREATEROLE;"

echo "Creating schema '$SCHEMA', owned by '$APP_USER'..."
run_psql "CREATE SCHEMA IF NOT EXISTS \"$SCHEMA\" AUTHORIZATION \"$APP_USER\";"

echo "Revoking default PUBLIC access to schema 'public' (cluster-wide hardening; admin is unaffected -- superusers bypass privilege checks)..."
run_psql "REVOKE ALL ON SCHEMA public FROM PUBLIC;"

echo "Granting '$APP_USER' connect access to database '$DATABASE'..."
run_psql "GRANT CONNECT ON DATABASE \"$DATABASE\" TO \"$APP_USER\";"

echo "Defaulting '$APP_USER' search_path to '$SCHEMA'..."
run_psql "ALTER ROLE \"$APP_USER\" IN DATABASE \"$DATABASE\" SET search_path TO \"$SCHEMA\";"

echo ""
echo "Done. Role '$APP_USER' can log in and is confined to schema '$SCHEMA' in database"
echo "'$DATABASE' -- no superuser bit, no access to 'public' or any other database."
echo ""
echo "Point the app at this role (never the admin role):"
echo "  - Docker: set APP_IB_USER=$APP_USER and APP_IB_PASSWORD=<password> in .env"
echo "  - Local dev (mvn spring-boot:run): set SPRING_DATASOURCE_USERNAME/PASSWORD env vars"
echo "Then start/restart the app so it connects as the scoped role."
