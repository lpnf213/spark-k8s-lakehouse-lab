# Lakehouse Stack: MinIO, Hive Metastore, and Iceberg

This stack simulates a production-style lakehouse for Spark jobs:

```text
Spark on Kubernetes
  -> Iceberg table format
  -> Hive Metastore catalog
  -> MinIO S3-compatible object storage
```

## Services

The Compose stack creates:

- `minio` as S3-compatible object storage.
- `minio-init` to create the `warehouse` and `spark-events` buckets.
- `hive-metastore-db` as the PostgreSQL backend for Hive Metastore metadata.
- `hive-metastore` as the Thrift metastore service used by Spark Iceberg catalogs.
- `trino` as the SQL query engine for Iceberg tables.

## Static IPs

The stack uses a dedicated Docker network:

```text
172.28.10.0/24
```

Service IPs:

- MinIO: `172.28.10.10`
- Hive Metastore PostgreSQL DB: `172.28.10.20`
- Hive Metastore Thrift service: `172.28.10.30`
- Trino: `172.28.10.40`

Inside Docker, prefer service names:

- `minio:9000`
- `hive-metastore:9083`
- `hive-metastore-db:5432`
- `trino:8080`

## Start The Stack

```powershell
docker compose -f docker-compose.lakehouse.yml up -d --build
```

Check services:

```powershell
docker compose -f docker-compose.lakehouse.yml ps
docker compose -f docker-compose.lakehouse.yml logs -f hive-metastore
```

## MinIO Console

Open:

```text
http://localhost:9001
```

Login:

- User: `minioadmin`
- Password: `minioadmin`

Buckets created automatically:

- `warehouse`
- `spark-events`

## Hive Metastore

The Hive Metastore Thrift endpoint is:

```text
thrift://hive-metastore:9083
```

From outside Docker, use:

```text
thrift://localhost:9083
```

## Trino

Open the Trino UI:

```text
http://localhost:8080
```

Run the Trino CLI:

```powershell
docker exec -it lakehouse-trino trino
```

Example:

```sql
SHOW TABLES FROM iceberg.raw_app_store;
SELECT count(*) FROM iceberg.raw_app_store.users;
```

More details:

```text
docs/trino-query-interface.md
```

## Iceberg Important Note

Iceberg is not a database and does not run as a server.

In this architecture:

- MinIO stores the table data files and metadata files.
- Hive Metastore stores catalog pointers and table metadata references.
- Spark uses the Iceberg runtime library to read/write Iceberg tables.

So the Spark image or Spark submit command must include the Iceberg Spark runtime dependency.

## Spark Iceberg Catalog Example

For Spark running in Docker Compose or Kubernetes with access to these service names:

```text
--conf spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions
--conf spark.sql.catalog.lakehouse=org.apache.iceberg.spark.SparkCatalog
--conf spark.sql.catalog.lakehouse.type=hive
--conf spark.sql.catalog.lakehouse.uri=thrift://hive-metastore:9083
--conf spark.sql.catalog.lakehouse.warehouse=s3a://warehouse/
--conf spark.hadoop.fs.s3a.endpoint=http://minio:9000
--conf spark.hadoop.fs.s3a.access.key=minioadmin
--conf spark.hadoop.fs.s3a.secret.key=minioadmin
--conf spark.hadoop.fs.s3a.path.style.access=true
--conf spark.hadoop.fs.s3a.connection.ssl.enabled=false
```

Example packages for Spark 3.5 with Scala 2.12:

```text
--packages org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.6.1,org.apache.hadoop:hadoop-aws:3.3.6
```

If your Spark distribution uses a different Spark or Scala binary version, change the Iceberg package coordinate to match.

## Interview Explanation

Use this short explanation:

> I created a local lakehouse using MinIO as S3-compatible object storage, Hive Metastore as the Iceberg catalog backend, and Spark as the processing engine. Iceberg manages table metadata, schema evolution, snapshots, and ACID-style commits, while MinIO stores the actual Parquet data and metadata files.

## Production Notes

For a real production environment:

- Use real object storage like S3, ADLS, or GCS.
- Use managed PostgreSQL for the Hive Metastore backend.
- Store credentials in secrets, not Compose files.
- Pin Docker image versions instead of using `latest`.
- Add persistent volumes and backup policies.
- Run Spark with controlled executor resources and proper service accounts.
