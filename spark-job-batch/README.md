# Spark Batch Job: App Store Raw Ingestion

This Spark job reads PostgreSQL operational tables and writes raw Apache Iceberg tables to MinIO using Hive Metastore.

The purpose of this subproject is to study what each Spark-on-Kubernetes step does. There are no PowerShell helper scripts on purpose: every command is explicit so you can see the moving parts.

The full command runbook lives in the main repository README:

```text
../README.md
```

## What It Reads

- `users`
- `apps`
- `subscriptions`

## What It Writes

- `lakehouse.raw_app_store.users`
- `lakehouse.raw_app_store.apps`
- `lakehouse.raw_app_store.subscriptions`

Files are stored in MinIO under:

```text
warehouse/raw_app_store.db/
```

## Default Mode

```text
delta_append
```

In `delta_append` mode, the job reads the latest watermark already present in Iceberg, reads PostgreSQL from that value inclusively, and uses Iceberg `MERGE INTO` to make reruns safer.

## Manual Kubernetes Run

Run all commands from PowerShell.

### 1. Start Source And Lakehouse Services

```powershell
cd C:\repository\spark-datalakeuhouse
docker compose -f docker-compose.postgres.yml up -d --build
docker compose -f docker-compose.lakehouse.yml up -d --build
```

This starts:

- PostgreSQL source database.
- MinIO object storage.
- Hive Metastore and its PostgreSQL metadata database.
- Trino query engine.

### 2. Verify Kubernetes

```powershell
cd C:\repository\spark-datalakeuhouse\vagrant-kubeadm-kubernetes
vagrant status
vagrant ssh controlplane -c "kubectl get nodes"
```

Expected nodes:

```text
controlplane
node01
node02
```

All nodes should be `Ready`.

### 3. Build The Spark Image

```powershell
cd C:\repository\spark-datalakeuhouse\spark-job-batch
docker build -t spark-job-batch:0.1.3 .
```

The Dockerfile compiles the Scala job inside Docker, so you do not need SBT installed locally.

### 4. Start The Local Docker Registry

Run this only if the registry is not already running:

```powershell
docker ps --filter name=registry
docker run -d -p 5000:5000 --restart always --name registry registry:2
```

If the registry container already exists, the `docker run` command can fail with a name conflict. That is OK.

### 5. Push The Spark Image

```powershell
docker tag spark-job-batch:0.1.3 localhost:5000/spark-job-batch:0.1.3
docker push localhost:5000/spark-job-batch:0.1.3
```

The Kubernetes manifest pulls the image as:

```text
192.168.31.1:5000/spark-job-batch:0.1.3
```

If your host adapter IP changes, update:

```text
spark-job-batch/k8s/external-docker-services.example.yaml
spark-job-batch/k8s/spark-submit-app-store-raw-ingestion.yaml
```

### 6. Configure CRI-O For The Local HTTP Registry

The local registry uses HTTP. CRI-O may try HTTPS and fail with:

```text
http: server gave HTTP response to HTTPS client
```

Configure the registry as insecure on every Kubernetes node:

```powershell
cd C:\repository\spark-datalakeuhouse\vagrant-kubeadm-kubernetes

vagrant ssh controlplane -c "sudo bash /vagrant/scripts/configure-local-registry.sh 192.168.31.1:5000"
vagrant ssh node01 -c "sudo bash /vagrant/scripts/configure-local-registry.sh 192.168.31.1:5000"
vagrant ssh node02 -c "sudo bash /vagrant/scripts/configure-local-registry.sh 192.168.31.1:5000"
```

### 7. Apply Spark RBAC

```powershell
cd C:\repository\spark-datalakeuhouse\vagrant-kubeadm-kubernetes
Get-Content -Raw -LiteralPath ..\spark-job-batch\k8s\spark-service-account.yaml | vagrant ssh controlplane -c "kubectl apply -f -"
```

This creates the `spark` service account and permissions that allow the driver pod to create executor pods.

### 8. Expose Docker Services To Kubernetes

```powershell
Get-Content -Raw -LiteralPath ..\spark-job-batch\k8s\external-docker-services.example.yaml | vagrant ssh controlplane -c "kubectl apply -f -"
```

This creates Kubernetes services for Docker-host services:

- `app-store-postgres.spark-jobs.svc.cluster.local`
- `minio.spark-jobs.svc.cluster.local`
- `hive-metastore.spark-jobs.svc.cluster.local`

### 9. Delete Previous Spark Run

```powershell
vagrant ssh controlplane -c "kubectl -n spark-jobs delete job spark-submit-app-store-raw-ingestion --ignore-not-found"
vagrant ssh controlplane -c "kubectl -n spark-jobs delete pod -l spark-app-name=app-store-raw-ingestion --ignore-not-found"
```

This keeps reruns clean.

### 10. Submit The Spark Job

```powershell
Get-Content -Raw -LiteralPath ..\spark-job-batch\k8s\spark-submit-app-store-raw-ingestion.yaml | vagrant ssh controlplane -c "kubectl apply -f -"
```

What happens:

