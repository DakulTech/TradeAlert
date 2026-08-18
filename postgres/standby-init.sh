#!/bin/bash
# Initialize PostgreSQL Standby for Replication
# This script runs on the standby database and clones from primary

set -e

echo "Initializing PostgreSQL Standby..."

# Wait for primary to be ready
echo "Waiting for primary database..."
until pg_isready -h postgres-primary -U replicator; do
  echo "Primary not ready yet. Waiting..."
  sleep 2
done

echo "Primary is ready. Performing base backup..."

# Perform base backup from primary
pg_basebackup \
  -h postgres-primary \
  -U replicator \
  -D /var/lib/postgresql/data \
  -P \
  -v \
  -W \
  --wal-method=stream \
  --write-recovery-conf

echo "Base backup complete."

# Set up recovery configuration for replication
cat > /var/lib/postgresql/data/recovery.conf << EOF
standby_mode = 'on'
primary_conninfo = 'host=postgres-primary user=replicator password=replicator123 port=5432'
restore_command = 'cp /var/lib/postgresql/wal_archive/%f %p'
EOF

# Change permissions
chmod 0600 /var/lib/postgresql/data/recovery.conf

echo "Standby initialization complete. Ready for replication."
