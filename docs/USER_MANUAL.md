# IAM System: User Manual

How to use the system as a developer through Swagger UI or Postman.

> **Matches the code as of step 11 (client status).** This manual is updated in every step that changes how the system is
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
| `users:reset-password` | Reset another user's password (that user gets a reset OTP by email and chooses a new password) |
| `roles:read` | List roles, and find them by id or name |
| `roles:create` | Create roles |
| `roles:update` | Rename roles, change their description, add and remove their permissions |
| `roles:delete` | Delete roles |
| `clients:read` | List clients and view a client |
| `clients:create` | Register application clients |
| `clients:update` | Rename clients and change their token settings |
| `clients:delete` | Delete application clients (never the management console) |
| `clients:suspend` | Suspend application clients (never the management console) |
| `clients:activate` | Reactivate suspended clients |
| `permissions:read` | List the catalog and view a permission |

PLATFORM keys, for super users only. The platform root holds them all, on every tenant; other platform users hold them
on the tenants their grants cover (a grant on all tenants covers every tenant). Only the Platform tenant's roles can hold them
(a standard tenant gets `404` "Some permissions not found"), and even there they take effect only when the role is
**granted** on tenants, never through a normal role assignment:

| Key | Allows |
|---|---|
| `tenants:read` | List and view all tenants (`/api/platform/tenants`) |
| `tenants:create` | Create tenants with their root user |
| `tenants:suspend` | Suspend a tenant (never the platform tenant) |
| `tenants:activate` | Reactivate a suspended tenant |

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

Mail settings (`SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` in `.env`) are needed for password resets: OTPs are only ever
sent by email, and no password is ever emailed. Each development step starts from a fresh database: `docker compose down -v` removes the old one.

**Token signing key.** Tokens are signed with an RSA private key that only the IAM holds. Configure one with
`IAM_JWT_KEYS_0_KID` (any id, e.g. `key-2026-10`) and `IAM_JWT_KEYS_0_PRIVATEKEY` (a PKCS#8 RSA key of at least 2048 bits,
base64 on one line; PEM header lines are optional). Write `PRIVATEKEY` as one word: Spring reads every `_` in a variable
name as a `.`, so `PRIVATE_KEY` would not reach the `private-key` property:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-key.pem
grep -v -- ----- jwt-key.pem | tr -d '\n'     # the value for IAM_JWT_KEYS_0_PRIVATEKEY
```

Put both in `.env` for `./gradlew bootRun`, or pass them to the `app` container (`-e` / `environment:`). Without a key the
app generates a temporary one at startup (a WARN in the log) and every token becomes invalid when it restarts.
**Rotating:** add the new key as `IAM_JWT_KEYS_0_*` and move the old one to `IAM_JWT_KEYS_1_*`; the first key signs, both
are published, so tokens signed by the old key keep working. Remove the old key once the access tokens it signed have expired (refresh tokens aren't signed).
`IAM_JWT_ISSUER` (default `http://localhost:8080`) is the public base URL put into every token's `iss`.

