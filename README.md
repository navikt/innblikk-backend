# Innblikk

Innblikk er et analyseverktøy for å måle brukeradferd, bygget av Team ResearchOps.

Spørsmål? Slack: [#researchops](https://nav-it.slack.com/archives/C02UGFS2J4B) eller opprett et issue her på GitHub.

---

### Prerequisites

- Java 25
- Colima
- Docker CLI with the Compose plugin (`docker compose`)

### Build the project

```bash
./gradlew build
./gradlew test --rerun-tasks
```

### Run locally

**1. Start Colima**

```bash
colima start --cpu 4 --memory 16 --disk 100
```

If `docker` is not installed, install the Docker CLI first; Colima provides the runtime, not the command.

**2. Start the database**

```bash
docker compose -f .compose/docker-compose.yaml up
```

> If Flyway migrations fail (e.g. after a schema change on the feature branch), wipe the volume and restart:
>
> ```bash
> docker compose -f .compose/docker-compose.yaml down -v && docker compose -f .compose/docker-compose.yaml up
> ```

> On macOS, Colima provides the container engine for these `docker compose` commands.

**3. Start the application**

```bash
./gradlew bootRun
```

Flyway runs automatically on startup and applies any pending migrations.

**4. Verify**

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

### Native image parity check (do this before merging)

Dev and prod run a **GraalVM native image** (see `Dockerfile`), not a JVM. `bootRun` runs on the
JVM with full reflection available and will not catch native-image bugs — most commonly missing
reflection hints for `@RequestBody`/`@ResponseBody` DTOs, especially polymorphic ones
(`@JsonSubTypes`/sealed interfaces) or anything else reflection-based (serialization, Flyway
exception handling, etc.). These bugs only surface once compiled ahead-of-time, which only
happens in CI/dev/prod — so `bootRun` passing locally proves nothing about the native image.

**On macOS with colima**, native-image compilation is memory-hungry. GraalVM auto-sizes its heap to
~80% of memory visible to the VM, and will hard OOM-kill (`exit 137`, "The Native Image build
process ran out of memory") well under 8GB for this app. Give colima real headroom:

```bash
colima start --cpu 4 --memory 16 --disk 100
```

If Colima is already wedged (`docker ps` hangs or prints nothing, builds silently stall):

```bash
colima stop
colima delete -f
colima start --cpu 4 --memory 16 --disk 100
```

`docker compose build`/`up --build` are also known to sometimes swallow all progress output even
with `BUILDKIT_PROGRESS=plain` (silently building in the background regardless — see
[docker/compose reports](https://forums.docker.com/t/no-running-output-from-docker-compose-build/149458)).
If a compose build looks stalled, verify directly with `docker build -t innblikk-backend-native .`
from the repo root instead, which reliably streams output and isolates Compose from the picture.

Run the real thing locally instead:

```bash
docker compose -f .compose/docker-compose.yaml down -v
docker compose -f .compose/docker-compose.yaml --profile native up --build
```

`--profile native` opts the `app` service into the run (kept out of the default `docker compose up`
so the fast DB+oauth-only inner loop isn't slowed down by a native compile every time). `depends_on`
handles startup ordering, so this is a single command — it builds the exact same `Dockerfile` CI
uses and wires it to Postgres + mock OAuth server.

**Testing against a local frontend?** Its dev proxy (`innblikk-frontend`'s `server.js`) requests
tokens from `mock-oauth2-server` via the host (`http://localhost:8080`), while this `app` container
validates them from inside the compose network. `mock-oauth2-server` derives the `iss` claim from
whatever `Host` header the token requester used — so a token minted via `localhost:8080` has
`iss: http://localhost:8080/issueissue`, which won't match a `host.docker.internal`-based
issuer-uri, and `host.docker.internal` isn't guaranteed to resolve from the bare host on every
colima setup anyway. `application.yaml`'s default config already uses `jwk-set-uri` instead of
`issuer-uri` for exactly this reason (only needs network connectivity for signature verification,
doesn't enforce an issuer-claim match — no custom `JwtDecoder`/validator overrides that), so a
plain host-run backend and this compose setup both sidestep the problem already. The only thing
this compose file's `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` override does is
repoint that at the internal hostname (`mock-oauth2-server:8080`), since `localhost:8080` isn't
reachable from inside the container. Prod still validates the real Azure AD issuer via
`application-kubernetes.yaml`; this only affects local dev/compose.


If output looks stalled, BuildKit hides the `RUN ./gradlew nativeCompile` step's logs by default —
use `BUILDKIT_PROGRESS=plain` to see it, and expect it to take several minutes:

```bash
BUILDKIT_PROGRESS=plain docker compose -f .compose/docker-compose.yaml --profile native up --build
```

If it fails with something like `Cannot construct instance of ...: cannot deserialize from Object
value (no delegate- or property-based Creator)`, that's a missing reflection hint. Register the
class(es) in `src/main/kotlin/no/nav/reops/config/NativeRuntimeHints.kt`.

#### Is GraalVM native image still worth it?

Worth revisiting if the pain above (multi-minute builds, memory-hungry compiles, reflection-hint
bugs that only exist because of native image) starts costing more than it's giving back. This app
is a normal Nais-deployed service, not scale-to-zero/serverless — the actual payoff of native image
(near-instant cold start, lower runtime memory) may not be worth much here in practice. If nobody
can point to a concrete requirement driving it (a cold-start SLA, a measured memory/cost problem at
our replica count), consider dropping it for a plain JVM image instead: simpler Dockerfile, no
reflection landmines, faster local iteration, normal Java tooling/debugging. Revisit with real
numbers if/when it becomes a real decision rather than a default.
