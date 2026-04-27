# syntax=docker/dockerfile:1.7

# Builder image — pinned by digest. Updated by CI's image-digest-bump job.
FROM eclipse-temurin:17-jdk-jammy@sha256:978ed38b7785312f7761bee5e24cfcd7ac2fe466c5f07acee7b322e0bae6d0ec AS build
WORKDIR /src
RUN apt-get update \
 && apt-get install -y --no-install-recommends maven \
 && rm -rf /var/lib/apt/lists/*
COPY backend/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY backend/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package \
 && cp target/*.jar /tmp/app.jar

# Runtime image — JRE only, runs as a non-root user. Pinned by digest.
FROM eclipse-temurin:17-jre-jammy@sha256:642d45bf22d3cb9face159181732ed9fa70873b2681e50445eff7d4785c176bb
WORKDIR /app

# Create an unprivileged user without a login shell. DORA Art. 9 / least
# privilege: the runtime should never run as root.
RUN groupadd --system --gid 1001 app \
 && useradd --system --uid 1001 --gid app --no-create-home --shell /usr/sbin/nologin app

COPY --from=build --chown=app:app /tmp/app.jar /app/app.jar

USER app:app
EXPOSE 8080

# Spring Boot already exposes /actuator/health; rely on the orchestrator's
# probe rather than burning a curl/wget into the image.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
