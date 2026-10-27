# CLAUDE.md

> **Resuming work?** Read this file, then `docs/IMPLEMENTATION_PLAN.md` (step-by-step plan and how to resume).
> This file = rules, agreed decisions, current state. The plan file = proposed technical design per remaining step.
> `docs/USER_MANUAL.md` = how to use the system today (Swagger/Postman flows).

## Working agreement
- The project owner makes the design decisions. Propose, explain trade-offs, then wait for a go-ahead.
- Work one small step at a time. One concern per change/PR. Don't bundle refactors with features.
- **Every class or file a step touches must end that step as clean as we can make it** (see "Code standards").
  Each step plan lists the touched classes and the cleanup planned for them. Code the step doesn't touch
  stays as it is; note problems there under "Known issues".
- Match existing style unless the step is specifically to change that style.
- Check every change against the "Target design" section below.
- Update this file when a step changes the architecture, commands, design or known issues.
  When a step finishes, also update `docs/IMPLEMENTATION_PLAN.md` (move it to "Done", adjust later steps if needed).
- **Keep `docs/USER_MANUAL.md` in sync:** every step that changes endpoints, request bodies, permissions or the setup flow
  updates the manual in the same commit, with the flows verified against the running app.
- **Keep the frontend plan in sync:** the same steps also update `../IAM System UX/README.md` (the frontend team's plan:
  steps, mockups, API coverage matrix, backend gaps, changelog), following its section 9 "How this document evolves".
- **No new plan steps:** `docs/IMPLEMENTATION_PLAN.md` keeps only its remaining steps. Anything new (a "later" option, a
  follow-up, a feature idea) becomes a numbered item in `IAM_ROADMAP.md`, ordered by priority and work volume, and
  documents refer to it as "roadmap item N".

## Code standards
What "clean" means for every class a step touches:
- **Single responsibility.** Small focused classes; no god services. Controllers stay thin (no logic).
- **One owner per concern.** No duplicated logic (e.g. current-user lookup, permission strings). Shared behaviour lives in one injectable bean.
- **Dependency injection only.** Constructor injection (`@RequiredArgsConstructor`); inject MapStruct mappers, never `Mappers.getMapper`; no static access to collaborators where a bean can be injected.
- **Explicit failures.** No `assert` or silent `null` returns for runtime checks; throw a meaningful exception.
- **No magic strings or numbers.** Constants live in the feature that owns them. Config values come from `@ConfigurationProperties`, not scattered `@Value`.
- **Logging with purpose.** No entry/exit debug noise. INFO for meaningful events, WARN for suspicious ones, ERROR only for real failures. Never log secrets.
- **Mask sensitive data.** Passwords, OTPs, tokens, keys and any other credential never appear in logs, audit details, exception
  messages or responses. Every class that holds one (request DTOs, configuration records, ...) overrides `toString()` and shows
  `SecretMask.MASK` instead of the value, and `SecretMaskTest` lists it, because frameworks log objects on their own (Spring MVC
  logs request bodies at DEBUG). Only the email that delivers an OTP contains it.
- **Entities.** No Lombok `@Data`/`@AllArgsConstructor` on JPA entities; `equals`/`hashCode` based on id; no business logic that belongs in services.
- **HTTP.** Correct status codes (201 on create, 204 for empty responses, 401/403/404 as appropriate).
- **Clean imports.** No unused imports; delete them. No wildcard (`*`) imports, static ones included; import each class or member explicitly.
  Keep IntelliJ's default order: other imports, blank line, `javax.*`/`java.*`, blank line, static imports (each group sorted).
- **Tests.** Behaviour of every touched class is covered; tests change together with the code.
  Every test method has a `@DisplayName` stating the behaviour in plain English (e.g. `@DisplayName("Users of another tenant are not found")`),
  placed right after `@Test`.

## Commits
- Work directly on `master`. Don't create a branch unless the owner explicitly asks for one.
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
  Model (agreed 2026-10-25): a reserved built-in platform tenant (`tenants.type = PLATFORM`, exactly one). Super users are
  normal users in it. Its root (the first super user) is created at startup from `IAM_PLATFORM_ROOT_*` config when the
  platform has no root; without the config the startup stops (only the test suite runs without a platform user), and
  there is no public signup into it.
  This replaced the unused `is_super_user` flag (dropped in step 10m). Done in step 10.
  - The platform root can do everything in every tenant (every TENANT key anywhere) plus every platform key
    (`tenants:read`, `tenants:create`, `tenants:suspend`, `tenants:activate`).
  - Other platform users get **grants**: a role or a single permission **on one tenant or on all tenants**
    (`platform_role_grants` / `platform_permission_grants`, `tenant_id` NULL = all tenants). Platform roles live in the
    platform tenant and may mix TENANT keys and `tenants:*` keys. Example: Alice holds `SUPPORT` on tenants 5 and 9.
  - A platform user works inside a tenant through the normal APIs by sending `X-Tenant-Id`; every permission check then
    asks "does this user hold this key on that tenant?". The header is ignored for tenant users. Every such action is
    audit-logged with the platform user and the target tenant.
  - No self-modification applies to platform users too.
- **Tenant**: created by signup. The user who creates a tenant becomes its **only** root user.
  The root has every tenant-management permission inside that tenant and no access to any other tenant.
  Done (step 5): public `POST /api/tenants/signup` creates the tenant and its root user in one transaction;
  a unique index enforces one root per tenant. Other users are created with `POST /api/users` inside the caller's tenant.
- **User types inside a tenant** (done in step 7c: `users.user_type`, set at creation and never changed):
  - *Management users*: created by root (or by a management user with permission). They use the management API
    and the future management UI: roles, regular users, token settings, UI settings.
  - *Regular users*: login/logout, get access (+ refresh) tokens, change password, set up MFA (later). No management access.
