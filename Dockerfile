# jlink ligger ikke i jre lengere (etter java 21)
FROM eclipse-temurin:21-jdk-alpine as jre

# --strip-debug uses objcopy from binutils
RUN apk add binutils

# Build small JRE image
RUN jlink \
    --verbose \
    --module-path $JAVA_HOME/bin/jmods/ \
    --add-modules java.base,java.desktop,java.management,java.naming,java.net.http,java.security.jgss,java.security.sasl,java.sql,jdk.httpserver,jdk.unsupported,jdk.crypto.ec,java.instrument \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /customjre


FROM scratch as javaagent
ARG JAVA_OTEL_VERSION=v2.1.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/$JAVA_OTEL_VERSION/opentelemetry-javaagent.jar /otel/javaagent.jar


FROM alpine:3.19.1 as app
ENV JAVA_HOME=/jre
ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=jre /customjre $JAVA_HOME
COPY --from=javaagent /otel/javaagent.jar javaagent.jar
COPY /app/build/libs/app-all.jar app.jar

CMD ["java", "-javaagent:javaagent.jar", "-Djdk.tls.client.protocols=TLSv1.2", "-XX:ActiveProcessorCount=2", "-jar", "app.jar"]

# use -XX:+UseParallelGC when 2 CPUs and 4G RAM.
# use G1GC when using more than 4G RAM and/or more than 2 CPUs
# use -XX:ActiveProcessorCount=2 if less than 1G RAM.
