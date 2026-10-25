# IAM System: User Manual

How to use the system as a developer through Swagger UI or Postman.

> **Matches the code as of step 10d (platform: list and view tenants).** This manual is updated in every step that changes how the system is
> used. When something here is temporary, it says which step changes it (see `docs/IMPLEMENTATION_PLAN.md`).

---

## 1. Concepts (as they work today)

| Concept | Today |
|---|---|
| **Tenant** | One organization. Created by **tenant signup**, which also creates the tenant's **root user** and its **management console** client. |
| **Client** | An application of a tenant that users log in through, identified by a public 24-character **`client_id`**. Every login, token refresh and forgot-password request names its client, and the client decides which tenant the user belongs to. |
| **Management console** | The one built-in client of each tenant (`type: MANAGEMENT_CONSOLE`): only management users log in through it, and it can't be deleted. |
| **Application client** | A client the tenant registers (`type: APPLICATION`): all users of the tenant can log in through it. |
| **Root user** | Exactly one per tenant. Holds every tenant permission implicitly (no role needed). |
| **Permission** | A fixed key `<resource>:<action>`, such as `users:read`. The catalog is part of the system: nobody creates, changes or deletes permissions. New keys arrive with new features. |
| **Role** | A named set of permissions, owned by one tenant (names are unique within the tenant). |
| **User type** | Chosen when the user is created and never changed (create a new user instead). |
| **Management user** | `userType: MANAGEMENT`. Uses the management API through roles and/or permissions granted directly. The root user is a management user. |
| **Regular user** | `userType: REGULAR` (the default). Logs in, refreshes tokens and manages their own password; never holds roles or permissions. |
| **Tenant isolation** | Every user and role belongs to one tenant. With the right permission you see and manage your **whole tenant**, and nothing else: users and roles of other tenants answer `404`. |
| **No self-modification** | Nobody can change their own roles, change or delete a role they hold, or reset their own password through the management API. Another user with the permission must do it. |

**Important limitations today:**
- **Introspection is public** (clients have no secrets yet): anyone holding a token can ask about it, and the answer only
  reveals that token's own claims.

### The permission catalog

| Key | Allows |
|---|---|
| `users:read` | List users, and find them by id, username or email |
| `users:create` | Create regular users in your tenant |
| `users:create-management` | Also create management users (needed on top of `users:create`) |
| `users:update` | Assign and remove the roles and direct permissions of other management users |
| `users:delete` | Delete users |
| `users:reset-password` | Reset another user's password (the new password is emailed to that user) |
| `roles:read` | List roles, and find them by id or name |
| `roles:create` | Create roles |
| `roles:update` | Rename roles, change their description, add and remove their permissions |
| `roles:delete` | Delete roles |
| `clients:read` | List clients and view a client |
| `clients:create` | Register application clients |
| `clients:update` | Rename clients and change their token settings |
| `clients:delete` | Delete application clients (never the management console) |
| `permissions:read` | List the catalog and view a permission |

