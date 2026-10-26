# IAM System: Implementation Plan

This file lets any new session pick up the work without the conversation that produced it.

- **`CLAUDE.md`** (repo root) is the source of truth for the working rules, the **agreed** target design and the current state.
  Read it first.
- **This file** holds the step-by-step technical plan for getting from the current code to the target design.
  Everything below the "Done" section is a **proposal**. Each step starts by confirming its open decisions with the owner.

---

## 1. How to resume in a fresh session

1. Read `CLAUDE.md` in full: working agreement, code standards, commit rules, target design, known issues, progress log.
2. Run `git log --oneline -15` and compare it with the progress log in `CLAUDE.md` to see which step was finished last.
3. Run `./gradlew clean test` (Docker must be running) to confirm a green baseline before changing anything.
4. Find the next step in section 4 of this file. Present its plan to the owner: touched classes, cleanup per class, migration, and the
   open decisions as questions. **Wait for the go-ahead.**
5. Implement → `./gradlew clean test` → smoke-test in Docker (`docker compose up --build app db`, app on port 8080)
   → update `CLAUDE.md`, this file and `docs/USER_MANUAL.md` (if usage changed) → show the
   result and propose a one-line commit message → commit/push **only** after explicit approval.

## 2. Working rules (summary; full text in `CLAUDE.md`)

- The owner decides. One small step at a time; work on `master`; no branches unless asked.
- Every class a step touches ends that step fully clean (see "Code standards" in `CLAUDE.md`), including clean, explicit imports.
- Every test method has a plain-English `@DisplayName`.
- Flyway owns the schema; never edit an applied migration; `ddl-auto=validate`.
- Commit messages: one brief line, no AI attribution. Never commit or push without explicit approval.

---

## 3. Done

