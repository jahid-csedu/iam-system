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
| Docs | This plan; user manual (`docs/USER_MANUAL.md`) | `d188c69`, step 5 commit |
| 5 | Tenants: `V2__tenants.sql`, `POST /api/tenants/signup` (tenant + root atomically, one root per tenant), `POST /api/users` | step 5 commit |

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

### Step 6: Tenant isolation

**Goal:** no user can see or change anything outside their tenant. This closes the "any root can edit every role" hole.

Proposed design:
- `UserAccessPolicy`: the same-tenant rule replaces the `createdBy` hierarchy. Decision below on whether management users
  keep any hierarchy inside a tenant. `createdBy` stays for audit only.
- Every tenant-owned query is tenant-scoped at the repository level (`findAllByTenantId`, `findByTenantIdAndUsername`, …).
  `findAllUsers` becomes a paginated, tenant-scoped query (fixes the load-everything issue).
- Roles become tenant-owned (`roles.tenant_id NOT NULL`); role CRUD and role assignment only within the tenant.
- Introduce an `AuthenticatedActor` record (`userId`, `username`, `tenantId`, flags) as the security principal instead of the
  JPA entity. `CurrentUserProvider` returns it, which removes the entity-reload-per-request smell. (This may become step 6b if large.)
- Decide the FK behaviour for deleting a user who created others (`created_by` → `ON DELETE SET NULL` is the likely answer).
- **Open decisions:** hierarchy inside a tenant (yes/no); whether `AuthenticatedActor` is part of step 6 or its own step.
- **Done when:** integration tests prove that tenant A's root/management users get 404/403 on every tenant B resource.

### Step 7: Fixed permission catalog, user types, predefined roles

**Goal:** the permission model from the target design.

Proposed design:
- **Schema:**
  - `permissions` redesigned: `id`, `key` (unique, e.g. `users:create`, `roles:write`, `token-settings:update`,
    `tenants:suspend`), `scope` (`PLATFORM`/`TENANT`), `description`. Rows come **only** from migrations.
    The `service_name`, `action`, `service_id` and `application_id` columns, the `services` table and the
    `PermissionAction` enum go away.
  - `users.user_type` (`MANAGEMENT`/`REGULAR`).
  - `user_permissions` (direct grants to management users) next to `user_roles`.
  - Predefined roles: a template (migration-seeded, e.g. `role_templates` + `role_template_permissions`) copied into each new
    tenant on signup. Root has every tenant permission implicitly, not through a role.
- **Code:**
  - `@RequirePermission("users:create")` (a permission key, not service + action). `AuthorizationService` checks
    keys: root → all TENANT-scope keys; otherwise union of role permissions + direct permissions.
  - Permission endpoints become read-only (list the catalog). Role endpoints manage roles built from the catalog.
  - Rules: **no self-modification** (a user can't change their own roles/permissions or edit a role they hold);
    a grantor may grant permissions they don't hold, but never to themselves; changes apply immediately.
  - Regular users can never hold management permissions or use management endpoints.
- **Open decisions:** the initial permission key list and naming convention; the predefined role set
  (e.g. Admin, User Manager, Role Manager, Viewer).
- **Done when:** the catalog is migration-only; every rule above has a test.

### Step 8: Client applications (`client_id`)

**Goal:** every login goes through a registered client; token settings per client.

Proposed design:
- **Schema:** `applications` evolves into `clients`: `id`, `tenant_id`, `client_id` (public, unique, random),
  `name`, `type` (`MANAGEMENT_CONSOLE`/`APPLICATION`), `access_token_ttl_seconds`, `refresh_token_enabled`,
  `refresh_token_ttl_seconds`, plus a `client_token_claims` table (or JSON column) listing which user claims go into tokens.
- Tenant signup auto-creates the tenant's **management-console client**, which only accepts management users.
- Login request: `client_id` + username + password. The client resolves the tenant; the username is looked up inside it.
- Switch usernames and emails to per-tenant uniqueness (`UNIQUE(tenant_id, username)`, `UNIQUE(tenant_id, email)`), as decided in
  step 5. Password reset and every lookup by username/email become tenant-aware.
- Management endpoints for clients (CRUD, token settings), protected by fixed permissions such as `clients:write`.
- **Open decisions:** client secrets now or later (needed for OAuth2 confidential clients); the default token settings for new clients.
- **Done when:** login without a valid `client_id` fails; the console client rejects regular users; TTL/refresh/claims follow the client.

### Step 9: Token redesign (asymmetric signing, JWKS, refresh-token store, introspection)

**Goal:** other applications verify tokens safely; logout and revocation work.

Proposed design:
- Sign with RS256 (or ES256). Keys come from configuration/keystore with a `kid`; publish `GET /.well-known/jwks.json`.
  Remove the hard-coded HMAC secrets in `JwtConstant`.
- Claims: `iss` (identifies the tenant), `sub` (user id), `aud`/`azp` (client), plus the per-client user claims.
  Drop the unused `authorities` claim.
- Refresh tokens: random opaque values stored **hashed** in a `refresh_tokens`/`sessions` table (user, client, expiry,
  revoked), rotated on use. `POST /api/auth/logout` revokes them. This replaces the `@Version`-based revocation.
- `POST /api/auth/introspect` (RFC 7662 style): active only if the token is valid, the user is active, and the tenant is ACTIVE.
- Bad or expired tokens in the JWT filter answer **401** (via the authentication entry point), not 500.
- Rework `/token/validate` (fold into introspection) and the `/authorize` endpoint (decide whether it stays).
- **Open decisions:** key per tenant or one platform key; access-token default TTL; whether `/authorize` survives.

### Step 10: Platform tenant, super users, tenant suspension

**Goal:** the platform level.

Proposed design:
- A reserved `platform` tenant created by migration. The first super user is bootstrapped from config
  (env vars) at startup if none exists; there is no public signup into it. Its own management-console client.
- PLATFORM-scope permissions (`tenants:read`, `tenants:suspend`, `tenants:activate`, …) usable only by platform users.
- `/api/platform/tenants` endpoints: list, view, suspend, activate.
- Suspension blocks login, refresh and introspection for every user of the tenant.
- Remove the unused `is_super_user` column.
- **Done when:** a suspended tenant's users cannot log in, refresh or pass introspection; tenant users can never call platform endpoints.

### Security and quality fixes (slot in when the owner chooses; some are absorbed by steps above)

| Item | Where it is handled |
|---|---|
| Public registration lets anyone become root with global power | Step 5 done (signup creates an own tenant); step 6 removes the global power |
| Hard-coded HMAC JWT secrets | Step 9 |
| JWT filter errors → 500 instead of 401 | Step 9 (or earlier as a small fix) |
| `/token/validate` ignores revocation | Step 9 |
| Password reset: emails a plaintext password; `java.util.Random` OTP, stored in plain text; no attempt limit; reveals whether an email exists | Own step after 8 (reset must be tenant/client-aware). Proposed: secure random OTP, hashed, max attempts, uniform response, user sets the new password |
| `mail.debug=true`, `logging.level.root=DEBUG`, DB credentials in `application.properties` | Small config step (profiles + env) |
| Error responses: many one-off exceptions, 412 for bad tokens, no `ProblemDetail` | Own step: base `IamException` + RFC 9457 `ProblemDetail` |
| `RoleMapper.toEntity` drops `permissionIds`; `RoleService` uses `Mappers.getMapper` | Step 7 (roles are reworked) |
| Deleting a permission assigned to a role fails (FK) | Step 7 (permissions become read-only) |
| `README.md` out of date | After step 5, and kept current afterwards |

---

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
