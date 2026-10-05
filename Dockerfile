FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
RUN useradd -r -u 10001 app
COPY --from=build /app/target/*.jar app.jar
USER 10001
EXPOSE 8080
CMD ["java", "-XX:MaxRAMPercentage=55", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