| Step | What | Commit(s) |
|---|---|---|
| Review | Codebase review, `CLAUDE.md`, target design agreed | `7a78b88` |
| 1 | Baseline: Dockerfile on JDK 25, `app` service in compose, fixed `ddl-auto` property | `d394cfc` |
| 2 | Flyway `V1__baseline.sql`, `ddl-auto=validate`, tests on Testcontainers MySQL | `ca26ea1` |
| CI | Tests on every push; Docker image built from `Dockerfile` and pushed only from `master` | `97e1b7f`, `8a8d3a1` |
| 3 | Package-by-feature layout | `8f164f3` |
| 4a | `UserService` split by responsibility, `UserAccessPolicy`, `CurrentUserProvider`, code standards | `98a5965` |
| 4b | `AuthorizationService` is the single owner of permission checks; `AuthenticationService` | `9c8fe14` |
| Docs | This plan; user manual (`docs/USER_MANUAL.md`) | `d188c69`, `dc3482a` |
| 5 | Tenants: `V2__tenants.sql`, `POST /api/tenants/signup` (tenant + root atomically, one root per tenant), `POST /api/users` | `c104519` |
| 6 | Tenant isolation: `V3__tenant_isolation.sql`, tenant-scoped user/role lookups, paged user list, no self role changes, `RoleService` split | `a687558` |
| Tests | `@DisplayName` on every test (now a rule) | `fa06792` |
| 6b | `AuthenticatedActor` principal, one-query JWT filter (revoked/inactive/locked → 401), DB permission checks | `1ab8f81` |
| 7a | Fixed permission catalog (`V4`), `PermissionKey`, read-only permission API, password change vs. reset split, forgot-password by username or email | `1f4f5e2` |
| 7b | Predefined roles (`V5`): `ADMIN`, `USER_MANAGER`, `VIEWER` templates copied into each new tenant at signup | `24b07c8` |
| 7c | User types (`V6`): `MANAGEMENT`/`REGULAR`, `users:create-management`, direct user permissions | `b7dc4e1` |
| 8a | Clients (`V7`): `client_id` login/refresh/forgot-password, console client per tenant, per-tenant usernames/emails | `5f2c416` |
| 8b | Per-client token settings (`V8`): lifetimes, refresh on/off, selectable claims | `099f14b` |
| 9a | RS256 signing with configured keys, `/.well-known/jwks.json`, `iss`/`aud`/`jti`/`kid` | `9fdae12` |
| 9b | Sessions (`V9`), single-use rotating opaque refresh tokens, reuse detection, logout, 401 on refresh errors | `efe589f` |
| 9c | `POST /api/auth/introspect` (RFC 7662 style), `/token/validate` removed, filter refuses non-ACTIVE tenants | `1e90eea` |
| 10a | Suspended tenant: login, refresh and forgot-password answer 403 (`ClientResolver`) | `4327479` |
| 10b | Platform tenant (`V10` `tenants.type`), created at startup by `PlatformBootstrap` | `167ec71` |
| 10c | Platform root created at startup from `IAM_PLATFORM_ROOT_*` | `ec724ae` |
| fix | No credentials in logs: root log level INFO, secrets masked in `toString()` (`SecretMask`) | `7b6315e` |
| 10d | PLATFORM keys `tenants:*` (`V11`), platform root holds them, `GET /api/platform/tenants` | `bed1c17` |
| 10e | `PUT /api/platform/tenants/{id}/suspend` and `/activate` (never the platform tenant; sessions kept) | `0e0f5fc` |
| 10f | `POST /api/platform/tenants`: super users create tenants through the signup service | `0effa3d` |
| 10g | `X-Tenant-Id`: the platform root works inside any tenant through the normal APIs | `fdca718` |
| 10h | Platform grants (`V12`); grants give TENANT keys inside the granted tenant(s) | `ef6937f` (query fixed in 10i) |
| 10i | Platform roles may hold `tenants:*` keys; PLATFORM keys only through grants; grant query fixed | `89b1e0a` |
| 10i+ | The platform tenant refuses regular users and application clients | `bdf6d18` |
| 10j | Platform endpoints check PLATFORM keys per tenant (`TenantScope`) | `08de269` |
| 10k | Role grants API (`/api/platform/grants/roles`, `/api/platform/users/{id}/grants`) | `10cf07e` |
| 10l | Permission grants; grants given and removed in bulk (`roleIds` / `permissionIds`, `DELETE` with the same body) | `f03eff7` |
| 10m | `is_super_user` dropped (`V13`); step 10 complete | `ca015ec` |
| 11 | Client status (`V14`): suspend / activate clients; suspended clients refuse logins and tokens | step 11 commit |
| CORS | Allowed browser origins from `iam.cors.allowed-origins` (`CorsProperties`, `CorsConfig`); none by default | CORS commit |
| Me | `GET /api/me` for management users: profile, home and working tenant, permission keys (`AuthorizationService.permissionsOf`) | `04a77e4`, me commit |

---

## 4. Remaining steps

Order matters: each step relies on the seams the earlier ones created. Before each step, list the touched classes and their
cleanup (working-agreement rule).

### Step 5: Tenant foundation (DONE, kept for reference)

Decisions taken: usernames and emails stay **globally** unique until step 8; **no tenant slug** yet; endpoints
`POST /api/tenants/signup` + `POST /api/users` (`/api/users/register` removed); the local DB was reset (no data migration).

**Goal:** tenants exist as a real concept. Signing up creates a tenant and its single root user in one transaction.

Proposed design:
- **Schema (V2 migration):**
  - Rename `organizations` → `tenants`. Columns: `id`, `name` (unique), `slug` (unique, URL-safe, decision below),
    `status` (`ACTIVE`/`SUSPENDED`), `created_at`, `updated_at`.
  - `users.organization_id` → `users.tenant_id`; `roles.organization_id` → `roles.tenant_id`;
    `applications.organization_id` → `applications.tenant_id`.
  - One root per tenant, enforced in the DB, e.g. `tenants.root_user_id BIGINT UNIQUE` referencing `users`
    (or a generated column + unique index on root users per tenant).
  - Local/dev data is throwaway (no production data exists), so the migration may require empty tables or reset them.
