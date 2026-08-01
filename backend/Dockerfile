# Multi-stage build: compile with Maven+JDK21, run on a slim JRE 21 image.
# Written for cloud hosts (Railway/Render/Fly.io) that build directly from a
# Dockerfile in the repo — none of these had a reliable way to auto-detect a
# plain Maven project's Java 21 requirement, so this is explicit rather than
# relying on buildpack auto-detection.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Dependencies first, so `mvn dependency:go-offline` is cached across builds
# and only re-runs when pom.xml actually changes, not on every source edit.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Matches pom.xml's <artifactId>/<version> (gotogether-backend-0.1.0-SNAPSHOT.jar)
# — a wildcard copy so this doesn't need editing every time the version bumps.
COPY --from=build /app/target/gotogether-backend-*.jar app.jar

# Cloud hosts (Railway, Render, etc.) inject their own $PORT env var and
# expect the app to listen on it — server.port defaults to 8081 in
# application.yml for local dev, but must respect $PORT when it's set, so
# this is passed through explicitly (--server.port=${PORT}, highest Spring
# property precedence) rather than assuming any hardcoded port works.
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT}"]