PLATFORM keys, for super users only (never part of a tenant's roles; the platform root holds them all):

| Key | Allows |
|---|---|
| `tenants:read` | List and view all tenants (`/api/platform/tenants`) |
| `tenants:create` | Create tenants with their root user (endpoint arrives in step 10f) |
| `tenants:suspend` | Suspend a tenant (endpoint arrives in step 10e) |
| `tenants:activate` | Reactivate a suspended tenant (endpoint arrives in step 10e) |

### Predefined roles

Every new tenant gets its own copies of these roles at signup. They are ordinary roles of the tenant: assign them, change
them, delete them, or create more. Later changes to the predefined set never affect existing tenants.

| Role | Permissions |
|---|---|
| `ADMIN` | every key in the catalog |
| `USER_MANAGER` | `users:read`, `users:create`, `users:update`, `users:delete` |
| `VIEWER` | `users:read`, `roles:read`, `clients:read`, `permissions:read` |

---

## 2. Start the system

| How | Command | URL |
|---|---|---|
| Docker (app + MySQL) | `docker compose up --build app db` | `http://localhost:8080` |
| Docker (with ELK logging) | `docker compose up --build` | app `:8080`, Kibana `:5601` |
| Local JVM (needs MySQL on `localhost:3366`, e.g. `docker compose up db`) | `./gradlew bootRun` | `http://localhost:8080` (override with `SERVER_PORT=8081` in `.env`) |

The database schema and the permission catalog are created automatically (Flyway) on first start, and so is the
**Platform** tenant of the super users (its name is therefore taken).

**First super user.** Set `IAM_PLATFORM_ROOT_USERNAME`, `IAM_PLATFORM_ROOT_PASSWORD` (password policy applies) and
`IAM_PLATFORM_ROOT_EMAIL` (optionally `IAM_PLATFORM_ROOT_FULL_NAME`) in `.env`; `docker compose` passes them to the app.
At startup, while the platform has no root user, this user becomes its root (a WARN is logged when they're missing). It
logs in like any root, through the Platform tenant's management console: its `client_id` is logged once, when the
Platform tenant is created ("Created the platform tenant ... (client_id ...)").
The variables are read only then: changing them later changes nothing.

Mail settings (`SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` in `.env`) are needed for password resets: OTPs and new passwords are only ever
sent by email. Each development step starts from a fresh database: `docker compose down -v` removes the old one.

**Token signing key.** Tokens are signed with an RSA private key that only the IAM holds. Configure one with
`IAM_JWT_KEYS_0_KID` (any id, e.g. `key-2026-10`) and `IAM_JWT_KEYS_0_PRIVATE_KEY` (a PKCS#8 RSA key of at least 2048 bits,
base64 on one line; PEM header lines are optional):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-key.pem
grep -v -- ----- jwt-key.pem | tr -d '\n'     # the value for IAM_JWT_KEYS_0_PRIVATE_KEY
```

Put both in `.env` for `./gradlew bootRun`, or pass them to the `app` container (`-e` / `environment:`). Without a key the
app generates a temporary one at startup (a WARN in the log) and every token becomes invalid when it restarts.
**Rotating:** add the new key as `IAM_JWT_KEYS_0_*` and move the old one to `IAM_JWT_KEYS_1_*`; the first key signs, both
are published, so tokens signed by the old key keep working. Remove the old key once the access tokens it signed have expired (refresh tokens aren't signed).
`IAM_JWT_ISSUER` (default `http://localhost:8080`) is the public base URL put into every token's `iss`.

---

## 3. Using Swagger UI

1. Open **`http://localhost:8080/swagger-ui.html`**.
2. Call `POST /api/tenants/signup` and note `managementConsole.client_id` from the response. Call `POST /api/auth/authenticate`
   with that `client_id`, and copy the `access_token` from the response.
3. Click **Authorize** (top right) and paste the token **without** the `Bearer ` prefix. Swagger adds it.
4. All protected endpoints now send the token. Access tokens expire after **5 minutes**: log in again (or refresh) and re-authorize.

## 4. Using Postman

1. Create an environment with `baseUrl = http://localhost:8080`, `clientId`, `accessToken`, `refreshToken`.
2. In the login request's **Scripts → Post-response** tab, store the tokens automatically:
   ```js
   const body = pm.response.json();
   pm.environment.set("accessToken", body.access_token);
   pm.environment.set("refreshToken", body.refresh_token);
   ```
3. On protected requests: **Authorization → Bearer Token → `{{accessToken}}`**. Send JSON bodies as **raw → JSON**.

---

## 5. Walkthrough: from an empty system to working users

All bodies are JSON. Passwords must be **at least 8 characters with an uppercase letter, a lowercase letter, a digit and a special
character** (`@#!$%^&-+=()`).

### Step 1: Sign up a tenant (public)
`POST /api/tenants/signup`
```json
{
  "tenantName": "Globex",
  "rootUser": {
    "username": "globex-root",
    "password": "Root1234!",
    "fullName": "Globex Root",
    "email": "root@globex.com"
  }
}
```
→ `201 Created` with the tenant, its root user and its management console:
```json
{ "tenant": { ... }, "rootUser": { ... },
  "managementConsole": { "id": 1, "client_id": "pipHcOj4eVaEOqgMCeTJygKC", "name": "Management Console", "type": "MANAGEMENT_CONSOLE" } }
```
Keep the console's `client_id`: management users log in with it. A taken tenant name gives `400`, and so does a root username
or email that any user of any tenant already uses.

### Step 2: Log in as root through the management console (public)
`POST /api/auth/authenticate`
```json
{ "client_id": "pipHcOj4eVaEOqgMCeTJygKC", "username": "globex-root", "password": "Root1234!" }
```
→ `200` with `access_token` (valid 5 minutes) and `refresh_token` (valid 24 hours, single use). Use the access token as `Bearer` from now on.
An unknown `client_id`, or a username that doesn't exist in the client's tenant, gives `401`.

### Step 3: Look at your roles (root)
`GET /api/roles` → the predefined `ADMIN`, `USER_MANAGER` and `VIEWER` roles of your tenant, each with its `id` and
`permissionIds`. `GET /api/permissions` shows which key each permission id is.

### Step 4 (optional): Create your own role (root)
`POST /api/roles`
```json
{ "name": "AUDITOR", "description": "Views users" }
```
→ `201` with the role's `id`. The role belongs to your tenant; another tenant may use the same name. A name already used in your
tenant gives `400`. Then attach permissions by id (from `GET /api/permissions`):

`PUT /api/roles/permissions`
```json
{ "roleId": 4, "permissionIds": [1] }
```
→ `204`. An unknown permission id gives `404`. (`DELETE /api/roles/permissions` with the same body removes them.)

### Step 5: Create a management user (needs `users:create` and `users:create-management`)
`POST /api/users`
```json
{ "username": "bob", "password": "Bob12345!", "fullName": "Bob Manager", "email": "bob@globex.com", "userType": "MANAGEMENT" }
```
→ `201` with `"userType": "MANAGEMENT"`. The user joins the caller's tenant, and `createdBy` is the caller.
Without `userType` the user is `REGULAR`; creating a `MANAGEMENT` user without `users:create-management` gives `403`.

### Step 6: Give the user a role (needs `users:update`)
`PUT /api/users/roles`
```json
{ "username": "bob", "roleIds": [2] }
```
(using the id of `USER_MANAGER`) → `204`. Bob can now see, create (regular users only), update and delete the users of the tenant.
Roles and permissions can only be given to management users: for a regular user the request gives `400`.
Bob can't change his own roles, or change the `USER_MANAGER` role while he holds it (`403`).
(`DELETE /api/users/roles` with the same body removes roles.) Role and permission changes apply to the user's next request,
without new tokens: permissions are read from the database every time, never from the token.

### Step 7: Register an application and create a regular user
Regular users can't log in through the management console (`403`), so register the application they use (needs `clients:create`):

`POST /api/clients`
```json
{ "name": "Shop" }
```
→ `201` with the new client's generated `client_id`. Then log in as Bob and `POST /api/users` again (e.g. `carol`) without
`userType`. Carol is a regular user: she logs in through the Shop client, refreshes her tokens and changes her own password,
and gets `403` on every management endpoint.

### Step 8 (optional): Grant a single permission directly (needs `users:update`)
`PUT /api/users/permissions`
```json
{ "username": "bob", "permissionIds": [6] }
```
(the id of `roles:read`) → `204`. Bob's permissions are now his roles' permissions plus the direct ones.
`DELETE /api/users/permissions` with the same body takes them away. Nobody can change their own permissions (`403`).

---

## 6. Everyday operations

| What | Request | Who |
|---|---|---|
| Change own password | `PATCH /api/users/password` `{ "oldPassword": "...", "newPassword": "..." }` | Any logged-in user, for themselves only |
| Reset another user's password | `POST /api/users/{id}/password-reset` (no body) → `204`; a new password is emailed to that user | `users:reset-password`; never your own password, never the root user's |
| Forgot password: request an OTP | `POST /api/password/reset-request` `{ "client_id": "...", "identifier": "<username or email>" }` → the OTP is emailed | Public |
| Forgot password: reset with the OTP | `POST /api/password/reset` `{ "client_id": "...", "identifier": "<username or email>", "otp": "123456" }` → a new password is emailed | Public |
| Refresh tokens | `POST /api/auth/token/refresh` `{ "client_id": "...", "refresh_token": "..." }` (the client the token was issued through) → a new access token **and a new refresh token**; the old refresh token is used up | Public (needs a valid refresh token) |
| Log out | `POST /api/auth/logout` (no body) → `204`; ends the session of the Bearer token: its access token gets `401` here and its refresh token stops working | Any logged-in user |
| Client token settings | `PUT /api/clients/{id}/token-settings` (see below); applies to tokens issued from then on | `clients:update` |
| Clients | `GET /api/clients`, `GET /api/clients/{id}`, `POST /api/clients` `{ "name" }`, `PUT /api/clients/{id}` `{ "name" }` (rename), `DELETE /api/clients/{id}` (never the console) | `clients:read` / `create` / `update` / `delete` |
| List users | `GET /api/users?page=0&size=20` → `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`, ordered by id; `size` is capped at 100 | `users:read` |
| Direct permissions | `PUT` / `DELETE /api/users/permissions` `{ "username", "permissionIds" }` (management users only, never yourself) | `users:update` |
| Find a user | `GET /api/users/{id}`, `/api/users/by-username?username=`, `/api/users/by-email?email=` | `users:read` |
| Delete a user | `DELETE /api/users/{id}` (users they created stay, without a creator) | `users:delete` (a root user can only delete itself) |
| Roles | `GET /api/roles`, `GET /api/roles/{id}`, `GET /api/roles/name/{name}` | `roles:read` |
| Change roles | `POST /api/roles`, `PUT /api/roles/{id}` `{ "name", "description" }`, `DELETE /api/roles/{id}` (also removes it from its users) | `roles:create` / `roles:update` / `roles:delete`, never on a role you hold |
| Role permissions | `PUT /api/roles/permissions` / `DELETE /api/roles/permissions` `{ "roleId", "permissionIds" }` | `roles:update`, never on a role you hold |
| Permission catalog | `GET /api/permissions`, `GET /api/permissions/{id}` (read-only) | `permissions:read` |

### Token settings per client

Every client decides how its tokens look. New clients (including the management console) start with the defaults:

```json
{ "accessTokenTtlSeconds": 300, "refreshTokenEnabled": true, "refreshTokenTtlSeconds": 86400, "claims": ["USERNAME"] }
```

Change them with `PUT /api/clients/{id}/token-settings` and the same body:

| Field | Allowed |
|---|---|
| `accessTokenTtlSeconds` | 60 to 3600 (1 minute to 1 hour) |
| `refreshTokenEnabled` | `false`: login returns no `refresh_token`, and refreshing through this client gives `400` |
| `refreshTokenTtlSeconds` | 3600 to 2592000 (1 hour to 30 days), always longer than the access token |
| `claims` | Any of `USERNAME`, `EMAIL`, `FULL_NAME`, `USER_TYPE`, `TENANT_ID` (an empty list for none). Roles and permissions are never put into tokens. |

Every access token also carries `iss`, `sub` (the user id), `aud` and `client_id` (the client), `sid` (the login session),
`jti`, `iat` and `exp`. Refresh tokens are random opaque strings, not JWTs.
A selected claim the user has no value for (for example no email) is left out.

### Platform administration (super users)

Log in as the platform root through the Platform tenant's console `client_id`, then:

| What | Request | Who |
|---|---|---|
| List all tenants | `GET /api/platform/tenants?page=0&size=20` → paged like `GET /api/users`, each `{ "id", "name", "type": "PLATFORM"/"STANDARD", "status" }` | `tenants:read` |
| View a tenant | `GET /api/platform/tenants/{id}` (`404` if unknown) | `tenants:read` |

Tenant users (their root included) get `403` on every `/api/platform/**` endpoint.

## 7. For other applications (verifying users)

| What | Request | Notes |
|---|---|---|
| Public keys for verifying tokens | `GET /.well-known/jwks.json` → `{ "keys": [ { "kty": "RSA", "use": "sig", "alg": "RS256", "kid": "...", "n": "...", "e": "AQAB" } ] }` | Public. Cache it; fetch again when a token names an unknown `kid`. |
| Is this access token active right now? | `POST /api/auth/introspect` `{ "token": "..." }` → `200` `{ "active": true, "sub", "iss", "aud", "client_id", "sid", "jti", "iat", "exp", "token_type": "Bearer", ...the client's claims }`, or just `{ "active": false }` | Public. Active only while the signature and expiry are valid, the session hasn't ended (logout, password change or reset), the user is active and unlocked, and the tenant is active. An inactive answer never says why. |

**Verifying an access token locally** (any JWT library):
1. The header must say `"alg": "RS256"` and `"typ": "at+jwt"`. Accept nothing else: never `none`, never an `HS*` algorithm
   (that would let anyone sign tokens with the public key).
2. Pick the JWKS key whose `kid` matches the header, and verify the signature with it.
3. Check `exp`, that `iss` is `<IAM base URL>/tenants/<your tenant id>`, and that `aud` contains your `client_id`.

The public key can only verify tokens; only the IAM's private key can sign them, so a changed token fails verification.
A locally verified token stays valid until it expires even if the user logs out or is locked meanwhile. Applications that
must notice that sooner call introspection instead (a database lookup per call), or keep access tokens short.

The IAM manages only its own permissions; other applications decide their own authorization from the verified user.

---

## 8. Rules worth knowing

- **Token lifetimes and claims** come from the client the user logged in through (defaults: 5 minutes / 24 hours / `username`).
  Tokens already issued keep their lifetime when the settings change.
- **Lockout:** 5 failed logins lock the account for 15 minutes. While locked, the user's existing tokens are rejected too.
- **Sessions:** every login starts a session. It lasts as long as its refresh token (each refresh moves the end forward by the
  client's refresh lifetime), or, for a client without refresh tokens, as long as its access token.
- **Refresh tokens are single use.** Each refresh returns a new one. Presenting a used refresh token again (a sign it was
  copied) ends the whole session, including the tokens issued after it.
- **Revocation:** logout ends one session; changing your own password ends your other sessions (the current one stays);
  a password reset (by another user or forgot-password) ends all of the user's sessions; deleting a user or a client ends
  its sessions. A deactivated or locked user's tokens are refused while that lasts. Roles and permissions are checked
  against the database on every request, so their changes apply immediately without ending sessions.
- **Password expiry:** passwords expire after 90 days; an expired password blocks login.
- **Password resets** (forgot-password and reset by another user) generate a new password and send it **only by email**; it never
  appears in a response. A forgot-password OTP is valid for 5 minutes and replaces any earlier OTP of the user.
- **One root per tenant**, created only by signup; a root user can't be deleted, or have its password reset, by anyone else.
- **Tenant isolation:** users and roles of another tenant are reported as not found (`404`), never as forbidden.
- **Usernames and emails** are unique per tenant: two tenants may both have a `bob`. Exception: a root user's username and
  email are unique across all users of all tenants. Login and forgot-password look the user up only inside the tenant of
  the request's `client_id`.
- **Access tokens** are RS256-signed JWTs. They carry the tenant as `iss`, the user id as `sub`, the client they were issued
  through as `aud` and `client_id`, the session as `sid`, and the client's selected claims. A refresh token only works through the client it was issued through.

## 9. Status codes

| Code | Meaning here |
|---|---|
| `200` / `201` / `204` | OK / created / done without a body |
| `400` | Already exists (tenant, user, role or client name in your tenant), deleting the management console, refreshing through a client without refresh tokens, an unknown token claim, roles or permissions for a regular user, bad password policy, wrong old password, invalid or expired OTP, negative page or page size below 1, missing or malformed request body |
| `401` | Not logged in: missing, malformed, expired or revoked access token, a locked or deactivated account, a suspended tenant, wrong username/password, an unknown `client_id`, or a refresh token that is unknown, expired, already used, logged out or of another client |
| `403` | Logged in but missing the required permission (including `users:create-management` for a management user); changing your own roles or permissions, or a role you hold; resetting your own or the root user's password; a regular user logging in through the management console; login, refresh or forgot-password for a suspended tenant ("Tenant is suspended"); or account locked, deactivated or expired |
| `404` | Not found, including users and roles of other tenants, unknown permissions, and an unknown username/email on forgot-password |
| `405` | The HTTP method isn't supported (for example creating a permission) |
| `422` | Request body failed validation (the `errors` list shows which field), including token lifetimes out of range |
