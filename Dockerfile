# Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first to leverage Docker cache
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render uses the PORT environment variable
EXPOSE 8080

# Convert Render's postgres:// URL to Spring's jdbc:postgresql:// before starting
ENTRYPOINT ["sh", "-c", "if [ -n \"$DATABASE_URL\" ]; then export SPRING_DATASOURCE_URL=${DATABASE_URL/postgres:/jdbc:postgresql:}; fi; java -jar app.jar"]