- Every tenant-owned row has a `tenant_id`. Usernames and emails are unique per tenant (done in step 8a).
  Exception: a **root user's username and email are unique across all users of all tenants**
  (no other user anywhere may share them; enforced in application code, MySQL can't express it as one constraint).
  Tenants have no slug until one is needed.
  Done (step 6): tenant isolation. Every user and role lookup is tenant-scoped, anything of another tenant is 404,
  and there is no hierarchy inside a tenant (`createdBy` is audit only).

### Tenant identification
- Regular users identify their tenant through a **`client_id`**. Tenants register client applications,
  and the login and token requests carry the `client_id`.
- **Every login goes through `client_id`**, one login flow for all users. On tenant creation, a built-in
  **management-console client** is auto-created for that tenant. Management users log in through it.
  It only accepts management users and has its own token settings like any other client.
  The platform tenant gets its own console client for super users in the same way.
- Done (step 8a): `clients` with a random 24-character URL-safe `client_id`; signup creates the console client; login,
  refresh and forgot-password carry `client_id`; application clients accept all users of the tenant; tokens use the
  user id as `sub` and carry the `client_id`. No client secrets yet (all clients are public, until OAuth2/OIDC work).

### Permissions and roles
- **Permissions are fixed and feature-based.** Tenants and platform users can never create permissions.
  New permissions are added to the database together with the feature or endpoint that needs them (by migration).
- Two catalogs: **platform permissions** (e.g. `tenants:suspend`) and **tenant-management permissions**
  (e.g. `users:create`, `roles:update`, `token-settings:update`). Platform permissions are never grantable inside a tenant.
  Done (step 7a): keys are generic `<resource>:<action>` (`users:read` covers list and every lookup; `users:update` covers
  assigning and removing roles), with `users:reset-password` kept separate because a reset can take over an account.
  The `scope` column (`TENANT`/`PLATFORM`) enforces the split: only TENANT keys go into a tenant's roles, and root holds
  every TENANT key implicitly but never a PLATFORM one.
- **Roles are dynamic**: built from the fixed permission list. On tenant creation, a set of **predefined roles** is
  created for that tenant. Root, or a management user with role-modification permission, can modify those roles and create new ones.
  Done (step 7b): `ADMIN` (every key), `USER_MANAGER` (`users:*` except reset-password), `VIEWER` (the read keys), copied
  from migration-defined templates at signup; later template changes never reach existing tenants.
  The platform tenant works the same way.
- Management users are given a set of roles and/or a set of individual permissions.
- **No self-modification, at every level (platform and tenant):** a user can never
  - change their own role assignments or directly assigned permissions, or
  - edit the definition of a role they hold.
  Another user with the right permission can do it for them. Example: A (with permission) can change B's roles, but never A's own.
- A user with the permission to grant permissions or roles can grant ones they don't hold themselves (but never to themselves).
- Role and permission changes apply immediately (no approval step). Future option: maker-checker approval,
  switched on per tenant by the root user (roadmap item 12).
- The root user has every tenant-management permission implicitly (not through a role), so root's access can't be edited by anyone.

### Tokens
- Token settings are **per client application** inside a tenant (done in step 8b). Management users configure, for each application:
  - access-token expiry,
  - whether refresh tokens are issued (e.g. app X gets refresh tokens, app Y gets access tokens only) and their expiry,
  - which user claims go into the JWT body (user id, username, email, …).
- First rollout: tokens carry only basic user information. The IAM doesn't manage other applications' roles or permissions.
- Other applications verify tokens through IAM endpoints: asymmetric signing + JWKS endpoint for local verification
  (done in step 9a: RS256, one platform key set; per-tenant keys are roadmap item 13), plus an introspection endpoint that
  reflects revocation and tenant suspension (done in step 9c: `POST /api/auth/introspect`, public until clients get secrets).
- Logout and revocation: store refresh tokens (hashed) in the database. This replaces the `@Version`-based revocation.
  Done (step 9b): a session per login (`sid` in access tokens), single-use rotating opaque refresh tokens with reuse
  detection, `POST /api/auth/logout`.

### Tenant lifecycle
- `tenants.status` = `ACTIVE` / `SUSPENDED` (later maybe `DELETED`). A suspended tenant fails login, refresh and introspection.
  Locally verified access tokens stay valid until they expire, so access tokens must be short-lived.
- Super users suspend, activate, list and create tenants through `/api/platform/tenants`, and act inside a tenant with
  `X-Tenant-Id` (see Platform above). The platform tenant can never be suspended. Suspending keeps sessions (every check
  refuses them while suspended), so reactivation restores unexpired sessions.
  Done (step 10a): login, refresh and forgot-password of a suspended tenant's users answer 403 "Tenant is suspended"
  (`ClientResolver`); the JWT filter and introspection refuse them since 9c.
- Clients have a status of their own (done in step 11): `PUT /api/clients/{id}/suspend|activate`
  (`clients:suspend`/`clients:activate`); a suspended client refuses login, refresh and forgot-password (403, `ClientResolver`)
  and its sessions' tokens are refused by the JWT filter and introspection (`UserSessionRepository.findActor`). The
  management console is never suspended.

### Future (not now)
- OAuth2/OIDC compatibility (roadmap item 8). The `client_id` choice keeps this path open.
- First priority is organizing the code structure toward this design.

### Open questions
- None currently.

## Stack
Java 25 · Spring Boot 4.1 (Jackson 3 = `tools.jackson.*`) · Spring Security (stateless JWT, JJWT 0.12)
· Spring Data JPA · MySQL 8 + Flyway (Testcontainers MySQL in tests) · MapStruct + Lombok · springdoc OpenAPI · Gradle
· ELK for logs (Logstash tails `logs/application.log`).

## Commands
- Run tests: `./gradlew clean test` (**Docker must be running**: Testcontainers starts MySQL 8.0; jacoco report in `build/reports/jacoco`)
- Run app: `./gradlew bootRun` (loads `.env`; profile `local` unless `.env` sets `SPRING_PROFILES_ACTIVE`; expects MySQL on
  `localhost:3366`). IntelliJ's run configuration can load `.env` too, but it passes `# NAME=value` lines on (Spring binds
  them), so `.env` and `example.env` never keep commented-out settings.
- Run everything in Docker: `docker compose up --build` (app on :8080, MySQL on :3366, plus ELK)
  - App + DB only: `docker compose up --build app db`
  - The app container runs the `dev` profile, so it needs the mail and CORS settings from `.env` (a missing one stops
    it). DB settings and the issuer are set in `docker-compose.yml`.
- Profiles: exactly one of `local`, `dev`, `stage`, `prod` (`test` for the suite), checked at startup by
  `EnvironmentProfileCheck`. `application.properties` holds what every environment shares and `iam.required-settings`;
  `application-<profile>.properties` lists the environment's settings as `${ENV_VAR:}`. Every profile, `local` included
  (owner: everything can be tried locally), needs every required setting; only the test suite is relaxed (its own
  `application.properties`). A new environment-dependent setting goes into every profile file, `iam.required-settings`,
  `example.env` and the manual's profile table.
- Swagger UI: http://localhost:8080/swagger-ui.html

## Database schema rules
- Flyway owns the schema: `src/main/resources/db/migration/V<n>__<description>.sql`.
- Never edit an applied migration. Every schema change is a new `V<n+1>__...` file.
- When the current development plan is finished, all migrations are merged into a single `V1__baseline.sql` taken from the
  live schema; versioned migrations start from there. Each step is verified on a fresh database (`docker compose down -v`).
- The permission catalog and the role templates are reference data inserted by migrations (the only rows migrations insert).
  A migration that adds a TENANT key also adds it to the `ADMIN` template (a test fails otherwise) and decides which
  other templates get it. PLATFORM keys go into no template.
- Hibernate runs with `ddl-auto=validate` (app and tests), so an entity/schema mismatch fails at startup.
- No other seed data. The first root user is created by tenant signup.
- One root per tenant is a functional unique index (`uk_users_root_per_tenant`), not visible to Hibernate validation.
- Booleans are `BOOLEAN`, timestamps `DATETIME(6)`, and constraints get readable names (`fk_<table>_<ref>`, `uk_<table>_<col>`, `idx_<table>_<col>`).
- Every table with a `tenant_id` has an index starting with `tenant_id` (`idx_<table>_tenant`, or a unique key such as `uk_roles_tenant_name`).

## Current layout (`src/main/java/com/example/iamsystem`)
Package by feature. Each feature uses only the sub-packages it needs, always with these names:
`controller/`, `service/`, `dto/`, `entity/` (JPA entities + their enums), `repository/`, `mapper/` (MapStruct),
`config/` (`@ConfigurationProperties` records and feature configuration).
Shared code lives in `common/`. No generic top-level `util/`, `constant/`, `enums/` or `aspect/` packages:
constants and helpers belong to the feature that owns them. Tests mirror the main package of the class under test.

- `platform/`: the platform level. `PlatformGrantService`/`PlatformGrantController` (role and permission grants;
  `entity/PlatformRoleGrant`, `entity/PlatformPermissionGrant`);
  `PlatformBootstrap` creates the platform tenant (+ console + predefined roles) at startup,
  and its root from `config/PlatformProperties` (`iam.platform.root.*`) while it has none (missing config stops the
  startup unless `iam.platform.allow-missing-root`, set by the test suite only); `PlatformTenantController`/
  `PlatformTenantService` (`/api/platform/tenants`, PLATFORM keys only)
- `tenant/`: `Tenant` + `TenantStatus` + `TenantType` (`STANDARD`/`PLATFORM`), `TenantSignupService` (tenant + root user + predefined roles in one transaction), `TenantController`
- `user/`: one service per responsibility: `UserCreationService`, `UserPasswordService`, `UserRoleService`, `UserPermissionService`
  (direct permissions), `UserService` (paged list, find, delete), `UserStatusService` (deactivate, activate, unlock), `UserProfileService` (name and email: another user's,
  or your own through `PATCH /api/me`), `MeService` (`GET /api/me`, `MeController`);
  `UserFactory` builds every new user (validation, password encoding, expiry) for signup and creation.
  `UserAccessPolicy` decides what an actor may do to another user of the same tenant (no own roles/permissions, only
  management users receive access, password-reset rules).
  `UserValidator` (availability + password policy), `config/PasswordExpirationProperties`.
- `client/`: client applications. `ClientService` (CRUD in the caller's tenant, creates each tenant's console),
  `ClientResolver` (public `client_id` → client and tenant), `ClientIdGenerator`
