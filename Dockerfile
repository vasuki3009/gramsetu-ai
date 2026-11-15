# ─────────────────────────────────────────────────────────────
# Stage 1: Build — Maven + JDK 17
# ─────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# ── Dependency layer ─────────────────────────────────────────
# Cached unless pom.xml changes. Re-downloading ~200 deps takes
# 3-5 min, so we isolate it from the source layer deliberately.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# ── Source layer ─────────────────────────────────────────────
# CACHEBUST busts this layer (and the mvn build below) without
# invalidating the dependency layer above.
#
# Force fresh compile every time:
#   docker-compose build --build-arg CACHEBUST=$(date +%s)
#
# Full nuclear rebuild (re-downloads deps too):
#   docker-compose build --no-cache
ARG CACHEBUST=1
COPY src ./src
RUN mvn clean package -DskipTests -B

# ─────────────────────────────────────────────────────────────
# Stage 2: Runtime — slim JRE only (~300 MB final image)
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Run as an unprivileged user
RUN groupadd --system gramsetu && useradd --system --gid gramsetu --no-create-home gramsetu

COPY --from=build /app/target/gramsetu-backend-1.0.0.jar app.jar
USER gramsetu

# Read files (such as the Tamil seed data) as UTF-8 even when the image has no locale set.
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"

# The app listens on $PORT (server.port=${PORT:8081}); platforms such as Railway inject PORT.
# EXPOSE is documentation only: it records the default.
EXPOSE 8081

# MaxRAMPercentage lets the JVM size its heap from the container's memory limit.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
