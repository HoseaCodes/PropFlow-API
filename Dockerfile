# ---------------------------------------------------------------------------
# Build stage
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src

# A BuildKit cache mount persists the Maven repository between builds without
# baking it into an image layer, so a source-only change reuses every previously
# downloaded artifact.
#
# The obvious alternative -- a separate `dependency:go-offline` layer keyed on
# pom.xml -- was tried and removed. That goal also resolves plugin dependencies,
# including optional ones this project never uses (it went looking for Jetty and
# javax.servlet), which makes it slow and prone to failing on artifacts the build
# does not actually need.
#
# Tests are skipped here deliberately. They need a Docker daemon for
# Testcontainers, which is not available inside an image build, and CI is the
# right place to gate on them -- a green pipeline is the evidence, not a side
# effect of packaging.
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests

# ---------------------------------------------------------------------------
# Runtime stage
# ---------------------------------------------------------------------------
# A JRE, not a JDK: the previous image shipped a full toolchain -- compiler,
# debugger, and their dependencies -- into production, which is both larger and
# more attack surface than a running service needs.
#
# jammy rather than alpine: eclipse-temurin publishes no arm64 alpine variant
# for 17, so an alpine base fails to build on Apple Silicon. A base image that
# only works on the maintainer's architecture is a bad default for a repository
# other people are meant to clone and run.
FROM eclipse-temurin:17-jre-jammy AS runtime

# curl is needed by the container healthcheck and is not present in the base
# image -- the reason the previous healthcheck could never have passed.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user. As root, a process escaping the application also
# owns the container filesystem, and on some configurations that is a step
# toward the host.
RUN groupadd --system propflow && useradd --system --gid propflow propflow

WORKDIR /app
COPY --from=build --chown=propflow:propflow /build/target/*.jar app.jar

USER propflow

EXPOSE 8080

# The port is not hardcoded. application.properties reads server.port from
# ${PORT:8080}, so the environment controls it; a -Dserver.port flag would
# override the environment and make the container's port unconfigurable.
#
# -XX:MaxRAMPercentage lets the JVM size its heap from the container's memory
# limit rather than from the host's total RAM. Without it, a JVM in a memory-
# capped container can size a heap larger than the limit and be OOM-killed by
# the kernel with no Java-level error -- a container that dies silently under
# load with nothing useful in its logs.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
