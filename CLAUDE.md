# CLAUDE.md

## Working agreement
- The project owner makes the design decisions. Propose, explain trade-offs, then wait for a go-ahead.
- Work one small step at a time. One concern per change/PR. Don't bundle refactors with features.
- Don't refactor, rename or "clean up" code outside the step we agreed on. Note it under "Known issues" instead.
- Match existing style unless the step is specifically to change that style.
- Check every change against the "Target design" section below.
- Update this file when a step changes the architecture, commands, design or known issues.

## Commits
- Never commit (or push) without the owner's explicit approval for that specific commit. Finish the change, show it, then wait.
- Commit messages are a single line: brief and specific (e.g. `Reject rootUser flag on public registration`). No body, no long details.
- Never add `Co-Authored-By`, "Generated with Claude", or any other AI attribution, in commits or in PR descriptions. The project owner is the sole author.

## What this is
A multi-tenant authentication and authorization provider, similar to Auth0. Tenants manage their users,
roles and token settings. Regular users log in to get JWTs, and other applications verify those JWTs.
Current implementation: README.md. Feature roadmap: IAM_ROADMAP.md.

## Target design (agreed with owner, 2026-10-24)
Nothing below exists in code yet unless marked. This section is where the code should end up.

### Three levels
```
Platform (global)       super users: manage tenants (e.g. suspend a whole tenant)
 └─ Tenant              root user + management users: manage their own tenant only
     └─ Regular users   log in, get tokens, self-service only
```
- **Platform**: a group of super users, controlled by roles and permissions. Global, not per tenant.
  Example power: block a tenant so none of its users (management or regular) can log in. More scopes later.
  Proposed model: a reserved built-in `platform` tenant. Super users are normal users in it.
  The first super user is bootstrapped from config at first startup, with no public signup into it.
  This replaces the unused `is_super_user` flag.
- **Tenant**: created by signup. The user who creates a tenant becomes its **only** root user.
  The root has every tenant-management permission inside that tenant and no access to any other tenant.
  Proposed: an explicit tenant-signup operation that creates the tenant and root user in one transaction,
  instead of a `rootUser` flag on user registration. Enforce one root per tenant in the database.
- **User types inside a tenant**:
  - *Management users*: created by root (or by a management user with permission). They use the management API
    and the future management UI: roles, regular users, token settings, UI settings.
  - *Regular users*: login/logout, get access (+ refresh) tokens, change password, set up MFA (later). No management access.
- Every tenant-owned row has a `tenant_id`. Usernames are unique per tenant, not globally.
  Tenant isolation replaces the current `createdBy`-chain check (`createdBy` stays for audit only).

### Tenant identification
- Regular users identify their tenant through a **`client_id`**. Tenants register client applications,
  and the login and token requests carry the `client_id`.
- **Every login goes through `client_id`**, one login flow for all users. On tenant creation, a built-in
  **management-console client** is auto-created for that tenant. Management users log in through it.
  It only accepts management users and has its own token settings like any other client.
  The platform tenant gets its own console client for super users in the same way.

### Permissions and roles
- **Permissions are fixed and feature-based.** Tenants and platform users can never create permissions.
  New permissions are added to the database together with the feature or endpoint that needs them (by migration).
- Two catalogs: **platform permissions** (e.g. `tenants:suspend`) and **tenant-management permissions**
  (e.g. `users:create`, `roles:write`, `token-settings:update`). Platform permissions are never grantable inside a tenant.
- **Roles are dynamic**: built from the fixed permission list. On tenant creation, a set of **predefined roles** is
  created for that tenant. Root, or a management user with role-modification permission, can modify those roles and create new ones.
  The platform tenant works the same way.
- Management users are given a set of roles and/or a set of individual permissions.
- **No self-modification, at every level (platform and tenant):** a user can never
  - change their own role assignments or directly assigned permissions, or
  - edit the definition of a role they hold.
  Another user with the right permission can do it for them. Example: A (with permission) can change B's roles, but never A's own.
- A user with the permission to grant permissions or roles can grant ones they don't hold themselves (but never to themselves).
- Role and permission changes apply immediately (no approval step). Future option: maker-checker approval,
  switched on per tenant by the root user.
- The root user has every tenant-management permission implicitly (not through a role), so root's access can't be edited by anyone.

### Tokens
- Token settings are **per client application** inside a tenant. Management users configure, for each application:
  - access-token expiry,
  - whether refresh tokens are issued (e.g. app X gets refresh tokens, app Y gets access tokens only) and their expiry,
  - which user claims go into the JWT body (user id, username, email, …).
- First rollout: tokens carry only basic user information. The IAM doesn't manage other applications' roles or permissions.
- Other applications verify tokens through IAM endpoints. Planned: asymmetric signing + JWKS endpoint for local
  verification, plus an introspection endpoint that reflects revocation and tenant suspension.
- Logout and revocation: store refresh tokens (hashed) in the database. This replaces the `@Version`-based revocation.

### Tenant lifecycle
- `tenants.status` = `ACTIVE` / `SUSPENDED` (later maybe `DELETED`). A suspended tenant fails login, refresh and introspection.
  Locally verified access tokens stay valid until they expire, so access tokens must be short-lived.
