# IAM System

A multi-tenant authentication and authorization provider, in the spirit of Auth0. Organizations sign up as tenants,
register the applications their users log in to, and manage those users, their roles and the tokens they get. Other
applications verify the tokens locally (JWKS) or ask the IAM (introspection).

## What it does

- **Tenants.** Signing up creates a tenant, its single root user and its management console. A platform level of
  super users manages the tenants: lists them, creates them, suspends and reactivates them, and works inside one when needed.
- **Clients.** Every login goes through a client application (`client_id`), which decides the tenant. Each client has
  its own token settings (lifetimes, refresh tokens on or off, which user claims the token carries) and can be suspended.
- **Users.** Management users run the tenant through the management API; regular users log in, refresh their tokens,
  change their password and reset a forgotten one with an emailed one-time code.
- **Permissions and roles.** A fixed catalog of `<resource>:<action>` permissions, combined into roles each tenant
  defines (three predefined ones to start with). Nobody can change their own access.
- **Tokens.** RS256-signed access tokens published through `/.well-known/jwks.json`, sessions with rotating single-use
  refresh tokens, logout, and introspection that reflects revocation and suspension.
- **Protection.** Account lockout after repeated wrong passwords, hashed and rate-limited reset codes, no secrets in
  logs, a CORS allow-list for browser apps, and an audit log of logins and management actions.

## Tech stack

Java 25 · Spring Boot 4.1 · Spring Security · Spring Data JPA · MySQL 8 with Flyway · MapStruct · Lombok · springdoc
OpenAPI · Gradle · Testcontainers · ELK for logs.

## Quick start

You need JDK 25 and Docker.

```bash
cp example.env .env        # then fill it in: every setting is required (manual, section 2)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-key.pem   # the token signing key
docker compose up db       # MySQL on localhost:3366
./gradlew bootRun          # the app, profile local
```

Or run everything in containers with `docker compose up --build app db`. Then open Swagger UI at
`http://localhost:8080/swagger-ui.html` (or the port in `SERVER_PORT`) and follow the
[walkthrough](docs/USER_MANUAL.md#5-walkthrough-from-an-empty-system-to-working-users): sign up a tenant, log in, create
users and roles.

Run the tests with `./gradlew clean test` (Docker must be running; Testcontainers starts MySQL).

## Documentation

| Document | What's in it |
|---|---|
| [docs/USER_MANUAL.md](docs/USER_MANUAL.md) | How to use the system: concepts, configuration and profiles, every endpoint with its permission, the walkthrough, rules and status codes |
| [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) | What has been built step by step and what comes next |
| [IAM_ROADMAP.md](IAM_ROADMAP.md) | Features planned after the current plan |
| [CLAUDE.md](CLAUDE.md) | Working agreement, target design, code standards, code layout and progress log |
