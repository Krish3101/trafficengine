# Render has no Java runtime of its own, so it builds this image from the repo.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Free Render instances have 512 MB; keep the heap well inside that.
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