- Super users act on tenants only through platform endpoints. Acting *inside* a tenant (support access) is a future,
  explicitly audited break-glass feature (roadmap #9).

### Future (not now)
- OAuth2/OIDC compatibility. The `client_id` choice keeps this path open.
- First priority is organizing the code structure toward this design.

### Open questions
- None currently.

## Stack
Java 25 · Spring Boot 4.1 (Jackson 3 = `tools.jackson.*`) · Spring Security (stateless JWT, JJWT 0.12)
· Spring Data JPA · MySQL 8 + Flyway (Testcontainers MySQL in tests) · MapStruct + Lombok · springdoc OpenAPI · Gradle
· ELK for logs (Logstash tails `logs/application.log`).

## Commands
- Run tests: `./gradlew clean test` (**Docker must be running**: Testcontainers starts MySQL 8.0; jacoco report in `build/reports/jacoco`)
- Run app: `./gradlew bootRun` (loads `.env`; expects MySQL on `localhost:3366`)
- Run everything in Docker: `docker compose up --build` (app on :8080, MySQL on :3366, plus ELK)
  - App + DB only: `docker compose up --build app db`
  - The app container reads `SPRING_MAIL_USERNAME/PASSWORD` from `.env` (optional). DB settings are set in `docker-compose.yml`.
- Swagger UI: http://localhost:8080/swagger-ui.html

## Database schema rules
- Flyway owns the schema: `src/main/resources/db/migration/V<n>__<description>.sql`.
- Never edit an applied migration. Every schema change is a new `V<n+1>__...` file.
- Hibernate runs with `ddl-auto=validate` (app and tests), so an entity/schema mismatch fails at startup.
- No seed data yet. The first root user is created through public registration.
- Booleans are `BOOLEAN`, timestamps `DATETIME(6)`, and constraints get readable names (`fk_<table>_<ref>`, `uk_<table>_<col>`).

## Current layout (`src/main/java/com/example/iamsystem`)
- `security/`: JWT filter, `JwtTokenUtil`, `AuthController` (`/api/auth/*`), `DefaultUserDetails(User)`, login success/failure listeners (lockout)
- `user/`: `UserService` (registration, password change, role assignment, creator-tree visibility), `password/` (OTP reset by email)
- `role/`, `permission/`: CRUD. Permission = `serviceName` + `PermissionAction` enum (READ/WRITE/UPDATE/DELETE)
- `aspect/AuthorizationAspect` + `util/authorization/@RequirePermission`: endpoint permission checks (string `"IAM:READ"` etc.)
- `audit/`: `@Auditable` (SpEL) → `AuditAspect` → MDC JSON log (`log_type=audit`)
- `organization/`, `application/`, `service/`: entities from PR #10. **Not used by any code yet.** `organizations` is the closest thing to a tenant.
- `src/main/resources/db/migration/`: Flyway migrations (`V1__baseline.sql` = schema as of the Flyway introduction)

## Current behaviours to remember
- Root user (`is_root_user`) passes every permission check in `PermissionService.hasPermission`, globally (no tenant scope).
- Public registration with `rootUser: true` creates a root user but **no tenant**.
- User visibility and edit rights = walking the `createdBy` chain (`UserService.isUserInTree`).
- Token revocation = JWT `version` claim vs `User.@Version` (the same column as optimistic locking).
- Access token 5 min, refresh token 24 h; constants and secrets live in `constant/JwtConstant.java`.
- `is_super_user` exists in schema and entity but is never read.

## Known issues (found in review 2026-10-24, not fixed yet)
Security
- No tenant isolation: roles and permissions are global, and any root user (anyone can self-register as root)
  can modify every role and permission in the system.
- JWT secrets are hard-coded in `JwtConstant` (HMAC, so any verifier could also forge tokens). `mail.debug=true`.
- Password reset emails a plaintext new password. OTP uses `java.util.Random`, has no attempt limit, and the endpoint reveals whether an email exists.
- `/api/auth/token/validate` ignores the token version (revoked tokens are reported as valid).

Broken / drift
- Deleting a user who created other users fails (FK `fk_users_created_by`, no ON DELETE rule). It needs a product decision.
- JWT errors thrown inside the filter become 500 instead of 401.
- `RoleMapper.toEntity` drops `permissionIds`.

Design debt
- Authorization logic is in 4 places (security config, aspect, strings in `UserService`, unused JWT `authorities` claim).
- `getCurrentUser()` is duplicated in 4 classes. The JPA entity is used as the security principal and is reloaded on every request.
- `findAllUsers` loads the whole table and filters in memory.
- Mappers are declared `componentModel="spring"` but used via `Mappers.getMapper`.
- Permission model (free-text `serviceName` + CRUD enum, created through the API) contradicts the target design (fixed, migration-defined permissions).

## Progress log
- 2026-10-24: Codebase reviewed. CLAUDE.md created. Target design agreed (platform/tenant/regular levels, client_id, fixed permissions + dynamic roles, no self-modification, per-application token settings, management-console client per tenant).
- 2026-10-24: Step 1 (baseline). Dockerfile on JDK 25, `app` service in docker-compose, fixed `ddl-auto` property name.
- 2026-10-24: Step 2 (single schema). Flyway `V1__baseline.sql` (from the entities), `ddl-auto=validate`, removed `db-init/`,
  tests on Testcontainers MySQL instead of H2, `.env` no longer passed to the test JVM.
  Next: to be chosen by the owner.
