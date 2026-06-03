FROM apache/hive:4.0.0

ARG POSTGRES_JDBC_VERSION=42.7.3
ARG ICEBERG_VERSION=1.6.1
ARG HADOOP_AWS_VERSION=3.3.6
ARG AWS_SDK_BUNDLE_VERSION=1.12.367

USER root

ADD https://repo1.maven.org/maven2/org/postgresql/postgresql/${POSTGRES_JDBC_VERSION}/postgresql-${POSTGRES_JDBC_VERSION}.jar /opt/hive/lib/postgresql-${POSTGRES_JDBC_VERSION}.jar
ADD https://repo1.maven.org/maven2/org/apache/iceberg/iceberg-hive-runtime/${ICEBERG_VERSION}/iceberg-hive-runtime-${ICEBERG_VERSION}.jar /opt/hive/lib/iceberg-hive-runtime-${ICEBERG_VERSION}.jar
ADD https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/${HADOOP_AWS_VERSION}/hadoop-aws-${HADOOP_AWS_VERSION}.jar /opt/hive/lib/hadoop-aws-${HADOOP_AWS_VERSION}.jar
ADD https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/${AWS_SDK_BUNDLE_VERSION}/aws-java-sdk-bundle-${AWS_SDK_BUNDLE_VERSION}.jar /opt/hive/lib/aws-java-sdk-bundle-${AWS_SDK_BUNDLE_VERSION}.jar

COPY conf/metastore-site.xml /opt/hive/conf/metastore-site.xml
COPY entrypoint.sh /opt/hive/bin/start-metastore.sh

RUN chmod 0644 /opt/hive/lib/postgresql-${POSTGRES_JDBC_VERSION}.jar \
    /opt/hive/lib/iceberg-hive-runtime-${ICEBERG_VERSION}.jar \
    /opt/hive/lib/hadoop-aws-${HADOOP_AWS_VERSION}.jar \
    /opt/hive/lib/aws-java-sdk-bundle-${AWS_SDK_BUNDLE_VERSION}.jar \
    && chmod 0755 /opt/hive/bin/start-metastore.sh \
    && chown -R hive:hive /opt/hive/conf /opt/hive/bin/start-metastore.sh

USER hive

EXPOSE 9083

ENTRYPOINT ["/opt/hive/bin/start-metastore.sh"]
