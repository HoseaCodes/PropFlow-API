# Use the official Maven image as a build stage
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Use OpenJDK for the runtime
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# The port is not hardcoded here. application.properties reads server.port from
# ${PORT:8080}, so the environment controls it -- a -Dserver.port flag would
# override the environment and make the container's port unconfigurable.
ENTRYPOINT ["java", "-jar", "app.jar"]