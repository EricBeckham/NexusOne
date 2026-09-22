#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c 'CREATE SCHEMA quartz; CREATE SCHEMA platform_static;'
PGOPTIONS='-c search_path=quartz' psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /opt/poc/init/tables_postgres.sql
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c 'CREATE TABLE quartz.poc_fire_log (job_key varchar(200) PRIMARY KEY, scheduler_instance varchar(200) NOT NULL, fired_at timestamptz NOT NULL DEFAULT now());'
