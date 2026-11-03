# Deploying the API to Render

The API runs as a Docker web service and connects to a separate Render Postgres database. The Docker image contains the Java runtime and packaged Spring Boot application; database credentials and the JWT signing secret are provided at runtime.

## Configure the web service

1. Create a Render Postgres database and choose a region.
2. Create a Render **Web Service** from this repository, select **Docker**, and use the same region as the database.
3. In the web service's environment settings, add:

   | Variable | Value |
   | --- | --- |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<internal-host>:5432/<database-name>` from the database's internal connection details |
   | `SPRING_DATASOURCE_USERNAME` | The database's generated application username |
   | `SPRING_DATASOURCE_PASSWORD` | The database's generated password; mark it secret |
   | `JWT_SECRET` | A newly generated random value of at least 32 characters; mark it secret |
   | `APP_COOKIE_SECURE` | `true` |
   | `APP_COOKIE_PATH` | `/api/auth` |
   | `SPRING_JPA_SHOW_SQL` | `false` |
   | `SPRING_JPA_FORMAT_SQL` | `false` |
   | `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` for the first deployment |

   Generate a JWT key locally with `openssl rand -base64 48`, then paste it directly into Render's secret environment variable field. Do not put the value in this repository or in a Docker build argument. Render supplies `PORT`; Spring Boot listens on it automatically.

4. Deploy. Render terminates HTTPS in front of the container. Set the mobile app's `EXPO_PUBLIC_API_URL` to the deployed `https://<service>.onrender.com` URL.

Use Render's internal Postgres host because the API and database are in the same region. Build the JDBC URL from that host, port, and database name; do not paste Render's `postgres://` connection string directly into `SPRING_DATASOURCE_URL`.

## Local development

Copy `.env.example` to `.env`, enter your local database password, and generate a local `JWT_SECRET`. Spring Boot does not automatically load `.env`; export the values before launching the app or add them to your IDE run configuration. `.env` is ignored by Git.

To build and run the container locally, use a database reachable from inside Docker and pass the same environment variables at runtime:

```bash
docker build -t microloan-api .
docker run --rm -p 8080:8080 --env-file .env microloan-api
```

## Database schema note

The project currently has no versioned database migrations. `update` lets Hibernate create or update the schema on initial deployment. Keep a backup of important production data, and add migrations before making future schema changes. Once an initial schema is established and migrations are in place, switch `SPRING_JPA_HIBERNATE_DDL_AUTO` to `validate`.

## Secret rotation

The old local database password and sample JWT signing key have been removed from `application.properties`. If either value was ever committed or shared, changing the current file is not enough: rotate the database password and deploy a new JWT key. Replacing the JWT key invalidates all current access tokens.