1. Kubernetes creates the `spark-submit-app-store-raw-ingestion` job.
2. The submit pod runs `/opt/spark/bin/spark-submit`.
3. Spark creates the driver pod.
4. The driver creates executor pods.
5. Executors read PostgreSQL and write Iceberg data to MinIO.

### 11. Watch Pods

```powershell
vagrant ssh controlplane -c "kubectl -n spark-jobs get pods -w"
```

Expected resources:

- One submit job pod.
- One Spark driver pod.
- One or more Spark executor pods.

### 12. Read Logs

Submit job logs:

```powershell
vagrant ssh controlplane -c "kubectl -n spark-jobs logs job/spark-submit-app-store-raw-ingestion"
```

Spark driver logs:

```powershell
vagrant ssh controlplane -c "kubectl -n spark-jobs logs -l spark-role=driver --tail=200"
```

Describe a failed pod:

```powershell
vagrant ssh controlplane -c "kubectl -n spark-jobs describe pod <pod-name>"
```

### 13. Verify Output In MinIO

Open:

```text
http://localhost:9001
```

Login:

```text
minioadmin / minioadmin
```

Open bucket:

```text
warehouse
```

Expected folders:

```text
raw_app_store.db/users/
raw_app_store.db/apps/
raw_app_store.db/subscriptions/
```

### 14. Verify Output With Trino

```powershell
docker exec -it lakehouse-trino trino
```

Run:

```sql
SHOW TABLES FROM iceberg.raw_app_store;

SELECT count(*) FROM iceberg.raw_app_store.users;
SELECT count(*) FROM iceberg.raw_app_store.apps;
SELECT count(*) FROM iceberg.raw_app_store.subscriptions;
```

## Kubernetes UI

Use the Kubernetes Dashboard to inspect pods, services, logs, events, and namespaces.

From PowerShell:

```powershell
cd C:\repository\spark-datalakeuhouse\vagrant-kubeadm-kubernetes
vagrant ssh controlplane -c "kubectl proxy --address=127.0.0.1 --port=8001" -- -L 8001:127.0.0.1:8001
```

Keep the terminal open, then open:

```text
http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/services/https:kubernetes-dashboard:/proxy/
```

Use this token:

```text
C:\repository\spark-datalakeuhouse\vagrant-kubeadm-kubernetes\configs\token
```

More details: `docs/kubernetes-dashboard-access.md`.

## Spark Job UI

The Spark UI runs inside the driver pod on port `4040`. It exists only while the driver pod is running.

Find the driver pod:

```powershell
cd C:\repository\spark-datalakeuhouse\vagrant-kubeadm-kubernetes
vagrant ssh controlplane -c "kubectl -n spark-jobs get pods -l spark-role=driver"
```

Copy the driver pod name and port-forward it:

```powershell
vagrant ssh controlplane -c "kubectl -n spark-jobs port-forward pod/<driver-pod-name> 4040:4040 --address=127.0.0.1" -- -L 4040:127.0.0.1:4040
```

Keep the terminal open, then open:

```text
http://localhost:4040
```

Use the Spark UI to inspect:

- Jobs, stages, and tasks.
- Executor usage.
- SQL queries and Iceberg writes.
- Failed stages and stack traces.

## Runtime Configuration

Main class:

```text
com.example.spark.operations.batch.AppStoreRawIngestionJob
```

App image:

```text
192.168.31.1:5000/spark-job-batch:0.1.3
```

PostgreSQL JDBC URL:

```text
jdbc:postgresql://app-store-postgres.spark-jobs.svc.cluster.local:5432/appstore
```

Hive Metastore URI:

```text
thrift://hive-metastore.spark-jobs.svc.cluster.local:9083
```

MinIO endpoint:

```text
http://minio.spark-jobs.svc.cluster.local:9000
```

Iceberg warehouse:

```text
s3a://warehouse/
```

## Spark JDBC Strategy

Spark reads PostgreSQL using JDBC partitioning:

```text
partitionColumn
lowerBound
upperBound
numPartitions
```

Important:

```text
lowerBound and upperBound do not filter rows.
They only define how Spark splits JDBC read ranges.
```

Actual delta filtering is pushed down with a PostgreSQL subquery:

```sql
WHERE watermark_column >= last_watermark
```

## Delta Append Watermarks

| Table | Watermark | Primary Key |
| --- | --- | --- |
| `users` | `created_at` | `user_id` |
| `apps` | `created_at` | `app_id` |
| `subscriptions` | `started_at` | `subscription_id` |

The job creates:

```text
_raw_partition_date = to_date(watermark_column)
```

Delta mode uses `MERGE INTO` with:

```text
primary key + _raw_partition_date
```

## Dynamic Executors

The Kubernetes submit manifest enables Spark dynamic allocation:

```text
spark.dynamicAllocation.enabled=true
spark.dynamicAllocation.shuffleTracking.enabled=true
spark.dynamicAllocation.initialExecutors=1
spark.dynamicAllocation.minExecutors=1
spark.dynamicAllocation.maxExecutors=10
spark.dynamicAllocation.executorIdleTimeout=60s
```

Spark on Kubernetes does not use the traditional external shuffle service by default. Shuffle tracking lets Spark safely remove idle executors without losing shuffle metadata.
