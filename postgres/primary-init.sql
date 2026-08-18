-- Initialize PostgreSQL for replication
-- This script runs on the primary database

-- Create replicator user for replication
CREATE USER IF NOT EXISTS replicator WITH REPLICATION ENCRYPTED PASSWORD 'replicator123';

-- Grant replication privilege
ALTER ROLE replicator WITH REPLICATION;

-- Create the main application user
CREATE USER IF NOT EXISTS tradealert WITH ENCRYPTED PASSWORD 'tradealert123';

-- Grant privileges to tradealert user
GRANT CONNECT ON DATABASE tradealert TO tradealert;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO tradealert;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO tradealert;

-- Allow replication connections from standby
-- The pg_hba.conf is handled by Docker environment variables, but this ensures the user exists