**Browser apps (CORS).** A web app served from another origin (for example the management console on
`http://localhost:5173`) may call the API only when its origin is listed in `IAM_CORS_ALLOWEDORIGINS`, comma-separated:
`IAM_CORS_ALLOWEDORIGINS=http://localhost:5173,https://console.example.com` (in `.env`; `docker compose` passes it on).
Each entry is an exact origin (scheme, host, optional port; no path, trailing slash or `*`); anything else stops the app
at startup. Allowed: `GET`, `POST`, `PUT`, `PATCH`, `DELETE` with the headers `Authorization`, `Content-Type` and
`X-Tenant-Id`; no cookies. Requests from an origin that isn't listed get `403` ("Invalid CORS request"); without the
variable no other origin is allowed. Swagger UI, Postman and servers are not affected (same origin or no `Origin` header).

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
| Who am I | `GET /api/me` → your profile (`id`, `username`, `fullName`, `email`, `userType`, `rootUser`), `homeTenant` and `workingTenant` (`id`, `name`, `type`, `status`; the working tenant is the `X-Tenant-Id` tenant for a platform user working inside it, else your own) and `permissions`: every key you hold in the working tenant, sorted. Changes to your roles show at once. Regular users get `403` | Any logged-in management user; no permission needed |
| Change own password | `PATCH /api/users/password` `{ "oldPassword": "...", "newPassword": "..." }` | Any logged-in user, for themselves only |
| Reset another user's password | `POST /api/users/{id}/password-reset` (no body) → `204`: that user gets a reset OTP by email, their current password stops working at once and all their sessions end; they set a new password with the OTP (`POST /api/password/reset` through any client of the tenant). If the OTP can't be sent nothing changes: `400` (no email address), `429` (3 OTPs in the last hour), `503` (mail failure) | `users:reset-password`; never your own password, never the root user's |
| Forgot password: request an OTP | `POST /api/password/reset-request` `{ "client_id": "...", "identifier": "<username or email>" }` → `200` and the OTP is emailed. The answer is the same when no such user exists, the user has no email address or the email fails, so it never tells whether an account exists. A user gets at most 3 OTPs per hour; further requests get the same answer but send nothing | Public |
| Forgot password: reset with the OTP | `POST /api/password/reset` `{ "client_id": "...", "identifier": "<username or email>", "otp": "123456", "newPassword": "..." }` → `200`: the password is now the one you chose (password policy applies, `400` otherwise and the OTP stays usable), a login lockout ends, and every session of the user ends. Any problem (unknown user, no pending OTP, expired or wrong OTP) is `400` "Invalid or expired OTP"; the fifth wrong guess deletes the OTP, so request a new one | Public |
| Refresh tokens | `POST /api/auth/token/refresh` `{ "client_id": "...", "refresh_token": "..." }` (the client the token was issued through) → a new access token **and a new refresh token**; the old refresh token is used up | Public (needs a valid refresh token) |
| Log out | `POST /api/auth/logout` (no body) → `204`; ends the session of the Bearer token: its access token gets `401` here and its refresh token stops working | Any logged-in user |
| Client token settings | `PUT /api/clients/{id}/token-settings` (see below); applies to tokens issued from then on | `clients:update` |
| Suspend / activate a client | `PUT /api/clients/{id}/suspend` → the client with `"status": "SUSPENDED"`: at once nobody can log in, refresh or reset a password through it (`403` "Client is suspended"), and the access tokens of its sessions get `401` (introspection `active: false`); sessions are kept. `PUT /api/clients/{id}/activate` → `"ACTIVE"`, and unexpired tokens work again. The management console can't be suspended (`400`). | `clients:suspend` / `clients:activate` |
| Clients | `GET /api/clients`, `GET /api/clients/{id}`, `POST /api/clients` `{ "name" }`, `PUT /api/clients/{id}` `{ "name" }` (rename), `DELETE /api/clients/{id}` (never the console) | `clients:read` / `create` / `update` / `delete` |
| List users | `GET /api/users?page=0&size=20` → `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`, ordered by id; `size` is capped at 100 | `users:read` |
| Direct permissions | `PUT` / `DELETE /api/users/permissions` `{ "username", "permissionIds" }` (management users only, never yourself) | `users:update` |
| Find a user | `GET /api/users/{id}`, `/api/users/by-username?username=`, `/api/users/by-email?email=` | `users:read` |
| Delete a user | `DELETE /api/users/{id}` (users they created stay, without a creator) | `users:delete` (never a root user, not even by itself: `403`) |
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
| Create a tenant | `POST /api/platform/tenants` with the signup body `{ "tenantName", "rootUser": {...} }` → `201` with the tenant, its root user and its management console, exactly like signup (same rules; audited with you as the actor) | `tenants:create` on all tenants |
| List tenants | `GET /api/platform/tenants?page=0&size=20` → paged like `GET /api/users`, each `{ "id", "name", "type": "PLATFORM"/"STANDARD", "status" }` | the tenants you hold `tenants:read` on (all of them for the platform root; an empty page without grants) |
| View a tenant | `GET /api/platform/tenants/{id}` (`404` if unknown) | `tenants:read` on that tenant |
| Suspend a tenant | `PUT /api/platform/tenants/{id}/suspend` → the tenant with `"status": "SUSPENDED"`. At once, its users can't log in, refresh or reset passwords (`403` "Tenant is suspended"), their tokens get `401` and introspection says `active: false`. Sessions are kept. The platform tenant can't be suspended (`400`). | `tenants:suspend` on that tenant |
| Activate a tenant | `PUT /api/platform/tenants/{id}/activate` → `"status": "ACTIVE"`; logins work again, and tokens of unexpired sessions work again | `tenants:activate` on that tenant |

Tenant users (their root included) get `403` on every `/api/platform/**` endpoint.

**The Platform tenant holds only management users and its console.** Create platform users with `POST /api/users`
(no `X-Tenant-Id`) and `"userType": "MANAGEMENT"`; a regular user there gives `400`, and so does registering an
application client (`POST /api/clients`).

