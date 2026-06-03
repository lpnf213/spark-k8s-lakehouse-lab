package com.example.spark.operations.batch

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.{col, current_timestamp, lit, to_date}

object AppStoreRawIngestionJob {

  def main(args: Array[String]): Unit = {
    val config = JobConfig.fromEnv()

    val spark = SparkSession
      .builder()
      .appName("app-store-raw-ingestion")
      .getOrCreate()

    try {
      createRawDatabase(spark, config)

      JdbcTableSpec.appStoreRawTables(config).foreach { table =>
        ingestTable(spark, config, table)
      }
    } finally {
      spark.stop()
    }
  }

  private def createRawDatabase(spark: SparkSession, config: JobConfig): Unit =
    spark.sql(s"CREATE DATABASE IF NOT EXISTS ${config.icebergCatalog}.${config.rawDatabase}")

  private def ingestTable(spark: SparkSession, config: JobConfig, table: JdbcTableSpec): Unit = {
    val targetIdentifier = s"${config.icebergCatalog}.${config.rawDatabase}.${table.targetTable}"

    // In delta mode, read the latest timestamp already written to Iceberg.
    // This becomes the inclusive lower bound for the next PostgreSQL read.
    val lowerWatermark = latestWatermark(spark, config, table, targetIdentifier)

    println(
      s"Reading PostgreSQL table ${table.sourceTable} with mode ${config.ingestionMode}" +
        lowerWatermark.map(value => s" from ${table.watermarkColumn} >= '$value'").getOrElse("")
    )

    val rawData = readJdbcTable(spark, config, table, lowerWatermark)
      .withColumn("_raw_source_system", lit("appstore-postgres"))
      .withColumn("_raw_source_table", lit(table.sourceTable))
      .withColumn("_raw_ingested_at", current_timestamp())
      // Explicit partition column created from the table watermark.
      // This makes the physical Iceberg partition visible and easy to use in MERGE.
      .withColumn("_raw_partition_date", to_date(col(table.watermarkColumn)))

    config.ingestionMode match {
      case "full_overwrite" =>
        // Full mode recreates the raw Iceberg table from the operational source.
        // This is useful for first loads, demos, and recovery scenarios.
        println(s"Replacing Iceberg table $targetIdentifier")
        replacePartitionedTable(spark, rawData, table, targetIdentifier)
      case "delta_append" =>
        if (tableExists(spark, config, table)) {
          // Delta mode merges rows from the source watermark onward.
          // Because the watermark filter is inclusive, reruns may duplicate the boundary timestamp.
          // MERGE makes the rerun idempotent by updating matched primary keys and inserting new keys.
          println(s"Merging delta rows into Iceberg table $targetIdentifier")
          mergeIntoPartitionedTable(spark, rawData, table, targetIdentifier)
        } else {
          // If the raw table does not exist yet, delta mode safely performs the first load.
          println(s"Creating partitioned Iceberg table $targetIdentifier")
          createPartitionedTable(spark, rawData, table, targetIdentifier)
        }
      case other =>
        throw new IllegalArgumentException(
          s"Unsupported INGESTION_MODE '$other'. Use 'delta_append' or 'full_overwrite'."
        )
    }
  }

  private def readJdbcTable(
      spark: SparkSession,
      config: JobConfig,
      table: JdbcTableSpec,
      lowerWatermark: Option[String]
  ): DataFrame = {
    // Spark JDBC accepts either a table name or a subquery in dbtable.
    // For delta reads, we use a pushed-down PostgreSQL filter so Spark does not scan
    // the entire operational source table before applying the watermark condition.
    val dbtable = lowerWatermark match {
      case Some(value) =>
        s"(SELECT * FROM ${table.sourceTable} WHERE ${table.watermarkColumn} >= TIMESTAMPTZ '$value') AS ${table.sourceTable}_delta"
      case None =>
        table.sourceTable
    }

    spark.read
      .format("jdbc")
      // JDBC URL points to the operational PostgreSQL database.
      .option("url", config.jdbcUrl)
      // dbtable is either the physical table or a filtered subquery for delta mode.
      .option("dbtable", dbtable)
      .option("user", config.jdbcUser)
      .option("password", config.jdbcPassword)
      .option("driver", config.jdbcDriver)
      // partitionColumn must be numeric, date, or timestamp. Here we use numeric IDs.
      // Spark creates WHERE ranges over this column and opens multiple JDBC connections.
      .option("partitionColumn", table.partitionColumn)
      // lowerBound and upperBound define how Spark calculates partition ranges.
      // They are not strict filters; all rows from dbtable are still eligible to be read.
      .option("lowerBound", table.lowerBound.toString)
      .option("upperBound", table.upperBound.toString)
      // numPartitions controls parallelism and maximum JDBC connections per table read.
      // Higher values can improve throughput but may overload the operational database.
      .option("numPartitions", config.jdbcPartitions.toString)
      .load()
      .select(col("*"))
  }

