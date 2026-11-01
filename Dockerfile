# syntax=docker/dockerfile:1
# Multi-stage build (ADR-034). Build: docker build -t franchiseads:local .

FROM eclipse-temurin:21.0.13_9-jdk-noble AS build
WORKDIR /build

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src/main src/main

# Tests are skipped here on purpose: CI runs the full suite with ./mvnw verify, and the integration tests need
# Testcontainers, which needs a Docker daemon that docker build can't reach. maven.test.skip also skips compiling
# them, so src/test isn't in the build context at all (.dockerignore).
# The cache mount keeps ~/.m2 (dependencies and the wrapper's Maven) between builds without baking it into a layer,
# so a code-only change rebuilds without downloading anything.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B package -Dmaven.test.skip=true \
    && cp target/franchiseads-*.jar application.jar

# Boot 4.1's jar tools split the jar into layers, least to most often changed.
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:21.0.13_9-jre-noble
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app

COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

USER app
EXPOSE 8080

# Container support is on by default in JDK 21; size the heap from the container's memory limit instead of the
# default 25%. Override at run time with -e JAVA_TOOL_OPTIONS=...
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "application.jar"]
