package com.example.spark.operations.batch

final case class JobConfig(
    // PostgreSQL JDBC connection used by Spark readers.
    jdbcUrl: String,
    jdbcUser: String,
    jdbcPassword: String,
    jdbcDriver: String,
    // Iceberg catalog and database where raw tables are written.
    icebergCatalog: String,
    rawDatabase: String,
    // full_overwrite replaces raw tables; delta_append appends from the last watermark.
    ingestionMode: String,
    // Number of parallel JDBC partitions Spark creates for each source table.
    jdbcPartitions: Int,
    // Upper bounds are used only for Spark JDBC partition planning, not row filtering.
    usersUpperBound: Long,
    appsUpperBound: Long,
    subscriptionsUpperBound: Long
)

object JobConfig {
  def fromEnv(): JobConfig =
    JobConfig(
      jdbcUrl = env("APPSTORE_JDBC_URL", "jdbc:postgresql://app-store-postgres:5432/appstore"),
      jdbcUser = env("APPSTORE_JDBC_USER", "appstore"),
      jdbcPassword = env("APPSTORE_JDBC_PASSWORD", "appstore"),
      jdbcDriver = env("APPSTORE_JDBC_DRIVER", "org.postgresql.Driver"),
      icebergCatalog = env("ICEBERG_CATALOG", "lakehouse"),
      rawDatabase = env("ICEBERG_RAW_DATABASE", "raw_app_store"),
      ingestionMode = env("INGESTION_MODE", "delta_append"),
      jdbcPartitions = env("JDBC_PARTITIONS", "8").toInt,
      usersUpperBound = env("APPSTORE_USERS_UPPER_BOUND", "100000").toLong,
      appsUpperBound = env("APPSTORE_APPS_UPPER_BOUND", "5000").toLong,
      subscriptionsUpperBound = env("APPSTORE_SUBSCRIPTIONS_UPPER_BOUND", "100000").toLong
    )

  private def env(name: String, defaultValue: String): String =
    sys.env.get(name).filter(_.nonEmpty).getOrElse(defaultValue)
}
