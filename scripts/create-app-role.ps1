<#
.SYNOPSIS
    Creates a scoped, non-superuser Postgres role for the app to connect as, confined to its
    own schema - instead of the app using the Postgres admin/superuser role.

.DESCRIPTION
    docker-compose.yml's postgres service creates one admin role on first init, via
    POSTGRES_USER/POSTGRES_PASSWORD (superuser by default with the official postgres image).
    Running the app as that same role means a leaked app credential (config file, log, bug)
    hands over the whole Postgres instance - every database, every role, cluster-wide admin.

    Run this ONCE, after "docker compose up -d postgres" has bootstrapped the admin role, to
    create a second role that:
      - Can log in with its own password, mandatory here (no default is ever set)
      - Owns a dedicated schema ("image_schema" by default) inside the image_browser database
        - AviationImageRepository / AdminConfigRepository's CREATE/DROP TABLE calls still work
        unmodified, because this role's search_path defaults to that schema, so their
        unqualified table names ("images", "app_config") resolve inside it automatically
      - Has NO access to the "public" schema (revoked cluster-wide from PUBLIC - safe here
        since only the admin role and this app role ever use this database, and superusers
        bypass privilege checks regardless)
      - Has NO superuser/createdb/createrole bit, and cannot touch any other database or role

    Afterwards, point the app at APP_IB_USER/APP_IB_PASSWORD (docker-compose.yml's
    image-browser service) or SPRING_DATASOURCE_USERNAME/PASSWORD (local, non-docker
    mvn spring-boot:run) - never at the admin role.

.PARAMETER AdminUser
    The Postgres admin/superuser role (POSTGRES_USER) - used only to run this setup, never by
    the app itself.

.PARAMETER AdminPassword
    Password for -AdminUser.

.PARAMETER AppUser
    Username for the new scoped role the app will connect as. Mandatory - no default.

.PARAMETER AppPassword
    Password for the new scoped role. Mandatory - no default.

.PARAMETER Database
    Database the schema lives in. Defaults to "image_browser" (matches POSTGRES_DB's default
    in docker-compose.yml).

.PARAMETER Schema
    Name of the dedicated schema created for -AppUser. Defaults to "image_schema".

.EXAMPLE
    ./scripts/create-app-role.ps1 -AdminUser postgres -AdminPassword <admin-pw> -AppUser image_browser_app -AppPassword <app-pw>
#>
param(
    [Parameter(Mandatory = $true)][string]$AdminUser,
    [Parameter(Mandatory = $true)][string]$AdminPassword,
    [Parameter(Mandatory = $true)][string]$AppUser,
    [Parameter(Mandatory = $true)][string]$AppPassword,
    [string]$Database = "image_browser",
    [string]$Schema = "image_schema"
)

$ErrorActionPreference = "Stop"

function Invoke-Psql {
    param([string]$Sql)

    docker compose exec -T -e PGPASSWORD=$AdminPassword postgres `
        psql -U $AdminUser -d $Database -v ON_ERROR_STOP=1 -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql command failed (exit code $LASTEXITCODE): $Sql"
    }
}

function Escape-SqlLiteral([string]$value) {
    return $value.Replace("'", "''")
}

$escapedPassword = Escape-SqlLiteral $AppPassword

Write-Host "Creating role '$AppUser' (login only - no superuser/createdb/createrole)..."
Invoke-Psql -Sql "CREATE ROLE `"$AppUser`" WITH LOGIN PASSWORD '$escapedPassword' NOSUPERUSER NOCREATEDB NOCREATEROLE;"

Write-Host "Creating schema '$Schema', owned by '$AppUser'..."
Invoke-Psql -Sql "CREATE SCHEMA IF NOT EXISTS `"$Schema`" AUTHORIZATION `"$AppUser`";"

Write-Host "Revoking default PUBLIC access to schema 'public' (cluster-wide hardening; admin is unaffected - superusers bypass privilege checks)..."
Invoke-Psql -Sql "REVOKE ALL ON SCHEMA public FROM PUBLIC;"

Write-Host "Granting '$AppUser' connect access to database '$Database'..."
Invoke-Psql -Sql "GRANT CONNECT ON DATABASE `"$Database`" TO `"$AppUser`";"

Write-Host "Defaulting '$AppUser' search_path to '$Schema'..."
Invoke-Psql -Sql "ALTER ROLE `"$AppUser`" IN DATABASE `"$Database`" SET search_path TO `"$Schema`";"

Write-Host ""
Write-Host "Done. Role '$AppUser' can log in and is confined to schema '$Schema' in database" -ForegroundColor Green
Write-Host "'$Database' - no superuser bit, no access to 'public' or any other database." -ForegroundColor Green
Write-Host ""
Write-Host "Point the app at this role (never the admin role):" -ForegroundColor Yellow
Write-Host "  - Docker: set APP_IB_USER=$AppUser and APP_IB_PASSWORD=<password> in .env" -ForegroundColor Yellow
Write-Host "  - Local dev (mvn spring-boot:run): set SPRING_DATASOURCE_USERNAME/PASSWORD env vars" -ForegroundColor Yellow
Write-Host "Then start/restart the app so it connects as the scoped role." -ForegroundColor Yellow
