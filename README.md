# Getting Started

### Prerequisites

- Java 25

### Build the project

```bash
./gradlew build
./gradlew test --rerun-tasks
```

### Run locally

**1. Start the database**

```bash
docker compose up
```

> If Flyway migrations fail (e.g. after a schema change on the feature branch), wipe the volume and restart:
>
> ```bash
> docker compose down -v && docker compose up
> ```

**2. Start the application**

```bash
./gradlew bootRun -Dspring-boot.run.profiles=local
```

Flyway runs automatically on startup and applies any pending migrations.

**3. Verify**

- Swagger UI: http://localhost:8086/api/swagger-ui/index.html
- Optional: [Bruno collection](https://github.com/navikt/reops-meta/tree/main/tooling/researchops-bruno/start-umami-backend)

### Request a token from the mock OAuth server

```bash
curl -s -X POST "http://localhost:8080/issueissue/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'grant_type=client_credentials&client_id=start-umami&client_secret=unused&audience=start-umami' \
  -d 'claims={"preferred_username":"test@nav.no","name":"Test User","NAVident":"T123456"}' \
  | jq -r .access_token
```

Use the token in subsequent requests:

```
Authorization: Bearer <token>
```

### Testing CHANGELOG using conventional commits

Install git-cliff:

```bash
brew install git-cliff
```

Then generate the changelog:

```bash
./gradlew generateChangelog
```

Produces `CHANGELOG.md` from conventional commit history. Configure via `cliff.toml`.