- **Code:**
  - Package `organization/` becomes `tenant/` (`Tenant` entity, `TenantRepository`, `TenantStatus` enum).
  - New `TenantSignupService` + `POST /api/tenants/signup` (public). Body: tenant name (+ slug) and root user details.
    Creates tenant + root user atomically.
  - `POST /api/users/register` loses the `rootUser` flag. It becomes "create a user in my tenant" (authenticated),
    and the new user inherits the creator's tenant. Probably rename it to `POST /api/users`.
  - `UserRegistrationService` splits: root creation moves to `TenantSignupService`.
- **Open decisions (ask first):**
  1. Tenant identifier: slug chosen by the signer, generated from the name, or both?
  2. Username uniqueness: per tenant (`UNIQUE(tenant_id, username)`, the target design) starting now, or in step 6?
  3. Email uniqueness: global (one email = one account anywhere) or per tenant?
  4. Keep `POST /api/users/register` as a path or move to `POST /api/users`?
- **Done when:** a signup creates a tenant + root in one transaction; a second root for the same tenant is impossible;
  tests cover signup rollback on failure.

### Step 6: Tenant isolation (DONE, kept for reference)

Decisions taken: **no hierarchy** inside a tenant (anyone with the permission sees the whole tenant; `createdBy` is audit
only); `AuthenticatedActor` moved to **step 6b**; permissions stay global and untouched until step 7; username uniqueness
moves entirely to step 8 (see there); the local DB was reset.

What was built:
- V3: roles `tenant_id NOT NULL` + `uk_roles_tenant_name (tenant_id, name)`; `idx_<table>_tenant` on every tenant-owned table;
  `fk_users_created_by ON DELETE SET NULL`; `fk_user_roles_role ON DELETE CASCADE`.
- Tenant boundary = tenant-scoped repository lookups (`...AndTenantId`); other tenants' users and roles are 404.
- `GET /api/users` paged (`page`, `size` capped at 100, ordered by id). Roles: CRUD in `RoleService`, permission
  assignment in `RolePermissionService`, request/response records, create → 201, removing role permissions needs `IAM:UPDATE`.
- No self-modification, brought forward from step 7: no own role changes (`UserAccessPolicy`), no changes to a held role
  (`RoleAccessPolicy`). Changing another user's password stays root-only until step 7 turns it into a permission.
- `TenantIsolationTest` proves tenant A's root gets 404 on every tenant B user and role endpoint.

### Step 6b: `AuthenticatedActor` as the security principal (DONE, kept for reference)

Decisions taken: permissions are read from the **database on every check** (changes apply immediately); the unused
`authorities` claim is removed now; a **locked or deactivated** user's existing tokens are rejected (401).

What was built:
- `AuthenticatedActor` record (`userId`, `username`, `tenantId`, `rootUser`) is the only principal; services load the
  `User` entity only when they change it. Policies compare ids; "holds this role" is a query.
- `JwtRequestFilter`: one query (`UserRepository.findActor`) matching username + token version + active + unlocked.
  Malformed, expired or revoked tokens leave the request unauthenticated → 401 (previously 500).
- `AuthorizationService` → `PermissionRepository.isGrantedToUser` (root short-circuits).
- `JwtTokenUtil` slimmed (tokens carry `sub`, `version`, `iat`, `exp`); `DefaultUserDetails` only for login/refresh.
- Tests: `TestActors.actingAs(user)` replaces the per-class token helpers; `JwtAuthenticationTest` covers real tokens
  end to end (valid, malformed, revoked, inactive, locked, expired lock).

### Step 7a: Fixed permission catalog (DONE, kept for reference)