  private def replacePartitionedTable(
      spark: SparkSession,
      rawData: DataFrame,
      table: JdbcTableSpec,
      targetIdentifier: String
  ): Unit = {
    spark.sql(s"DROP TABLE IF EXISTS $targetIdentifier")
    createPartitionedTable(spark, rawData, table, targetIdentifier)
  }

  private def createPartitionedTable(
      spark: SparkSession,
      rawData: DataFrame,
      table: JdbcTableSpec,
      targetIdentifier: String
  ): Unit = {
    val tempView = tempViewName(table.targetTable)
    rawData.createOrReplaceTempView(tempView)

    // Raw tables are physically partitioned by _raw_partition_date.
    // The value is derived from the source watermark column, for example:
    // created_at -> _raw_partition_date for users/apps
    // started_at -> _raw_partition_date for subscriptions
    spark.sql(
      s"""
         |CREATE TABLE $targetIdentifier
         |USING iceberg
         |PARTITIONED BY (_raw_partition_date)
         |AS SELECT * FROM $tempView
         |""".stripMargin
    )
  }

  private def mergeIntoPartitionedTable(
      spark: SparkSession,
      rawData: DataFrame,
      table: JdbcTableSpec,
      targetIdentifier: String
  ): Unit = {
    val tempView = tempViewName(table.targetTable)
    rawData.createOrReplaceTempView(tempView)

    val columns = rawData.columns
    val updateAssignments = columns.map(column => s"target.$column = source.$column").mkString(", ")
    val insertColumns = columns.mkString(", ")
    val insertValues = columns.map(column => s"source.$column").mkString(", ")

    // MERGE uses both:
    // 1. Primary key: identifies the business row.
    // 2. Partition date: limits matching to the partition derived from the watermark.
    // This keeps reruns idempotent while helping Iceberg prune affected partitions.
    spark.sql(
      s"""
         |MERGE INTO $targetIdentifier AS target
         |USING $tempView AS source
         |ON target.${table.primaryKeyColumn} = source.${table.primaryKeyColumn}
         |AND target._raw_partition_date = source._raw_partition_date
         |WHEN MATCHED THEN UPDATE SET $updateAssignments
         |WHEN NOT MATCHED THEN INSERT ($insertColumns) VALUES ($insertValues)
         |""".stripMargin
    )
  }

  private def latestWatermark(
      spark: SparkSession,
      config: JobConfig,
      table: JdbcTableSpec,
      targetIdentifier: String
  ): Option[String] =
    if (config.ingestionMode == "delta_append" && tableExists(spark, config, table)) {
      // Collecting one aggregate value to the driver is safe: this query returns one row.
      // The value is used only to build the next JDBC source query.
      spark
        .sql(s"SELECT CAST(MAX(${table.watermarkColumn}) AS STRING) AS watermark FROM $targetIdentifier")
        .collect()
        .headOption
        .flatMap(row => Option(row.getAs[String]("watermark")))
    } else {
      None
    }

  private def tableExists(spark: SparkSession, config: JobConfig, table: JdbcTableSpec): Boolean =
    spark
      .sql(s"SHOW TABLES IN ${config.icebergCatalog}.${config.rawDatabase} LIKE '${table.targetTable}'")
      .count() > 0

  private def tempViewName(tableName: String): String =
    s"${tableName}_raw_input"
}
