FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests
FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /app/target/vortex-service-1.0.0-SNAPSHOT.jar app.jar
RUN mkdir -p /tmp/vortex-assets
EXPOSE 8080 9090
ENTRYPOINT ["java", \
    "-jar", \
    "app.jar", \
    "--spring.profiles.active=prod"]