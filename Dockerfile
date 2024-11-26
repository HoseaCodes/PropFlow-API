# Use the official Maven image as a build stage
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Use OpenJDK for the runtime
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Add PostgreSQL JDBC driver
RUN apt-get update && apt-get install -y wget \
    && wget https://jdbc.postgresql.org/download/postgresql-42.6.0.jar \
    && mv postgresql-42.6.0.jar postgresql.jar

ENV PORT=8080
EXPOSE ${PORT}

CMD ["sh", "-c", "java -jar \
    -Dserver.port=${PORT} \
    -Dspring.profiles.active=prod \
    -DPOSTGRES_URL=${POSTGRES_URL} \
    -DPOSTGRES_USER=${POSTGRES_USER} \
    -DPOSTGRES_PASSWORD=${POSTGRES_PASSWORD} \
    app.jar"]