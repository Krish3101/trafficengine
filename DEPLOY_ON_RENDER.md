Deploying to Render.com

Recommended approach: use the existing `Dockerfile` (recommended) or use Render's Docker/Buildpacks.

Steps (Docker):

1. Push repository to GitHub.
2. On Render, create a new service → Web Service → Connect repository.
3. Select "Docker" as the environment (Render will use the repo's `Dockerfile`).
4. Set the following Environment Variables in Render's dashboard (Environment → Environment Variables):
   - `PORT` = (Render sets this automatically at runtime; no need to set manually)
   - `SPRING_DATASOURCE_URL` = jdbc:mysql://<HOST>:<PORT>/<DB_NAME>?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
   - `DB_USER` = <mysql-username>
   - `DB_PASSWORD` = <mysql-password>

Notes:
- The app reads `server.port` from `${PORT:8080}` (see `src/main/resources/application.properties`), which works with Render.
- The `Dockerfile` builds a runnable fat JAR and exposes port 8080; Render will map the runtime `PORT` automatically inside the container.
- If you prefer using Render's managed databases, create a MySQL instance and copy the connection string into `SPRING_DATASOURCE_URL`.

Troubleshooting:
- If you see DB connection errors, verify the `SPRING_DATASOURCE_URL`, `DB_USER`, and `DB_PASSWORD` values and security group access.
- To build without Docker, use Render's Java buildpacks and set the Start Command to: `java -jar target/traffic-0.0.1-SNAPSHOT.jar` after building.

Local quick test:

Build locally:

```bash
./mvnw clean package -DskipTests
java -jar target/traffic-0.0.1-SNAPSHOT.jar
```

Then open http://localhost:8080
