#!/usr/bin/env bash
set -euo pipefail

echo "Waiting for Hive Metastore database at ${METASTORE_DB_HOST:-hive-metastore-db}:${METASTORE_DB_PORT:-5432}"
until timeout 1 bash -c "</dev/tcp/${METASTORE_DB_HOST:-hive-metastore-db}/${METASTORE_DB_PORT:-5432}" >/dev/null 2>&1; do
  sleep 2
done

echo "Initializing Hive Metastore schema if needed"
if /opt/hive/bin/schematool -dbType postgres -info >/tmp/metastore-schema-info.log 2>&1; then
  cat /tmp/metastore-schema-info.log
else
  cat /tmp/metastore-schema-info.log
  /opt/hive/bin/schematool -dbType postgres -initSchema --verbose
fi

echo "Starting Hive Metastore on port 9083"
exec /opt/hive/bin/hive --service metastore -p 9083
