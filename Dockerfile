# syntax=docker/dockerfile:1

# Build stage: packages the application with the Maven wrapper. Tests are not run here: they use
# Testcontainers, which needs a Docker daemon that is not available during an image build.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B clean package -DskipTests \
    && cp target/challenge-*.jar app.jar

# Runtime stage: JRE only, running as a non-root user.
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
