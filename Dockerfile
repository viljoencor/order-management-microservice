# Steps
# Build, test and package the application with Maven on Java 21.
# Copy only the packaged JAR into a small Java 21 runtime image.
# Run as a non-root user and take credentials from environment variables at run time.
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first, so they stay cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B clean verify

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system orderflow && useradd --system --gid orderflow --no-create-home orderflow

# The wildcard skips orderflow-api-*.jar.original, which Spring Boot's repackaging leaves behind.
COPY --from=build /build/target/orderflow-api-*.jar app.jar

USER orderflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
