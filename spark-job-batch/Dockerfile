FROM eclipse-temurin:17-jdk-jammy AS builder

ARG SBT_VERSION=1.10.2

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates tar gzip \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL "https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz" \
    | tar -xz -C /opt \
    && ln -s /opt/sbt/bin/sbt /usr/local/bin/sbt

WORKDIR /build

COPY build.sbt ./build.sbt
COPY project ./project
COPY src ./src

RUN sbt package

FROM apache/spark:3.5.1-scala2.12-java17-python3-ubuntu

USER root

ARG ICEBERG_VERSION=1.6.1
ARG POSTGRES_JDBC_VERSION=42.7.3
ARG HADOOP_AWS_VERSION=3.3.4
ARG AWS_SDK_BUNDLE_VERSION=1.12.262

ADD https://repo1.maven.org/maven2/org/apache/iceberg/iceberg-spark-runtime-3.5_2.12/${ICEBERG_VERSION}/iceberg-spark-runtime-3.5_2.12-${ICEBERG_VERSION}.jar /opt/spark/jars/
ADD https://repo1.maven.org/maven2/org/postgresql/postgresql/${POSTGRES_JDBC_VERSION}/postgresql-${POSTGRES_JDBC_VERSION}.jar /opt/spark/jars/
ADD https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/${HADOOP_AWS_VERSION}/hadoop-aws-${HADOOP_AWS_VERSION}.jar /opt/spark/jars/
ADD https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/${AWS_SDK_BUNDLE_VERSION}/aws-java-sdk-bundle-${AWS_SDK_BUNDLE_VERSION}.jar /opt/spark/jars/

COPY --from=builder /build/target/scala-2.12/spark-job-batch_2.12-0.1.0-SNAPSHOT.jar /opt/spark/app/spark-job-batch.jar

RUN chmod 0644 /opt/spark/jars/iceberg-spark-runtime-3.5_2.12-${ICEBERG_VERSION}.jar \
    /opt/spark/jars/postgresql-${POSTGRES_JDBC_VERSION}.jar \
    /opt/spark/jars/hadoop-aws-${HADOOP_AWS_VERSION}.jar \
    /opt/spark/jars/aws-java-sdk-bundle-${AWS_SDK_BUNDLE_VERSION}.jar \
    /opt/spark/app/spark-job-batch.jar

USER 185
