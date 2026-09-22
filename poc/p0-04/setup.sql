-- Run as a PostgreSQL administrator in the dedicated nexus_poc database.
-- Create role nexus_poc, set its password and create database nexus_poc first.
CREATE SCHEMA IF NOT EXISTS flowable AUTHORIZATION nexus_poc;
GRANT USAGE, CREATE ON SCHEMA flowable TO nexus_poc;
CREATE TABLE IF NOT EXISTS public.business_marker (id text PRIMARY KEY);
GRANT SELECT, INSERT, UPDATE, DELETE ON public.business_marker TO nexus_poc;
