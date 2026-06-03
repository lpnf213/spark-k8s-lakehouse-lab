package com.example.spark.operations.batch

final case class JdbcTableSpec(
    sourceTable: String,
    targetTable: String,
    // Primary key used by delta MERGE to match source rows with existing Iceberg rows.
    primaryKeyColumn: String,
    // Numeric column Spark uses to split JDBC reads into parallel partitions.
    partitionColumn: String,
    // Timestamp column used by delta_append to find and read new source rows.
    watermarkColumn: String,
    // Bounds define partition ranges for Spark JDBC. They do not filter the final dataset.
    lowerBound: Long,
    upperBound: Long
)

object JdbcTableSpec {
  def appStoreRawTables(config: JobConfig): Seq[JdbcTableSpec] =
    Seq(
      JdbcTableSpec(
        sourceTable = "users",
        targetTable = "users",
        primaryKeyColumn = "user_id",
        partitionColumn = "user_id",
        watermarkColumn = "created_at",
        lowerBound = 1L,
        upperBound = config.usersUpperBound
      ),
      JdbcTableSpec(
        sourceTable = "apps",
        targetTable = "apps",
        primaryKeyColumn = "app_id",
        partitionColumn = "app_id",
        watermarkColumn = "created_at",
        lowerBound = 1L,
        upperBound = config.appsUpperBound
      ),
      JdbcTableSpec(
        sourceTable = "subscriptions",
        targetTable = "subscriptions",
        primaryKeyColumn = "subscription_id",
        partitionColumn = "subscription_id",
        watermarkColumn = "started_at",
        lowerBound = 1L,
        upperBound = config.subscriptionsUpperBound
      )
    )
}
