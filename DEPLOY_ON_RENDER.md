## Render Deployment

This project is configured for a fast Render deploy with Docker and an embedded H2 database.

### Render setup

1. Push the repository to GitHub.
2. In Render, create a new Web Service and connect the repository.
3. Choose Docker as the runtime.
4. Leave environment variables empty unless you want to override the defaults.
5. Deploy.

### What the app uses

- Spring Boot web app with Thymeleaf UI.
- Embedded H2 database, so no external database service is required.
- `PORT` is read from the Render runtime automatically.

### Local run

```bash
./mvnw clean package -DskipTests
java -jar target/traffic-0.0.1-SNAPSHOT.jar
```

Open http://localhost:8080
