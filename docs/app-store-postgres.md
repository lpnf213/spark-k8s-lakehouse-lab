# App Store Operational Postgres Demo

This Docker setup creates a synthetic App Store operational database for Spark JDBC ingestion practice.

## Start Postgres

```powershell
docker compose -f docker-compose.postgres.yml up -d --build
```

The database is available on:

- Host: `localhost`
- Port: `5432`
- Database: `appstore`
- User: `appstore`
- Password: `appstore`
- Docker network IP: `172.29.10.10`

Inside the same Docker network, prefer the service name:

```text
app-store-postgres:5432
```

From the host machine, prefer:

```text
localhost:5432
```

## Default Volume

The default seed creates:

- `100000` users
- `5000` apps
- `1000000` app downloads
- `250000` reviews
- `300000` purchases
- `100000` subscriptions

This is enough to demonstrate Spark JDBC partitioning, joins, aggregations, and shuffle behavior without making local setup painfully slow.

## Larger Interview Demo

For a heavier local volume, edit `docker-compose.postgres.yml`:

```yaml
APP_STORE_USERS: 500000
APP_STORE_APPS: 10000
APP_STORE_DOWNLOADS: 10000000
APP_STORE_REVIEWS: 2000000
APP_STORE_PURCHASES: 3000000
APP_STORE_SUBSCRIPTIONS: 1000000
```

Then recreate the database volume:

```powershell
docker compose -f docker-compose.postgres.yml down -v
docker compose -f docker-compose.postgres.yml up -d --build
```

## Spark Problem Statement

Read the operational App Store database with Spark JDBC and produce analytical datasets:

1. Daily downloads per app and country.
2. Top 10 apps by revenue per category.
3. Average rating per app over time.
4. Active subscriptions by plan type.
5. Failed payment rate by country and day.

Important interview points:

- Use JDBC partitioning for large tables.
- Avoid reading large operational tables with one JDBC connection.
- Push filters down where possible.
- Join dimensions like `apps` and `app_categories` after reading facts.
- Write curated outputs to Parquet or Iceberg.

## Example JDBC Options

```scala
val downloads = spark.read
  .format("jdbc")
  .option("url", "jdbc:postgresql://host.docker.internal:5432/appstore")
  .option("dbtable", "app_downloads")
  .option("user", "appstore")
  .option("password", "appstore")
  .option("driver", "org.postgresql.Driver")
  .option("partitionColumn", "download_id")
  .option("lowerBound", "1")
  .option("upperBound", "1000000")
  .option("numPartitions", "8")
  .load()
```

When Spark runs inside Kubernetes, replace `host.docker.internal` with the reachable Postgres service or host IP.

## Run Inside Kubernetes

After your Vagrant kubeadm cluster is ready:

```powershell
kubectl apply -f k8s/app-store-postgres.yaml
kubectl -n app-store get pods
kubectl -n app-store logs deploy/app-store-postgres
```

Spark jobs running in the same Kubernetes cluster can use:

```text
jdbc:postgresql://app-store-postgres.app-store.svc.cluster.local:5432/appstore
```

For this local interview demo, the Kubernetes manifest uses `emptyDir` storage. That keeps setup simple and avoids needing a dynamic storage provisioner in the Vagrant cluster. In a production explanation, say you would use a `StatefulSet` with a persistent volume.

## Big Data Interview Angle

This database intentionally behaves like an operational source, not a data lake:

- Fact tables like `app_downloads`, `app_purchases`, and `app_reviews` can grow to millions of rows.
- Spark should read large tables with JDBC partitioning.
- Aggregations by day, country, category, and app create realistic shuffle scenarios.
- The operational database should not be overloaded by too many Spark JDBC partitions.
- Curated outputs should be written to analytical storage like Iceberg or Parquet.