Decisions taken: step 7 split into 7a/7b/7c; **generic keys** `<resource>:<action>` (not one per endpoint), with
`users:reset-password` separate; `/api/auth/authorize` removed (the IAM manages only its own permissions); changing a
password is self-only, resetting is by another user with `users:reset-password` (never self, never root) and emails a
generated password; the forgot-password flow takes a username or email.

What was built: `V4__permission_catalog.sql` (catalog rows, `scope`, `services` dropped); `PermissionKey` enum + catalog
sync test; `@RequirePermission(PermissionKey)`; root holds TENANT keys only; only TENANT permissions attachable to roles;
`NewPasswordIssuer` shared by both resets; OTP expiry bug fixed (days → minutes); Spring MVC errors keep their status.

### Step 7b: Predefined roles (DONE, kept for reference)

Decisions taken: `ADMIN` has every key (including `users:reset-password`); `USER_MANAGER` = `users:read/create/update/delete`;
`VIEWER` = `users:read`, `roles:read`, `permissions:read`; no role-manager role (`ADMIN` covers it); template changes never
reach existing tenants.

What was built: `V5__predefined_roles.sql` (`role_templates`, `role_template_permissions`); immutable `RoleTemplate` entity;
`PredefinedRoleService` (mandatory transaction) called by `TenantSignupService`; tests for the copy, per-tenant independence,
rollback, and "the ADMIN template holds every key".

### Step 7c: User types and direct permissions (DONE, kept for reference)

Decisions taken: the type comes from `userType` in `POST /api/users` (default `REGULAR`) and never changes (create a new
user instead); creating a `MANAGEMENT` user needs the new key `users:create-management` (ADMIN template only); regular
users never hold roles or permissions (400); later, platform users will hold them too.

What was built: `V6__user_types.sql` (`users.user_type`, `user_permissions`, the new key); `UserType`; `UserPermissionService`
with `PUT`/`DELETE /api/users/permissions` (`users:update`, never yourself); permission check = role permissions ∪ direct
permissions; `UserDto` shows `userType` and `permissionIds`.

### Step 8a: Client applications and `client_id` (DONE, kept for reference)

Decisions taken: 8 split into 8a/8b; `client_id` = 24 random URL-safe characters; client secrets later (OAuth2/OIDC);
the console client accepts management users only, application clients accept all users of the tenant; tokens use the
user id as `sub`.

What was built: `V7__clients.sql` (`clients`, one console per tenant, per-tenant username/email uniqueness, `clients:*` keys);
`client/` feature (CRUD, `ClientResolver`); signup returns the console client; `TenantAuthenticationProvider` looks the
username up inside the client's tenant; refresh only through the issuing client; forgot-password tenant-scoped; root
username/email unique across all tenants (application check); lockout listeners by user id.

### Step 8b: Per-client token settings (DONE, kept for reference)

Decisions taken: defaults for every new client 5-minute access tokens, refresh tokens on for 24 hours, the `username` claim
(refresh stays on by default because step 9 reworks refresh tokens anyway); selectable claims `username`, `email`,
`full_name`, `user_type`, `tenant_id` (never roles/permissions); access 1 min–1 h, refresh 1 h–30 days and longer than
access; `clients:update` covers the settings; the console's settings are editable.

What was built: `V8__client_token_settings.sql`; `TokenClaim`; `TokenSettingsDto` with validation; `PUT /api/clients/{id}/token-settings`;
`JwtTokenUtil.generateAccessToken/generateRefreshToken(user, client)`; no refresh token when disabled; hard-coded lifetimes removed.

### Step 9: Token redesign (asymmetric signing, JWKS, refresh-token store, introspection)

**Goal:** other applications verify tokens safely; logout and revocation work.

