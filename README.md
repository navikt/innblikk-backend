# Getting Started

### Prerequisites
- Java version 21

### Build the project
```
./gradlew build
./gradlew test --rerun-tasks
```

### Run locally
1. Start docker-compose in project reops-event-proxy under .compose `docker-compose up`
2. Start application with profile `local`:  
   `./gradlew bootRun -Dspring-boot.run.profiles=local`
3. Gå til swagger-doc `http://localhost:8086/api/swagger-ui/index.html#/`
4. Optional, Bruno collection https://github.com/navikt/reops-meta/tree/main/tooling/researchops-bruno/start-umami-backend

### Request token from the mock server
```
curl -s -X POST "http://localhost:8080/issueissue/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=start-umami&client_secret=unused&audience=start-umami" \
  | jq -r .access_token
```
#### Bruk jwt token i header
```
Authorization: Bearer token
```

### Testing CHANGELOG using conventional commits (eventually automate this in CI/CD pipeline)
Install first git-cliff
```
brew install git-cliff 
```
Then run the following command in the project root:
```
./gradlew generateChangelog
```
Generates a CHANGELOG.md file based on the commit history and the conventional commit messages.
Configure "cliff.toml"