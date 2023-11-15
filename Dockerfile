FROM alpine:3.18.2 as app
RUN apk --update --no-cache
COPY /app/build/libs/app-all.jar app.jar

FROM eclipse-temurin:20.0.2_9-jre-alpine
ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"
RUN apk --update --no-cache
COPY --from=app app.jar .
CMD ["java", "-XX:ActiveProcessorCount=2", "-jar", "app.jar"]

# use -XX:+UseParallelGC when 2 CPUs and 4G RAM.
# use G1GC when using more than 4G RAM and/or more than 2 CPUs
# use -XX:ActiveProcessorCount=2 if less than 1G RAM.