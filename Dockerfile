# jlink ligger ikke i jre lengere (etter java 21)
FROM eclipse-temurin:21-jdk-alpine as jre

# --strip-debug uses objcopy from binutils
RUN apk add binutils

# Build small JRE image
RUN jlink \
    --verbose \
    --module-path $JAVA_HOME/bin/jmods/ \
    --add-modules java.base,java.desktop,java.management,java.naming,java.net.http,java.security.jgss,java.security.sasl,java.sql,jdk.httpserver,jdk.unsupported,jdk.crypto.ec \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /customjre

FROM alpine:3.19.1 as app
ENV JAVA_HOME=/jre
ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN curl -L -O https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

COPY --from=jre /customjre $JAVA_HOME
COPY /app/build/libs/app-all.jar app.jar

ARG APP_NAME
ENV JRE_OPTS -javaagent:./opentelemetry-javaagent.jar -Dotel.resource.attributes=service.name=$APP_NAME

CMD ["java", "$JRE_OPTS", "-XX:ActiveProcessorCount=2", "-jar", "app.jar"]

# use -XX:+UseParallelGC when 2 CPUs and 4G RAM.
# use G1GC when using more than 4G RAM and/or more than 2 CPUs
# use -XX:ActiveProcessorCount=2 if less than 1G RAM.