- `passwordreset/`: self-service reset (OTP by email, user found by username or email in the client's tenant; the user then
  sets a password of their choice). `ResetOtpIssuer` is the one place that makes and emails OTPs (bcrypt hash only, used
  by both resets), counted by `OtpRequestLimiter`; `config/OtpExpirationProperties`
- `emailverification/`: `EmailVerificationService` (a logged-in user's own address: `requestCode` emails a code after
  commit, `confirm` checks it; `discardPending` on an email change), `EmailVerificationController`
  (`/api/users/email-verification`, `/confirm`), `EmailVerificationCode` (bcrypt hash, one per user),
  `config/EmailVerificationProperties` (`iam.email-verification.code-minutes`, 30)
- `otp/`: what every one-time code shares. `OneTimeCodes` (six `SecureRandom` digits, `MAX_FAILED_ATTEMPTS` = 5),
  `OtpRequestLimiter` (at most 3 codes per user and `OtpPurpose` per hour, `otp_requests`); wrong codes are
  `InvalidOtpException` (400)
- `email/`: `EmailService` is the only user of `JavaMailSender`: features hand it an `OutgoingEmail` (`EmailPurpose` +
  recipient + template values, masked in `toString`); it renders `resources/email/<template>.html` (sharing
  `layout.html`) and `.txt` with Thymeleaf (`config/EmailTemplateConfig`, core engine only, no web views) and sends both
  parts from `iam.mail.from` (`config/EmailProperties`). A new email = a new `EmailPurpose` + its two templates.
  `send` sends at once; `sendAfterCommit` (the default for new emails) publishes a `QueuedEmail` that `EmailQueue` (a
  `@TransactionalEventListener`, after commit) sends on its own small thread pool (`config/EmailQueueProperties`; not an
  `Executor` bean, which would replace Boot's default `@Async` executor); failures are logged, never retried.
- `role/`: tenant-owned roles. `RoleService` (CRUD), `RolePermissionService` (attach/detach permissions), `RoleAccessPolicy` (no changes to a role you hold),
  `PredefinedRoleService` (copies the `RoleTemplate`s into a new tenant during signup)
- `permission/`: the read-only catalog. `PermissionKey` enum (key + scope) mirrors the `permissions` rows (a test keeps them in sync);
  `@RequirePermission(PermissionKey)` on every protected endpoint
- `authorization/`: `AuthorizationService` = the only place answering "does user X hold service:action?";
  `@RequirePermission` + `AuthorizationAspect` enforce it on endpoints
- `auth/`: thin `AuthController` (`/api/auth/*`) + `AuthenticationService` (login, refresh, logout),
  `TokenIntrospectionService` (`/api/auth/introspect`) and DTOs;
  `JwksController` (`/.well-known/jwks.json`)
- `security/`: Spring Security wiring: `config/` (incl. `JwtProperties`; `CorsConfig` + `CorsProperties` = allowed browser
  origins), `filter/` (JWT filter), `jwt/` (`SigningKeys` = the
  RSA key set, `TokenIssuer`, `TokenVerifier`: access tokens only),
  `userdetails/` (`AuthenticatedActor` = the request principal; `CurrentUserProvider`, the single owner of "who is logged in"
  and of the current tenant id; `ActingTenantResolver` = which tenant a request works in (`X-Tenant-Id`);
  `TenantAuthenticationProvider` = login inside the client's tenant; `DefaultUserDetails`/
  `DefaultUserDetailsService` for login and refresh only), `event/` (login success/failure listeners), `service/FailedPasswordCounter` (the one owner of
  wrong-password counting and the lockout), `config/LockoutProperties`
- `session/`: login sessions. `UserSession` + `RefreshToken` (hash only), `SessionService` (start, refresh-token checks and
  rotation with reuse detection, revoke one / all / all but one), `RefreshTokenGenerator` (random token + SHA-256 hash)
- `audit/`: `@Auditable` (SpEL) → `AuditAspect` → MDC JSON log (`log_type=audit`)
- `common/`: `exception/` (`IamException` = base of our own failures, each subclass with its HTTP status; `GlobalHandler`
  (a `ResponseEntityExceptionHandler`) answers every error as an RFC 9457 `ProblemDetail`, also for the JWT filter and
  `JwtAuthenticationEntryPoint` through the `HandlerExceptionResolver`; anything else is a logged 500 without its
  message; `ErrorMessage`), `config/` (OpenAPI; `EnvironmentProfileCheck`,
  registered in `META-INF/spring.factories`; the mail sender is Spring Boot's, from `spring.mail.*`, used only by `email/`), `validation/` (`@ValidEmail`), `util/` (`DateUtil`)
- `src/main/resources/db/migration/`: Flyway migrations (`V1__baseline.sql` = schema as of the Flyway introduction)

## Current behaviours to remember
- Root user (`is_root_user`) holds every TENANT key in `AuthorizationService`; what it can reach is still limited to its tenant.
  The platform tenant's root also holds every PLATFORM key (`tenants:*`); a standard tenant's root never does.
- **Tenant boundary = tenant-scoped repository lookups** (`...AndTenantId`). Users and roles of other tenants are 404, never 403.
  Policies (`UserAccessPolicy`, `RoleAccessPolicy`) only decide rules inside the tenant.
- No self-modification: nobody changes their own roles or permissions, or a role they hold (403).
- Endpoint permissions are owned by `@RequirePermission`; the access policies never check permissions. One data-dependent
  check: creating a `MANAGEMENT` user also needs `users:create-management`, checked by `UserCreationService` through
  `AuthorizationService`.
- Users are `MANAGEMENT` or `REGULAR` (default), fixed at creation. Only management users hold roles and direct permissions
  (`user_permissions`); a user's permissions are the union of both. Root is `MANAGEMENT` and holds every TENANT key implicitly.
- Login: `POST /api/auth/authenticate` `{ client_id, username, password }`. The client's tenant id travels as the
  authentication details to `TenantAuthenticationProvider`; the console client refuses regular users (403).
  Refresh: `{ client_id, refresh_token }`, only through the issuing client, and refused when the client has refresh tokens off.
- Login and refresh answer like an OAuth2 token response: `access_token`, `token_type` (`Bearer`), `expires_in` (seconds,
  from the same number as the token's `exp`: `TokenIssuer` returns `IssuedAccessToken`), `refresh_token` (left out when the
  client has them off) and `username`. Spring Security's default headers send `Cache-Control: no-store` on them (RFC 6749
  §5.1 requires it; `SessionFlowTest` checks it).
- Token settings live on the client (`access_token_ttl_seconds`, `refresh_token_enabled`, `refresh_token_ttl_seconds`,
  `client_token_claims`). `SessionService.start` and `TokenIssuer.issueAccessToken(session)` read them; defaults are
  `ClientService.DEFAULT_TOKEN_SETTINGS` (5 min, refresh on for 24 h, `USERNAME`).
- CORS: only the origins in `iam.cors.allowed-origins` (`IAM_CORS_ALLOWEDORIGINS`, exact origins, validated at startup)
  may call the API from a browser on another origin; none by default. Methods GET/POST/PUT/PATCH/DELETE, headers
  `Authorization`, `Content-Type`, `X-Tenant-Id`, no credentials (tokens never travel in cookies). Other origins get 403.
  Per-client origins are roadmap item 7.
- Tokens are RS256-signed by the first key of `iam.jwt.keys` (kid + PKCS#8 PEM private key, public key derived); every
  configured key is published at `GET /.well-known/jwks.json` (public) and accepted by `TokenVerifier`, which only takes
  RS256 by a known `kid` and `typ: at+jwt`. Each key is a kid plus the PEM inline (`private-key`) or a location to read it
  from (`private-key-location`, e.g. `file:/run/secrets/...`; exactly one). No key configured → a temporary key with a
  WARN only where `iam.jwt.allow-temporary-key` (the test suite only); everywhere else the startup stops.
  `iss` = `iam.jwt.issuer` + `/tenants/{id}`, `aud` = `client_id`, `sid` = session id, plus `jti`.
- Sessions (`user_sessions`): one per login; expire with their latest refresh token (each refresh extends them by the
  client's refresh lifetime) or, without refresh tokens, with the access token. Refresh tokens (`refresh_tokens`) are
  32 random bytes, stored as SHA-256 hex, single use; a used one presented again revokes its session. Refresh runs in one
  transaction that commits on `InvalidTokenException` (401), so that revocation sticks.
- Session revocation: logout (that session), own password change (all but the current one), any password reset (all),
  user/client deletion (DB cascade; deleting a user also deletes their pending reset OTPs). Deactivation and lock don't revoke; the filter and refresh refuse such users.
- `PUT /api/users/{id}/deactivate|activate|unlock` (`users:deactivate` / `users:activate` / `users:unlock`,
  `UserStatusService`): idempotent, audited (`USER_DEACTIVATE` / `USER_ACTIVATE` / `USER_UNLOCK`), never yourself or the
  root (`UserAccessPolicy.checkCanChangeStatus`, 403);
  sessions are kept, so activation restores unexpired ones. `UserDto.active` shows the state.
- New catalog keys reach existing tenants' roles only when an admin adds them (template changes never reach existing
  tenants; a copied role keeps no link to its template). The root holds every TENANT key at once.
- `POST /api/tenants/signup` (public) creates a tenant + its root user + predefined roles + management console (its `client_id` is in the response). `POST /api/users` (`users:create`) creates a user in the
  caller's tenant. Anonymous calls to protected endpoints get 401; missing permissions get 403.
- Errors are RFC 9457 problems (`application/problem+json`: `title`, `status`, `detail` = our message, `instance` = path;
  `type` left out = `about:blank`). Invalid input adds `errors` (`field` + `message`): 422 for bodies, 400 for
  parameters (`@Min` on `page`/`size`). "Already exists" is 409. Stable error codes are roadmap item 2.
- `GET /api/users` is paged (`page`, `size` ≤ 100, ordered by id; Spring Data `PagedModel` JSON). Roles are a plain list.
- Deleting a user keeps the users they created (`created_by` → NULL); deleting a role removes it from its users.
- Nobody deletes a root user, not even the root itself (403), so every tenant keeps its root. Nobody deletes their own
  account either (403 "You can't delete your own account"); self-service deletion is roadmap item 5.
- Lockout: `FailedPasswordCounter` counts every wrong password (login, old password of a password change, current
  password of an email change) in one atomic update in its own transaction; 5 lock the account for 15 minutes (the filter
  then refuses its tokens); a correct one clears the count. A missing password is no guess. `PUT /api/users/{id}/unlock`
  (`users:unlock`, `FailedPasswordCounter.unlock`) ends a lock at once; `UserDto.lockedUntil` shows a running lock.
- `PATCH /api/users/{id}` (`users:update`) changes another user's `fullName` and `email` (left out = unchanged, `""` removes
  the name, an email can't be removed). A new email also needs `users:reset-password` (it receives the password resets),
  must be free in the tenant and not any root's (`UserValidator.validateEmailAvailable`; a root's email must be unused
  everywhere, `validateEmailAvailableForRoot`), and deletes the user's pending reset OTP. Never yourself, never the root.
  Your own profile: `PATCH /api/me` (management users; a new email needs `currentPassword`; the root's only way).
- `PATCH /api/users/password` changes only your own password (old password required). `POST /api/users/{id}/password-reset`
  (`users:reset-password`) emails another user a reset OTP, then disables their current password and ends their sessions
  (nothing changes when the OTP can't be sent: 400 no email, 429 limit, 503 mail failure); never your own, never root's.
  Passwords are set only by their owner (`UserPasswordUpdater`): own change with the old password, or reset with an OTP.
- OTPs are only ever sent by email: never in responses, logs or audit details. No password is ever emailed.
- Email verification: `users.email_verified`. New users (signup, `POST /api/users`) and every changed address start
  unverified (`UserProfileService.apply` also deletes the pending code); the platform root from config starts verified.
  Verified only by the user typing a code (`POST /api/users/email-verification` + `/confirm`, any logged-in user, own
  account in the home tenant); codes like reset OTPs (`OneTimeCodes`, 5 guesses, `OtpPurpose.EMAIL_VERIFICATION` limit
  3/hour, 30 minutes). No automatic emails (no page to link to yet, roadmap item 3); login never needs it. `UserDto` and
  `MeDto` show `emailVerified`. Forgot-password sends only to a verified address (`ResetOtpIssuer.issueAfterCommit`,
  outcome `EMAIL_NOT_VERIFIED`, same 200); the admin reset (`issueNow`) also to an unverified one; a completed reset
  verifies the address. Tokens with the `EMAIL` claim also carry `email_verified`.
- The security principal is `AuthenticatedActor` (`userId`, `username`, `tenantId`, `rootUser`, `sessionId`,
  `homeTenantId`), never the `User` entity. `tenantId` is the tenant the request works in (the tenant boundary used by the
  services); `homeTenantId` is the user's own tenant. They differ only when a platform user sends `X-Tenant-Id`
  (`ActingTenantResolver`, called by the JWT filter; ignored for tenant users; malformed 400, unknown 404). Inside another
  tenant the platform root holds every key; other platform users hold the TENANT keys their grants give on that tenant or
  on all tenants (`PermissionRepository.isGrantedToPlatformUserInTenant`, native query over `platform_role_grants` +
  `platform_permission_grants`); audit adds `acting_tenant_id`. Grants never apply inside the platform tenant itself. `JwtRequestFilter` finds it with one query (`UserSessionRepository.findActor`) that only matches a live
  session of an active, unlocked user of an `ACTIVE` tenant, through an `ACTIVE` client; anything else leaves the request
  unauthenticated (401). When the session is live but the account is blocked, a second query
  (`UserSessionRepository.findAccountState`, `SessionAccountState.refusalReason`) puts the reason into a request attribute
  and `JwtAuthenticationEntryPoint` answers 401 with it ("Your account is locked until ...", "Your account is
  deactivated", "Tenant is suspended", "Client is suspended"); otherwise "A valid access token is required". Public
  endpoints ignore a refused token as before.
  Introspection uses the same query, so the API and other applications agree on what "active" means. `User.@Version` is
  optimistic locking only. Permissions are read from the database on
  every check (`PermissionRepository.isGrantedToUser`); `AuthorizationService.permissionsOf` lists all of an actor's keys
  with the same rules (a test keeps the two in agreement). `GET /api/me` (`MeService`) returns the logged-in management
  user's profile, home and working tenant and those keys; no permission needed; regular users 403.

## Known issues (found in review 2026-10-24, not fixed yet)
Security
- The reset request's response time differs a little between a known and an unknown user (a known one's request writes
  the limit and OTP rows); the slow parts (bcrypt, the email) are evened out or after the response since step 13b.

Broken / drift
- Audit events for failures are logged at ERROR level.
- Ended sessions and used/expired refresh tokens are never purged (needs a cleanup job; roadmap item 1, session management).
- Sessions slide forever while refreshed: no absolute session lifetime yet (roadmap item 1).

Design debt

## Progress log
- 2026-10-24: Codebase reviewed. CLAUDE.md created. Target design agreed (platform/tenant/regular levels, client_id, fixed permissions + dynamic roles, no self-modification, per-application token settings, management-console client per tenant).
- 2026-10-24: Step 1 (baseline). Dockerfile on JDK 25, `app` service in docker-compose, fixed `ddl-auto` property name.
- 2026-10-24: Step 2 (single schema). Flyway `V1__baseline.sql` (from the entities), `ddl-auto=validate`, removed `db-init/`,
  tests on Testcontainers MySQL instead of H2, `.env` no longer passed to the test JVM.
- 2026-10-24: Step 3 (package by feature). Moved every class into feature packages with consistent sub-packages;
  no class renamed, no logic changed. Updated jacoco exclusions to the new paths.
- 2026-10-24: Step 4a (user feature cleanup). Split `UserService` into registration/password/role/query services,
  added `UserAccessPolicy`, `CurrentUserProvider`, `PasswordExpirationProperties`; removed `UserRoleAttachmentUtil`;
  thin `UserController`. Fixed: own password change via `?username=` no longer skips the old-password check.
  Code standards + clean-import rule added; all wildcard/unused imports removed.
- 2026-10-24: Step 4b (one owner for permission checks). New `AuthorizationService` (typed service + action, no strings);
  `PermissionService` is CRUD only (injected mapper, 404 on deleting a missing permission, duplicate check on update);
  `AuthController` logic moved to `AuthenticationService` (login no longer loads the user twice; bad/expired refresh
  tokens give 412 instead of 500); `AuditAspect` uses `CurrentUserProvider`. Removing user roles now needs `IAM:UPDATE`.
- 2026-10-24: Wrote `docs/IMPLEMENTATION_PLAN.md` (full step plan for resuming in a fresh session).
- 2026-10-24: Step 5 (tenant foundation). Flyway `V2__tenants.sql`: organizations → tenants, `tenant_id NOT NULL` on users,
  one root per tenant (functional unique index), `roles.is_global` dropped. `POST /api/tenants/signup` (tenant + root in one
  transaction), `POST /api/users` (`IAM:WRITE`) replaces `/api/users/register`; `UserFactory`; entities cleaned (id-based equals);
  `AuthorizationConfig` simplified (anonymous → 401). Decisions: usernames/emails global until step 8, no tenant slug yet.
  Added `docs/USER_MANUAL.md`.
- 2026-10-25: Step 6 (tenant isolation). Flyway `V3__tenant_isolation.sql`: roles `tenant_id NOT NULL` + `uk_roles_tenant_name`,
  `idx_<table>_tenant` on every tenant-owned table, `created_by` ON DELETE SET NULL, `user_roles.role_id` ON DELETE CASCADE.
  Tenant-scoped user and role lookups (other tenants → 404), no hierarchy inside a tenant, paged `GET /api/users`,
  no self role changes and no changes to a held role. `RoleService` split (`RolePermissionService`, `RoleAccessPolicy`),
  role DTOs as records, create role → 201. Decisions: usernames stay global until step 8, then per tenant, except root
  username/email unique across all users; `AuthenticatedActor` moved to step 6b; permissions untouched until step 7.
- 2026-10-25: Step 6b (lightweight principal). `AuthenticatedActor` replaces the `User` entity in the security context; the JWT
  filter loads it with one query that also rejects revoked tokens and inactive or locked users (bad tokens → 401, no more 500);
  `AuthorizationService` checks permissions with a DB query (changes apply immediately); unused `authorities` claim and
  Spring authorities removed; `JwtTokenUtil` slimmed; shared test helper `TestActors.actingAs`.
- 2026-10-25: Step 7a (fixed permission catalog). Flyway `V4__permission_catalog.sql`: `permissions` = `name` + `scope` +
  `description`, 10 TENANT keys inserted, `services` table dropped. `PermissionKey` enum + `@RequirePermission(key)`;
  permission endpoints read-only; root holds TENANT keys only; only TENANT keys attachable to roles; `/api/auth/authorize`
  removed. Passwords: `PATCH /api/users/password` is self-only; new `POST /api/users/{id}/password-reset`
  (`users:reset-password`, not self, not root); `NewPasswordIssuer` shared with the forgot-password flow, which now takes
  a username or email in a JSON body. Fixed: OTP expiry was in days instead of minutes; OTP now from `SecureRandom`;
  405/415/unreadable-body errors no longer become 500. Decisions: generic keys; merge all migrations into one baseline
  after the plan.
- 2026-10-25: Step 7b (predefined roles). Flyway `V5__predefined_roles.sql`: `role_templates` + `role_template_permissions`
  (`ADMIN` = every key, `USER_MANAGER` = users read/create/update/delete, `VIEWER` = read keys). Signup copies them into the
  new tenant in the same transaction (`PredefinedRoleService`). Decisions: no role-manager role; template changes never
  reach existing tenants.
- 2026-10-25: Step 7c (user types). Flyway `V6__user_types.sql`: `users.user_type` (`MANAGEMENT`/`REGULAR`, not updatable),
  `user_permissions`, new key `users:create-management` (ADMIN template). `POST /api/users` takes `userType` (default REGULAR);
  `PUT`/`DELETE /api/users/permissions` (`users:update`); regular users can't receive roles or permissions (400); permission
  check = roles ∪ direct permissions. `PermissionRepository.getRequiredTenantPermissions` shared by roles and users.
  Decisions: type never changes (create a new user instead); management creation needs its own key.
- 2026-10-25: Step 8a (clients). Flyway `V7__clients.sql`: `applications` → `clients` (one `MANAGEMENT_CONSOLE` per tenant,
  functional unique index), usernames/emails unique per tenant, `clients:*` keys (ADMIN; `clients:read` also VIEWER).
  Login/refresh/forgot-password carry `client_id`; `TenantAuthenticationProvider` replaces `DaoAuthenticationProvider`;
  tokens: `sub` = user id + `client_id` claim; lockout listeners by user id; `LockoutProperties`; deactivated accounts → 403.
  Decisions: 24-char URL-safe ids, secrets later, console = management users only, applications = all users.
- 2026-10-25: Step 8b (token settings). Flyway `V8__client_token_settings.sql`: lifetimes + refresh flag on `clients`,
  `client_token_claims`. `PUT /api/clients/{id}/token-settings` (`clients:update`); access 1 min–1 h, refresh 1 h–30 days and
  longer than access (422 otherwise); claims from `TokenClaim` (never roles/permissions); no refresh token when disabled.
  Decisions: defaults 5 min / refresh on 24 h / username for every client (option A); console settings editable.
  Next: step 9 (token redesign: asymmetric signing, JWKS, refresh-token store, introspection).
- 2026-10-25: Step 9a (RS256 + JWKS). Hard-coded HMAC secrets and `JwtConstant` removed; `JwtTokenUtil` split into
  `TokenIssuer`/`TokenVerifier`; `SigningKeys` from `iam.jwt.keys` (temporary key + WARN when none); `GET /.well-known/jwks.json`;
  access tokens get `typ: at+jwt`, `kid`, `iss` (per tenant), `aud`, `jti`. Decisions: one platform key set (per-tenant keys
  → roadmap item 10), RS256, logout by bearer token (9b), public introspection until client secrets (9c).
  Next: step 9b (sessions, opaque rotated refresh tokens, logout).
- 2026-10-25: Step 9b (sessions). Flyway `V9__user_sessions.sql`: `user_sessions`, `refresh_tokens` (SHA-256 hash).
  Refresh tokens are opaque, single use and rotated; reuse revokes the session; access tokens carry `sid` and the filter checks
  the session; `version` claim and refresh JWTs removed; `POST /api/auth/logout` (204); refresh errors 401 instead of 412
  (`InvalidTokenException` replaces `JwtException`); password change/reset end sessions. Decisions: own password change keeps
  the current session; deactivation/lock don't revoke (checked per request); sessions slide with each refresh.
  Next: step 9c (introspection).
- 2026-10-25: Step 9c (introspection). `POST /api/auth/introspect` (RFC 7662 style, public): active with the token's claims
  only for a valid token of a live session whose user is usable and whose tenant is ACTIVE, otherwise just `active: false`
  (always 200). `/api/auth/token/validate` and `TokenVerifier.isValid` removed. The JWT filter now also refuses users of a
  non-ACTIVE tenant (ready for step 10). Step 9 complete.
  Next: step 10 (platform tenant, super users, tenant suspension).
- 2026-10-25: Step 10 planned in small sub-steps (10a-10j; owner asked for commits touching only a few classes). Platform
  design agreed: platform root does everything everywhere; platform users get role/permission grants per tenant or for all
  tenants; `X-Tenant-Id` to act inside a tenant; `tenants:*` keys; client suspension after step 10.
- 2026-10-25: Step 10a (suspended tenants). `ClientResolver` refuses clients of a non-ACTIVE tenant (403 "Tenant is
  suspended"), which covers login, refresh and forgot-password.
  Next: step 10b (platform tenant).
- 2026-10-25: Step 10b (platform tenant). Flyway `V10__tenant_type.sql`: `tenants.type` + `uk_tenants_platform` (one
  platform). `PlatformBootstrap` (ApplicationRunner) creates the "Platform" tenant with its console and predefined roles on
  first startup, through the same services signup uses. Decision: created in Java, not by migration.
  Next: step 10c (platform root from config).
- 2026-10-25: Step 10c (platform root). `PlatformProperties` (`IAM_PLATFORM_ROOT_USERNAME/PASSWORD/EMAIL/FULL_NAME`);
  `PlatformBootstrap` creates the platform root through `UserFactory.newRootUser` while the platform has no root (WARN
  without complete config; invalid config stops startup; later config changes are ignored). docker-compose passes the vars.
  Next: step 10d (`tenants:*` keys, list/view tenants).
- 2026-10-25: Fix: plaintext passwords in the log. Spring MVC logged request bodies at DEBUG (`logging.level.root=DEBUG`),
  passwords included. Root level now INFO, and every credential-carrying record (`UserLoginDto`, `UserCreationDto`,
  `PasswordChangeDto`, `PasswordResetDto`, `JwtRefreshTokenDto`, `IntrospectionRequest`, `PlatformProperties.RootUser`) masks
  the secret in `toString()` (`SecretMask`). New code standard: mask sensitive data.
- 2026-10-25: Step 10d (platform keys, list tenants). Flyway `V11__platform_permissions.sql`: `tenants:read/create/suspend/
  activate` (PLATFORM, in no template). `AuthorizationService`: the platform root holds every PLATFORM key.
  `PlatformTenantController`/`PlatformTenantService`: `GET /api/platform/tenants` (paged) and `/{id}`; `TenantDto` has `type`.
  Next: step 10e (suspend / activate).
- 2026-10-25: Step 10e (suspend / activate). `PUT /api/platform/tenants/{id}/suspend|activate` (`tenants:suspend` /
  `tenants:activate`), idempotent, audited (`TENANT_SUSPEND`/`TENANT_ACTIVATE`); the platform tenant can't be suspended
  (400); sessions are kept, so activation restores unexpired ones.
  Next: step 10f (create a tenant as a super user).
- 2026-10-25: Step 10f (create tenants). `POST /api/platform/tenants` (`tenants:create`) runs `TenantSignupService.signup`
  (same body, rules and result; the `TENANT_SIGNUP` audit names the super user as actor). Public signup stays open.
  Next: step 10g (`X-Tenant-Id` for the platform root).
- 2026-10-25: Step 10g (`X-Tenant-Id`). `AuthenticatedActor` gets `homeTenantId`; `tenantId` = working tenant, so services
  are unchanged. `ActingTenantResolver` (platform users only) + filter (errors through `GlobalHandler`);
  `AuthorizationService`: in another tenant only the platform root; `changeOwnPassword` uses the home tenant; audit
  `acting_tenant_id`. Decisions: services keep `actor.tenantId()` (owner: self-descriptive); `created_by` may be the
  platform root; the platform root may work inside a suspended tenant.
  Next: step 10h (platform grants).
- 2026-10-25: Step 10h (platform grants). Flyway `V12__platform_grants.sql`: `platform_role_grants`,
  `platform_permission_grants` (`tenant_id` NULL = all standard tenants, also later ones; unique per user + role/permission
  + tenant). `AuthorizationService` evaluates them for platform users working in another tenant (TENANT keys only).
  Step 10 re-split: 10i per-tenant platform checks, 10j grant endpoints, 10k remove `is_super_user`.
  Decisions: grants only for platform management users and platform roles (checked when granting, 10j).
  Next: step 10i (per-tenant platform permissions).
- 2026-10-25: Step 10i (platform roles). The platform tenant's roles may hold PLATFORM keys
  (`PermissionRepository.getRequiredPermissions`); standard tenants' roles and all direct user permissions stay TENANT
  only. Decision (option A): PLATFORM keys take effect only through grants, so `AuthorizationService` never answers a
  PLATFORM key from normal roles. Fix: the 10h grant query (`SELECT EXISTS`) failed on MySQL (Long vs boolean); it is a
  count now (`countPlatformGrants`). Step 10 re-split again: 10j per-tenant platform checks, 10k grant endpoints,
  10l remove `is_super_user`; platform tenant = management users only (own small commit).
  Next: platform tenant without regular users, then 10j.
- 2026-10-25: Step 10i+ (platform tenant content). The platform tenant refuses REGULAR users (`UserCreationService`) and
  application clients (`ClientService`), 400; it holds only management users and its console. Roadmap items 11
  (client-scoped regular users) and 12 (self-registration per tenant, Auth0-like, required later) added (renumbered
  2026-10-26: now items 6 and 2).
  Next: step 10j (per-tenant platform checks).
- 2026-10-25: Step 10j (platform checks per tenant). `AuthorizationService.platformScope` (all tenants or the granted ones;
  tenant users 403) + `checkPlatformPermission(..., tenantId)` / `checkPlatformPermissionOnAllTenants`; one grant query
  (`PermissionRepository.findGrantedTenantIds`, NULL = all tenants). `PlatformTenantService` checks every operation on its
  tenant (list = readable tenants, empty without grants; view/suspend/activate the path tenant; create needs an
  all-tenants grant); the controller has no `@RequirePermission` any more. Decisions: no access to a tenant = 403;
  no grants = empty list.
  Next: step 10k (grant endpoints).
- 2026-10-25: Step 10k (role grants API). `PlatformRoleGrant` entity + repository + mapper; `PlatformGrantService`:
  `GET /api/platform/users/{userId}/grants` (`users:read`), `POST /api/platform/grants/roles` (201) and
  `DELETE /api/platform/grants/roles/{id}` (`users:update`), audited. Only from inside the platform tenant; only platform
  roles; only to platform management users, never yourself or the platform root; only standard tenants or all; no
  duplicates. Decision: grants are managed with `users:read`/`users:update` held in the platform tenant.
  Next: step 10l (permission grants API).
- 2026-10-25: Step 10l (permission grants, bulk). `PlatformPermissionGrant` + repository. Owner asked for lists: grants and
  removals take `roleIds` / `permissionIds` like role assignment: `POST /api/platform/grants/roles|permissions` (201, the
  user's grants of those ids) and `DELETE` with the same body (204), all or nothing; existing grants are kept, missing
  ones ignored (no "already exists" error, no delete by grant id). The listing adds `permissionGrants`.
  Next: step 10m (remove `is_super_user`).
- 2026-10-25: Step 10m. Flyway `V13__drop_super_user_flag.sql` drops `users.is_super_user` (and the `User` field).
  Step 10 complete (platform tenant, platform root, tenant suspension, `X-Tenant-Id`, grants).
  Next: step 11 (client status), or one of the security and quality fixes; owner decides.
- 2026-10-25: Step 11 (client status, one commit by owner's request). Flyway `V14__client_status.sql`: `clients.status`,
  keys `clients:suspend` / `clients:activate` (ADMIN template). `ClientService.suspendClient/activateClient` (idempotent,
  audited, never the console); `ClientResolver` refuses suspended clients (403 "Client is suspended"); the session query
  requires an ACTIVE client, so the filter and introspection refuse its tokens; `ClientDto.status`. Sessions are kept,
  like tenant suspension. Next: owner decides (security fixes, ProblemDetail, migration baseline merge).
- 2026-10-25: Security fix 1 (mail debug). `MailConfig` set `mail.debug=true`, which printed every email (OTPs, new
  passwords) to the console. Removed the class; Spring Boot's auto-configured sender reads `spring.mail.*`
  (SMTP auth + STARTTLS in `application.properties`). Next: security fix 2 (user deletion with a pending OTP).
- 2026-10-25: Security fix 2. Flyway `V15__password_reset_otp_cascade.sql`: deleting a user deletes their pending reset
  OTPs (the delete failed on the foreign key before). Next: security fix 3 (nobody deletes a root user).
- 2026-10-25: Security fix 3. `UserAccessPolicy.checkCanDelete` refuses every deletion of a root user, its own included
  (403 "The root user can't be deleted"); a root could delete itself before and leave its tenant without one, and the same
  went for the platform root. Next: security fix 4 (uniform reset-request response).
- 2026-10-25: Security fix 4. `POST /api/password/reset-request` answers 200 with the same text for an unknown user, a
  user without email and a failed email (was 404 / 400 / 500). `PasswordResetService.createPasswordResetOtp` returns an
  `OtpRequestOutcome` (`SENT`, `UNKNOWN_USER`, `NO_EMAIL`, `MAIL_FAILED`) that the audit records (`request_outcome`), with a WARN
  or ERROR log. Decisions: a failed email is no error for the caller; the timing difference is a known issue for now.
  Next: security fix 5 (OTP attempts and lookup by user).
- 2026-10-25: Security fix 5. Flyway `V16__password_reset_otp_attempts.sql`: `failed_attempts`, OTPs no longer unique.
  `resetPassword` finds the user in the client's tenant first and compares (constant time) with that user's OTP only,
  locked for update; every failure is 400 "Invalid or expired OTP"; wrong guesses are counted (commits on the exception)
  and the 5th deletes the OTP. `PasswordResetOTP` lost `@Data` (id-based equals, masked `toString`).
  Next: security fix 6 (hashed OTPs).
- 2026-10-25: Security fix 5b (reset request limit). Flyway `V17__password_reset_requests.sql`. `ResetRequestLimiter`
  allows 3 OTPs per user per hour (the user's row is locked while counting, `UserRepository.lockById`); over the limit
  the request gets the usual 200, sends nothing and is audited as `TOO_MANY_REQUESTS`. With 5 guesses per OTP that caps
  guessing at 15 per hour. Decision: never lock the account for wrong OTPs (anyone could lock anyone out).
  Next: security fix 6 (hashed OTPs).
- 2026-10-25: Security fix 5c. `ResetRequestLimiter.tryRecord` runs in its own transaction (`REQUIRES_NEW`), so the
  user-row lock lasts milliseconds instead of until the OTP email is sent (a login of that user had to wait for it).
  Plan step 13 added: one email service, templates per purpose, asynchronous sending after commit.
  Next: security fix 5d (mail timeouts), then 6 (hashed OTPs).
- 2026-10-25: Security fix 5d. SMTP connect, read and write timeouts of 10 s (`spring.mail.properties.mail.smtp.*timeout`);
  JavaMail waited forever by default, so a hanging mail server could hang a request (and the reset OTP lock) indefinitely.
  Verified against a silent local server: the send fails after 10 s. Next: security fix 6 (hashed OTPs).
- 2026-10-25: Security fix 6. Flyway `V18__password_reset_otp_hash.sql`: `otp` → `otp_hash` (pending plain OTPs deleted).
  OTPs are stored as a bcrypt hash through the shared `PasswordEncoder` and checked with `matches`. Decision (option A):
  bcrypt, not SHA-256 (a million six-digit values hash in under a second) and not an HMAC secret (one more key to
  manage). Next: security fix 7 (no emailed passwords).
- 2026-10-25: Security fix 7a (forgot-password sets a chosen password). `POST /api/password/reset` takes `newPassword`; no
  password is emailed any more by this flow. `UserPasswordUpdater` is the one place that sets a chosen password (policy,
  hash, expiry, ends a login lockout), used by the reset and by your own password change. Decisions (option B): users
  choose their password; a reset ends a login lockout. Next: 7b (admin reset emails an OTP, `NewPasswordIssuer` goes).
- 2026-10-25: Security fix 7b (admin reset sends an OTP). `POST /api/users/{id}/password-reset` emails the user a reset OTP
  through `ResetOtpIssuer` (extracted from `PasswordResetService`, shared by both resets, counted by the hourly limit),
  then `UserPasswordUpdater.disablePassword` and all sessions end; if no OTP goes out nothing changes (400 / 429 / 503,
  new `TooManyRequestsException`, `EmailNotSentException`). `NewPasswordIssuer` removed: no password is ever emailed.
  Decisions: the old password stops working at once; admin OTPs count towards the limit; same 5-minute lifetime.
  Security fixes done; remaining: fail startup without a JWT key outside dev/test (config step).
- 2026-10-26: Frontend plan written in the sister folder `IAM System UX/README.md` (product-manager view: steps, mockups,
  every endpoint). New working rule: every backend change that affects the API updates that doc too.
- 2026-10-26: Fix: the password policy's special characters were `[@#!$%^&-+=()]`, where `&-+` is a range, so `-` was
  refused and `'` `*` accepted. The hyphen is escaped; the set is exactly `@#!$%^&-+=()` as documented. Also fixed stale
  docs (forgot-password 404, 401 vs 403 at login, 429/503 rows, admin reset OTP summary).
- 2026-10-26: CORS. `CorsProperties` (`iam.cors.allowed-origins`, exact origins, invalid ones stop startup) and `CorsConfig`
  (every path; GET/POST/PUT/PATCH/DELETE; `Authorization`, `Content-Type`, `X-Tenant-Id`; no credentials; preflight cached
  1 h); `AuthorizationConfig` turns CORS on. Decisions: one platform-wide list now (option A); per-client allowed web
  origins later (plan step 15, option B).
- 2026-10-26: `GET /api/me`, commit 1 of 2. `AuthorizationService.permissionsOf(actor)` lists every key the actor holds in
  the working tenant with the same rules as `hasPermission`, in at most two queries (`PermissionRepository.
  findNamesGrantedToUser` / `findNamesGrantedToPlatformUserInTenant`); `PermissionsOfTest` compares both against MySQL
  for every kind of actor. Decision: `/api/me` is for management users only (regular users 403).
- 2026-10-26: `GET /api/me`, commit 2 of 2. `MeController` + `MeService` + `MeDto`: profile, `homeTenant` and
  `workingTenant` (`TenantDto`; the working tenant follows `X-Tenant-Id`), `permissions` = `permissionsOf` keys, sorted.
  No `@RequirePermission`; regular users 403 "Only management users can use this endpoint". Platform grant scopes per
  tenant (`tenants:*` on which tenants) are not included (frontend gap G-16).
- 2026-10-26: Profile update, commit 1 of 2. `PATCH /api/users/{id}` (`UserProfileService`, `UserUpdateDto`, audit
  `USER_UPDATE` with the field names only); `UserAccessPolicy.checkCanUpdateProfile` (not yourself, not the root);
  `ResetOtpIssuer.discardPending`. `EmailValidator` cleaned (null is valid, compiled pattern, dots escaped: `a@bcom` was
  accepted); creation keeps requiring an email (`@NotNull`). Decisions: a new email needs `users:reset-password`; only the
  root changes the root's details; your own email change will need your password (commit 2, `PATCH /api/me`); pending
  reset OTPs are deleted on an email change; email verification later (plan step 13).
- 2026-10-26: Profile update, commit 2 of 2. `PATCH /api/me` (`UserProfileService.updateOwnProfile`, `OwnProfileUpdateDto`
  with a masked `currentPassword`): management users only (`UserAccessPolicy.checkIsManagementUser`, now also used by
  `MeService`), a new email needs the current password (400 otherwise), a root's email is checked across all tenants;
  answers like `GET /api/me`. Known issue added: wrong current/old passwords aren't counted.
- 2026-10-26: Fix: wrong old/current passwords counted. `FailedPasswordCounter` (`recordFailure` in its own transaction,
  atomic `incrementFailedLoginAttempts`; `recordSuccess`) used by the login listeners, `changeOwnPassword` and the own
  email change. Fixes a lost-update race in the login listener too (it read, added 1 and wrote back). Decision (option A):
  the same counter and lockout as login.
- 2026-10-26: Refusal reasons. A token whose live session belongs to a blocked account gets 401 with the reason (owner
  asked: a locked user should be told so); `JwtAuthenticationEntryPoint` writes the usual error JSON (`ExceptionResponse`)
  instead of `sendError`. Decisions: 401, not login's 403 (the token can't be used; 403 means "stay logged in" to the
  frontend); reasons only for live sessions (their holder owns the account), never for unknown or ended tokens.
- 2026-10-26: `expires_in`. Login and refresh add `token_type: Bearer` and `expires_in`; `TokenIssuer.issueAccessToken`
  returns `IssuedAccessToken` (token + lifetime), so `exp` and `expires_in` share one number. `JwtResponse` is a record
  with masked tokens in `toString`. `Cache-Control: no-store` was already sent (Spring Security defaults); a test keeps it.
  Decision: no `refresh_expires_in` (not standard).
- 2026-10-26: Own account deletion. `UserAccessPolicy.checkCanDelete` refuses deleting yourself (403), like every other
  self-modification. Decisions (option A): another user deletes you; a deliberate self-service deletion with the current
  password is plan step 16 (option B, with self-registration; moved to roadmap item 3 the same day).
- 2026-10-26: Profiles, commit 1 of 2. `local`, `dev`, `stage`, `prod` (owner's rule: optional in `local`, required
  everywhere else, `dev` included); `EnvironmentProfileCheck` (an `EnvironmentPostProcessor`) refuses a run without
  exactly one of them and names every missing required setting (`iam.required-settings`). DB, issuer, mail and CORS
  moved into the profile files; `bootRun` defaults to `local` (and skips commented `.env` lines); compose runs `dev`
  and passes mail and CORS from `.env` without empty defaults. Decisions: an explicit check instead of placeholders
  without defaults (one message lists every missing setting, and an empty value counts as missing); commit 2 adds the JWT key (also from a PEM
  file, temporary key only in `local`) and the platform root (required outside `local` only while the platform has no
  root).
- 2026-10-26: Profiles, commit 2 of 2. `local` is now as strict as the others (owner: try everything locally; only the
  test suite is relaxed). Signing keys: `private-key-location` (read through the `ResourceLoader`) next to
  `private-key`, exactly one per key; an all-empty entry is no key; without a key the startup stops unless
  `iam.jwt.allow-temporary-key` (test suite). Platform root: missing config stops the startup while the platform has no
  root, unless `iam.platform.allow-missing-root` (test suite). Both keys and the root are listed in every profile file; compose
  mounts the PEM as a secret (`IAM_JWT_KEY_FILE`). `SigningKeyProperties` masks its key in `toString`. Decisions: keep
  the inline key for platforms without files; the root config is needed only until the root exists (owner).
- 2026-10-26: Roadmap reorganized by priority and work volume (owner): 14 items in three priority groups, each with its
  size (S/M/L) and the seam it builds on (moved from the plan's section 5). Plan step 16 became roadmap item 3;
  maker-checker (item 9) and the OAuth2/OIDC provider (item 5) joined from CLAUDE.md; SSO and identity federation
  merged (item 12). New rule: the plan takes no new steps, new ideas go to the roadmap.
- 2026-10-27: Step 12a (deactivate / activate users). Flyway `V19__user_status.sql`: `users:deactivate`, `users:activate`
  (ADMIN and USER_MANAGER templates). `PUT /api/users/{id}/deactivate|activate` (`UserStatusService`, idempotent, audited);
  never yourself or the root; `UserDto.active`. Decisions: own keys, not `users:update` (offboarding is more than an
  edit); sessions kept (as in 9b); existing tenants' roles don't get new keys automatically (option A, as in 7b).
  Next: step 12b (unlock).
- 2026-10-27: Step 12b (unlock). Flyway `V20__user_unlock.sql`: `users:unlock` (ADMIN and USER_MANAGER templates).
  `PUT /api/users/{id}/unlock` (`UserStatusService.unlockUser`, audited `USER_UNLOCK`) clears the lock and the count
  through `FailedPasswordCounter.unlock` (shares the clearing with `recordSuccess`); never yourself or the root (a locked
  root waits). `UserDto.lockedUntil` = end of a running lock, else left out. Step 12 complete.
  Next: step 13 (asynchronous templated email).
- 2026-10-27: Step 13a (email service). `email/` feature: `EmailService` (the only `JavaMailSender` user), `OutgoingEmail`,
  `EmailPurpose`, Thymeleaf templates (HTML with a shared layout + plain text) in `resources/email/`; new required
  setting `iam.mail.from` (`IAM_MAIL_FROM`, validated). `ResetOtpIssuer` sends through it; the email constants left
  `PasswordResetConstants`. Integration tests mock `EmailService` and read the OTP from the handed-over values.
  Decisions (all recommended options): Thymeleaf HTML + text; admin reset stays synchronous (keeps its 503) while
  forgot-password goes async in 13b; no outbox (it would store OTPs in plain text); no retries; 13b also evens out the
  bcrypt work of unknown users; email verification is 13c. Next: step 13b (asynchronous sending after commit).
- 2026-10-27: Step 13b (asynchronous email). `EmailService.sendAfterCommit` + `EmailQueue` (after commit, own thread
  pool, `iam.mail.queue.*`); outside a transaction it refuses (the email would never go out). `ResetOtpIssuer`:
  `issueAfterCommit` (forgot-password, outcome `QUEUED`) and `issueNow` (admin reset, keeps `MAIL_FAILED` → 503); the
  bcrypt hash is made first on every path and `spendIssuingTime` makes one for an unknown user, so the response time no
  longer tells accounts apart (known issue reduced to the small database difference). Next: step 13c (email
  verification design).
- 2026-10-27: Step 13c-1a (shared one-time codes, no behaviour change). Flyway `V21__otp_requests.sql`:
  `password_reset_requests` → `otp_requests` with a `purpose`. New `otp/` package: `OneTimeCodes` (code generation and
  the 5-guess limit), `OtpRequestLimiter` (was `ResetRequestLimiter`, now per purpose), `OtpPurpose`;
  `InvalidPasswordResetOTPException` → `InvalidOtpException`. Next: step 13c-1b (email verification).
- 2026-10-27: Step 13c-1b (email verification). Flyway `V22__email_verification.sql`: `users.email_verified` (existing
  rows verified), `email_verification_codes`. `emailverification/` feature (request and confirm, audited
  `EMAIL_VERIFICATION_REQUEST` / `EMAIL_VERIFICATION`), `EmailPurpose.EMAIL_VERIFICATION` templates, `emailVerified` in
  `UserDto` and `MeDto`; new addresses (creation, signup, email changes) are unverified, the configured platform root is
  verified. Next: step 13c-2 (resets only to verified addresses, `email_verified` claim).
- 2026-10-27: Step 13c-2. Forgot-password emails only verified addresses (`EMAIL_NOT_VERIFIED` outcome, same answer and
  bcrypt work); the admin reset still reaches unverified ones (the admin vouches); a completed reset verifies the
  address; `email_verified` claim with `EMAIL` (`TokenIssuer`). Step 13 complete.
- 2026-10-27: Plan step 14 (links in reset and verification emails) moved to the roadmap as item 2 (owner), before
  self-registration, which needs it; roadmap items 2-14 became 3-15 (older progress-log entries keep their numbers).
  Next: step 15 (per-client web origins), the last plan step.
- 2026-10-27: Plan step 15 (per-client web origins) moved to the roadmap as item 6 (owner), before OAuth2/OIDC; roadmap
  items 6-15 became 7-16. The plan has no steps left.
- 2026-10-27: Error step, commit a (no format change). `IamException` (status per class) replaces 14 one-off handlers
  with one; the four "already exists" exceptions are one `AlreadyExistsException`; the `IllegalArgumentException` → 400
  catch-all is gone (a library's internal message reached the caller; such errors are a 500 now), so paging is checked
  with `@Min` on `page` and `size`. Decisions (all recommended): `ProblemDetail` with `type` `about:blank`, no stable
  error `code` yet, "already exists" becomes 409, no transitional `message` field. Next: commit b (the `ProblemDetail`
  format).
- 2026-10-27: Error step, commit b. RFC 9457 `ProblemDetail` answers: `GlobalHandler` extends
  `ResponseEntityExceptionHandler` (Spring MVC's own errors become problems too), `errors` lists invalid body fields (422)
  and parameters (400); `JwtAuthenticationEntryPoint` answers through the handler (`AuthenticationRequiredException`), so
  every 401 has the same shape; `ExceptionResponse` removed; "already exists" is 409. New roadmap item 2 (stable error
  codes, frontend gap G-4); items 2-16 became 3-17. Error step complete.
