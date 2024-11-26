# Use the official Maven image as a build stage
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Use OpenJDK for the runtime
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
# ENTRYPOINT ["java","-jar", "-Dserver.port=8081", "app.jar"]
CMD ["sh", "-c", "java -jar \
    -Dspring.profiles.active=prod \
    -Dserver.port=${PORT} \
    -Dspring.datasource.url=${JDBC_DATABASE_URL} \
    -Dspring.datasource.username=${JDBC_DATABASE_USERNAME} \
    -Dspring.datasource.password=${JDBC_DATABASE_PASSWORD} \
    app.jar"]