Decisions taken: one platform key set for now (per-tenant keys are roadmap item 10; `kid` + a tenant-specific `iss`
keep that path open); RS256; without configured keys the app generates a temporary key and warns; logout is authenticated
by the bearer access token; introspection is public until clients get secrets; the default access-token lifetime stays the
5 minutes of step 8b (configurable per client). Split into three commits:

**9a: asymmetric signing + JWKS (DONE)**
- RS256 with configured RSA private keys (`iam.jwt.keys[n].kid` + `private-key` PEM); the first key signs, every key's
  public half is published at `GET /.well-known/jwks.json`. Only the private key can sign; the public key verifies only,
  and the verifier accepts RS256 signatures by known `kid`s only (no `none`, no HMAC with the public key).
- No configured key: a temporary key is generated at startup with a WARN (tokens don't survive a restart).
- Access token: header `typ: at+jwt` + `kid`; claims `iss` = `<iam.jwt.issuer>/tenants/{tenantId}`, `sub`, `aud` and
  `client_id` = the client, `jti`, `iat`, `exp`, the client's claims (`version` stays until 9b).
- Hard-coded HMAC secrets and `JwtConstant` removed; `JwtTokenUtil` split into `TokenIssuer` and `TokenVerifier`.

**9b: sessions, opaque refresh tokens, logout (DONE)**
- `user_sessions` (one per login) and `refresh_tokens` (SHA-256 hash, rotated on every refresh; reusing a used token revokes the
  session). Access tokens carry `sid`; the JWT filter checks the session instead of the `version` claim.
- `version` claim dropped (`@Version` = optimistic locking only); a password reset or deletion revokes the user's sessions,
  an own password change all but the current one; deactivation and lock are refused per request instead of revoking. `POST /api/auth/logout` (bearer) revokes the current session (204).
- Refresh-token errors answer 401 instead of 412.

**9c: introspection (DONE)**
- `POST /api/auth/introspect` `{ token }` (RFC 7662 style): active only if signature and expiry are valid, the session is
  live, the user is active and unlocked, and the tenant is ACTIVE; otherwise just `{ "active": false }`.
- `/api/auth/token/validate` removed.

### Step 10: Platform tenant, super users, tenant suspension (DONE, kept for reference)

**Goal:** the platform level. Design agreed 2026-10-25 (see CLAUDE.md "Platform"): the platform root does everything in
every tenant; other platform users get role/permission grants on one tenant or on all tenants; `X-Tenant-Id` lets a
platform user work inside a tenant through the normal APIs; platform keys `tenants:read/create/suspend/activate`.
Small sub-steps, each touching only a few classes:

| Sub-step | What |
|---|---|
| 10a (DONE) | Suspended tenant: login, refresh and forgot-password refused with 403 (`ClientResolver`) |
| 10b (DONE) | Platform tenant: `tenants.type` (`STANDARD`/`PLATFORM`, one platform); the tenant + console client + roles are created at startup by `PlatformBootstrap` |
| 10c (DONE) | Platform root created at startup from `IAM_PLATFORM_ROOT_USERNAME/PASSWORD/EMAIL` (WARN when missing) |
| 10d (DONE) | `tenants:*` PLATFORM keys; `GET /api/platform/tenants` (list, view) for platform users only |
| 10e (DONE) | Suspend / activate a tenant (never the platform tenant); sessions kept |
| 10f (DONE) | Create a tenant with its root as a super user |
| 10g (DONE) | `X-Tenant-Id`: the platform root works inside any tenant through the normal APIs; audit records platform user + tenant |
| 10h (DONE) | `platform_role_grants` / `platform_permission_grants` (tenant or all, NULL = all standard tenants); with `X-Tenant-Id` a platform user holds the TENANT keys its grants give on that tenant |
| 10i (DONE) | Platform roles may hold `tenants:*` keys (platform tenant only); PLATFORM keys count only through grants |
| 10i+ (DONE) | The platform tenant has management users only (no regular users) and no application clients |
| 10j (DONE) | Platform endpoints check per tenant (list/view only granted tenants, suspend/activate the path tenant, create needs an all-tenants grant) |
| 10k (DONE) | Role grants API (only to platform management users, only platform roles, no self-grants; `users:update` in the platform tenant) |
| 10l (DONE) | Permission grants API (`/api/platform/grants/permissions`) |
| 10m (DONE) | Remove the unused `is_super_user` column |

**Done when:** a suspended tenant's users cannot log in, refresh or pass introspection; tenant users can never call
platform endpoints or act in another tenant; platform users act in a tenant only with a grant (or as platform root).

### Step 11: Client status (DONE)

`clients.status`, `clients:suspend` / `clients:activate` (tenant and platform users); a suspended client refuses login and
refresh, and its sessions are refused by the filter and introspection.

### Security and quality fixes (slot in when the owner chooses; some are absorbed by steps above)

| Item | Where it is handled |
|---|---|
| Public registration lets anyone become root with global power | Done: step 5 (signup creates an own tenant), step 6 (tenant isolation) |
| Hard-coded HMAC JWT secrets | Done in step 9a |
| JWT filter errors → 500 instead of 401 | Done in step 6b |
| `/token/validate` ignores revocation | Done in step 9c (replaced by introspection) |
| Password reset: emails a plaintext password; `java.util.Random` OTP, stored in plain text; no attempt limit; reveals whether an email exists | Own step after 8 (reset must be tenant/client-aware). Proposed: secure random OTP, hashed, max attempts, uniform response, user sets the new password |
| `mail.debug=true` (done: security fix 1, `MailConfig` removed); DB credentials in `application.properties`; no JWT key configured only warns | Small config step (profiles + env); outside dev/test, fail at startup without a configured JWT key |
| Error responses: many one-off exceptions, 412 for bad tokens, no `ProblemDetail` | Own step: base `IamException` + RFC 9457 `ProblemDetail` |
| `RoleMapper.toEntity` drops `permissionIds`; `RoleService` uses `Mappers.getMapper` | Done in step 6 |
| Merge all migrations into one baseline from the live schema | After the last step of this plan |
| Deleting a permission assigned to a role fails (FK) | Done in step 7a (permissions are read-only) |
| Forgot-password hardening: OTP attempt limit (done: security fix 5; 3 OTPs per user per hour: fix 5b), hashed OTP (done: security fix 6, bcrypt), uniform response (no account enumeration; done: security fix 4, except the timing difference); emailed plaintext password (done: security fix 7, users choose their password with an OTP; nothing emails a password) | Done |
| Deleting a user with a pending reset OTP fails (FK) | Done: security fix 2 (`V15`, ON DELETE CASCADE) |
| A root user can delete itself, leaving its tenant without a root | Done: security fix 3 (nobody deletes a root user) |
| `README.md` out of date | After step 5, and kept current afterwards |

---

### Step 12: Unlock, deactivate and activate users (planned, owner asked 2026-10-25)
Today a locked user (5 wrong passwords) waits 15 minutes, and nobody can deactivate or reactivate a user: `active` is set at
creation and no API changes it.
- `PUT /api/users/{id}/unlock`: ends a login lockout at once (clears `user_locked`, `account_locked_until`,
  `failed_login_attempts`).
- `PUT /api/users/{id}/deactivate` / `activate`: a deactivated user can't log in or refresh, and the JWT filter and
  introspection already refuse their tokens (`findActor` checks `active`). Decide then whether deactivation also revokes
  sessions.
- Permissions to decide then: new keys (e.g. `users:unlock`, `users:deactivate`) or `users:update`. Rules as for other user
  changes: tenant-scoped (other tenants 404), never yourself, never the root user.

### Step 13: Email service: asynchronous sending with templates (planned, owner asked 2026-10-25)
Today each feature builds a `SimpleMailMessage` and sends it inside the request and its transaction
(`ResetOtpIssuer`). A slow SMTP server slows the response, holds database locks longer, and
makes a known user's reset request measurably slower than an unknown one's. More emails will come (verification,
invitations, MFA codes, notifications), so:
- One owner for email: an email service that every feature calls with a purpose and its values; nobody else touches
  `JavaMailSender`.
- Common templates, one per purpose (reset OTP, later verification, invitation, ...), with shared layout and
  wording; per-tenant branding later.
- Asynchronous: sent after the transaction commits (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async` on its own
  executor), so requests never wait for SMTP and roll-backs never send. Failures are logged (ERROR) and maybe retried;
  decide then whether an outbox table is needed for guaranteed delivery.
- The reset request's `MAIL_FAILED` outcome can't be known during the request any more; decide then how to audit it.
- Removes the reset-request timing difference (Known issues in `CLAUDE.md`).

### Step 14: Reset link in admin-initiated reset emails (planned with the frontend, owner asked 2026-10-26)
The two resets reach the user differently:
- **Forgot-password (self-service):** the user starts on the frontend, so the email needs only the OTP; the frontend moves
  from the request screen to the OTP screen and then the new-password screen.
- **Reset by a management user:** the user didn't start anything and doesn't know where to go, so the email must also
  carry a **link to the reset page**.

To decide when the frontend exists:
- Where the reset page URL comes from: a per-client setting (e.g. `password_reset_url`, like Auth0's application login
  URL), a tenant setting, or the hosted IAM page; and which client an admin reset uses (the admin picks one, or the
  tenant's default).
- What the link carries: only `client_id` + identifier with the OTP typed by hand, or a long single-use reset token in
  the link instead of the six-digit OTP (no typing; never put the short OTP itself in a URL, where browser history,
  proxies and logs keep it).
- A separate email template for this purpose (step 13).

### Step 15: Allowed web origins per client (planned, owner asked 2026-10-26)
Today the browser origins allowed to call the API are one platform-wide list (`iam.cors.allowed-origins`), set by whoever
runs the IAM. That covers our own console and hosted pages, but a tenant's own single-page app on its own domain (calling
login, refresh or forgot-password directly) needs the platform operator to add its origin. Like Auth0's "Allowed Web
Origins", each client gets its own list, managed by the tenant:
- `clients.allowed_web_origins` (or a `client_allowed_origins` table), edited with the client (`clients:update`), exact
  origins only, validated like `CorsProperties`.
- A `CorsConfigurationSource` that allows the configured platform origins everywhere, plus a client's origins on the
  public endpoints that carry its `client_id` (and for tokens of that client). To decide then: a preflight carries no
  body, so the `client_id` can't be read from it; options are allowing the union of all clients' origins on the public
  auth endpoints (cached) or moving `client_id` into the path or a header.
- Cache the origin lookup (every preflight would otherwise hit the database).
- Frontend: an "Allowed web origins" field on the application detail page.

## 5. After the target design: the feature roadmap (`IAM_ROADMAP.md`)

Each item builds on a seam created above:

| Roadmap item | Builds on |
|---|---|
| MFA | Login flow in `AuthenticationService`: password ok → MFA challenge → tokens; per-client or per-tenant MFA policy |
| SSO / OIDC compatibility | `client_id` model + asymmetric tokens (step 9); consider Spring Authorization Server |
| User groups | `AuthorizationService` permission resolution (user ∪ roles ∪ group roles) |
| SCIM provisioning | Stable tenants, users and groups |
| ABAC / fine-grained access | `AuthorizationService` gains attribute inputs |
| Session management | Refresh-token/session table from step 9 |
| Identity federation | Clients + OIDC |
| API keys | A new authentication path producing an `AuthenticatedActor`; permission checks unchanged |
| Break-glass access | Platform permissions + audited, time-limited override |
| Maker-checker for role changes | Optional per tenant, switched on by root (agreed future option) |
