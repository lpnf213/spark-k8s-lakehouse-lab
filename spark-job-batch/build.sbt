ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.12.18"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = project
  .in(file("."))
  .settings(
    name := "spark-job-batch",
    Compile / mainClass := Some("com.example.spark.operations.batch.AppStoreRawIngestionJob"),
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-sql" % "3.5.1" % Provided,
      "org.apache.iceberg" %% "iceberg-spark-runtime-3.5" % "1.6.1" % Provided,
      "org.postgresql" % "postgresql" % "42.7.3" % Provided
    )
  )
