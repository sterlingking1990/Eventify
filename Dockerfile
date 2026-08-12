FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Dependencies resolve in their own layer so a source-only change doesn't
# re-download the world on every deploy.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Documentation only — the platform routes to whatever $PORT it injects, and
# application.properties reads server.port=${PORT:8080}.
EXPOSE 8080

# The JVM's default heap sizing reads the HOST's memory, not the container's
# limit, so on a small instance it happily reserves more than it is allowed and
# gets OOM-killed. MaxRAMPercentage makes it size against the cgroup limit.
#
# Container-aware DNS TTL: Java caches DNS forever by default, which breaks when
# a managed database's IP changes underneath a long-running process.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -Dnetworkaddress.cache.ttl=30"

ENTRYPOINT ["java", "-jar", "app.jar"]