**Working inside a tenant.** Add the header `X-Tenant-Id: <tenant id>` to any normal request (`/api/users`, `/api/roles`,
`/api/clients`, ...) and it works inside that tenant, as if you were its root: list and create users, manage roles,
clients and token settings. This also works in a suspended tenant (for support). Every audited action records
`acting_tenant_id`. A header that isn't a number gives `400`, an unknown tenant `404`. Changing your own password still
changes yours. The platform root can do everything there. Other platform users can do what their **grants** on that
tenant (or on all tenants) allow: a granted platform role gives its permissions, a granted permission gives itself.
Inside the Platform tenant itself (no header) they use their normal roles; grants don't apply there.

**Giving grants** (from inside the Platform tenant, with `users:read` / `users:update` held there; the platform root has them):

| What | Request |
|---|---|
| A platform user's grants | `GET /api/platform/users/{userId}/grants` → `{ "roleGrants": [ { "id", "userId", "roleId", "roleName", "tenantId", "createdAt" } ], "permissionGrants": [ { "id", "userId", "permissionId", "permissionName", "tenantId", "createdAt" } ] }` (`tenantId` missing = all tenants) |
| Grant platform roles | `POST /api/platform/grants/roles` `{ "userId", "roleIds": [..], "tenantId" }` → `201` with the user's grants of those roles; leave out `tenantId` for all standard tenants (also future ones) |
| Remove role grants | `DELETE /api/platform/grants/roles` with the same body → `204`; removes those roles' grants on that tenant (without `tenantId`: the all-tenants grants) |
| Grant permissions | `POST /api/platform/grants/permissions` `{ "userId", "permissionIds": [..], "tenantId" }` → `201`; any catalog key, including `tenants:*` (e.g. `tenants:suspend` on all tenants) |
| Remove permission grants | `DELETE /api/platform/grants/permissions` with the same body → `204` |

Rules: only roles of the Platform tenant (another tenant's role is `404`) or catalog permissions (unknown `404`); only to management users of the Platform
tenant (others `404`), never to yourself (`403`) and never to the platform root (`400`, it holds everything already);
only on a standard tenant (the Platform tenant `400`, an unknown one `404`). Like assigning roles, a request is all or
nothing (one unknown id and nothing changes, `404`); a grant the user already has is kept, and removing one they don't
have changes nothing. A standard
tenant's users get `403`. Grants and their removal are audited and apply immediately.
Tenant users' `X-Tenant-Id` is ignored: they always work in their own tenant.

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
- **Password resets** (forgot-password and reset by another user) email the user an **OTP**, never a password; the user then
  chooses a new password with it. An OTP is valid for 5 minutes, replaces any earlier OTP of the user, allows 5 wrong
  guesses, and a user gets at most 3 per hour.
- **One root per tenant**, created only by signup; nobody can delete a root user (not even the root itself), and nobody else can reset its password.
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
| `400` | Already exists (tenant, user, role or client name in your tenant), deleting or suspending the management console, suspending the platform tenant, a regular user or an application client in the platform tenant, refreshing through a client without refresh tokens, an unknown token claim, roles or permissions for a regular user, bad password policy, wrong old password, invalid or expired OTP, negative page or page size below 1, missing or malformed request body |
| `401` | Not logged in: missing, malformed, expired or revoked access token, an access token of a locked or deactivated account or of a suspended tenant or client (at login these are `403`), wrong username/password, an unknown `client_id`, or a refresh token that is unknown, expired, already used, logged out or of another client |
| `403` | A browser request from an origin not listed in `IAM_CORS_ALLOWEDORIGINS`; a regular user calling `GET /api/me`; logged in but missing the required permission (including `users:create-management` for a management user); changing your own roles or permissions, or a role you hold; resetting your own or the root user's password; a regular user logging in through the management console; login, refresh or forgot-password for a suspended tenant ("Tenant is suspended") or through a suspended client ("Client is suspended"); or account locked, deactivated or expired |
| `404` | Not found, including an unknown tenant in `X-Tenant-Id`, users and roles of other tenants, and unknown permissions (forgot-password answers `200` for an unknown username/email too) |
| `405` | The HTTP method isn't supported (for example creating a permission) |
| `422` | Request body failed validation (the `errors` list shows which field), including token lifetimes out of range |
| `429` | Resetting another user's password when that user already got 3 reset OTPs in the last hour |
| `503` | Resetting another user's password when the OTP email can't be sent (nothing changed; try again later) |
