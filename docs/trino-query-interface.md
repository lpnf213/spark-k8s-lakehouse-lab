# Trino Query Interface

Trino provides a SQL query engine for the Iceberg tables written by Spark.

## Architecture

```text
Spark writes Iceberg tables
  -> MinIO stores data and Iceberg metadata files
  -> Hive Metastore stores catalog metadata
  -> Trino queries Iceberg tables through Hive Metastore
```

## Start Trino

From the repository root:

```powershell
cd C:\repository\spark-operations\spark-operations
docker compose -f docker-compose.lakehouse.yml up -d trino
```

Or start the full lakehouse stack:

```powershell
docker compose -f docker-compose.lakehouse.yml up -d --build
```

## Trino UI

Open:

```text
http://localhost:8080
```

The Trino UI shows query history and cluster status. For writing SQL interactively, use the Trino CLI or DBeaver.

## Run SQL With Trino CLI

```powershell
docker exec -it lakehouse-trino trino
```

Example queries:

```sql
SHOW CATALOGS;
SHOW SCHEMAS FROM iceberg;
SHOW TABLES FROM iceberg.raw_app_store;

SELECT count(*) FROM iceberg.raw_app_store.users;
SELECT count(*) FROM iceberg.raw_app_store.apps;
SELECT count(*) FROM iceberg.raw_app_store.subscriptions;

SELECT country_code, count(*) AS users
FROM iceberg.raw_app_store.users
GROUP BY country_code
ORDER BY users DESC;
```

## DBeaver Connection

Use DBeaver with the Trino driver:

- Host: `localhost`
- Port: `8080`
- User: `admin`
- Catalog: `iceberg`
- Schema: `raw_app_store`
- SSL: disabled

Then query:

```sql
SELECT *
FROM iceberg.raw_app_store.users
LIMIT 10;
```

## Catalog Config

The Trino Iceberg catalog is configured here:

```text
docker/trino/catalog/iceberg.properties
```

It connects to:

- Hive Metastore: `thrift://hive-metastore:9083`
- MinIO endpoint: `http://minio:9000`
- MinIO bucket: `warehouse`

## Why Trino

Hive Metastore is only metadata. It is not the SQL query interface.

Trino is the query engine that reads the Iceberg table metadata from Hive Metastore and reads the data files from MinIO.
