# IAM System: User Manual

How to use the system as a developer through Swagger UI or Postman.

> **Matches the code as of step 8a (client applications and `client_id`).** This manual is updated in every step that changes how the system is
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
- **Token lifetimes are the same for every client** (5 minutes / 24 hours) until per-client token settings arrive (step 8b).

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
| `clients:update` | Rename clients |
| `clients:delete` | Delete application clients (never the management console) |
| `permissions:read` | List the catalog and view a permission |

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

The database schema and the permission catalog are created automatically (Flyway) on first start. Mail settings
(`SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` in `.env`) are needed for password resets: OTPs and new passwords are only ever
sent by email. Each development step starts from a fresh database: `docker compose down -v` removes the old one.

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
→ `200` with `access_token` (valid 5 minutes) and `refresh_token` (valid 24 hours). Use the access token as `Bearer` from now on.
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
(`DELETE /api/users/roles` with the same body removes roles.) Changing a user's roles revokes the tokens they already
hold (`401`), so they log in again. Changing the permissions of a role applies to its users immediately, without new tokens.

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
| Refresh tokens | `POST /api/auth/token/refresh` `{ "client_id": "...", "refresh_token": "..." }` (the client the token was issued through) | Public (needs a valid refresh token) |
| Clients | `GET /api/clients`, `GET /api/clients/{id}`, `POST /api/clients` `{ "name" }`, `PUT /api/clients/{id}` `{ "name" }` (rename), `DELETE /api/clients/{id}` (never the console) | `clients:read` / `create` / `update` / `delete` |
| List users | `GET /api/users?page=0&size=20` → `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`, ordered by id; `size` is capped at 100 | `users:read` |
| Direct permissions | `PUT` / `DELETE /api/users/permissions` `{ "username", "permissionIds" }` (management users only, never yourself) | `users:update` |
| Find a user | `GET /api/users/{id}`, `/api/users/by-username?username=`, `/api/users/by-email?email=` | `users:read` |
| Delete a user | `DELETE /api/users/{id}` (users they created stay, without a creator) | `users:delete` (a root user can only delete itself) |
| Roles | `GET /api/roles`, `GET /api/roles/{id}`, `GET /api/roles/name/{name}` | `roles:read` |
| Change roles | `POST /api/roles`, `PUT /api/roles/{id}` `{ "name", "description" }`, `DELETE /api/roles/{id}` (also removes it from its users) | `roles:create` / `roles:update` / `roles:delete`, never on a role you hold |
| Role permissions | `PUT /api/roles/permissions` / `DELETE /api/roles/permissions` `{ "roleId", "permissionIds" }` | `roles:update`, never on a role you hold |
| Permission catalog | `GET /api/permissions`, `GET /api/permissions/{id}` (read-only) | `permissions:read` |

## 7. For other applications (verifying users)

| What | Request | Notes |
|---|---|---|
| Is this access token valid? | `POST /api/auth/token/validate` `{ "token": "..." }` → `{ "status": true }` | Public. Checks signature and expiry only (revocation in step 9). |

The IAM manages only its own permissions; other applications decide their own authorization from the verified user.

---

## 8. Rules worth knowing

- **Token lifetimes:** access token 5 minutes, refresh token 24 hours (hard-coded until step 8/9).
- **Lockout:** 5 failed logins lock the account for 15 minutes. While locked, the user's existing tokens are rejected too.
- **Revocation:** any change to a user (roles, password, profile, lock, deactivation) revokes the access and refresh tokens
  issued before it. Permissions are checked against the database on every request, so role changes apply immediately.
- **Password expiry:** passwords expire after 90 days; an expired password blocks login.
- **Password resets** (forgot-password and reset by another user) generate a new password and send it **only by email**; it never
  appears in a response. A forgot-password OTP is valid for 5 minutes and replaces any earlier OTP of the user.
- **One root per tenant**, created only by signup; a root user can't be deleted, or have its password reset, by anyone else.
- **Tenant isolation:** users and roles of another tenant are reported as not found (`404`), never as forbidden.
- **Usernames and emails** are unique per tenant: two tenants may both have a `bob`. Exception: a root user's username and
  email are unique across all users of all tenants. Login and forgot-password look the user up only inside the tenant of
  the request's `client_id`.
- **Tokens** carry the user id as `sub`, the `client_id` they were issued through, and the user's `version`. A refresh token
  only works through the client it was issued through.

## 9. Status codes

| Code | Meaning here |
|---|---|
| `200` / `201` / `204` | OK / created / done without a body |
| `400` | Already exists (tenant, user, role or client name in your tenant), deleting the management console, roles or permissions for a regular user, bad password policy, wrong old password, invalid or expired OTP, negative page or page size below 1, missing or malformed request body |
| `401` | Not logged in: missing, malformed, expired or revoked access token, a locked or deactivated account, wrong username/password, or an unknown `client_id` |
| `403` | Logged in but missing the required permission (including `users:create-management` for a management user); changing your own roles or permissions, or a role you hold; resetting your own or the root user's password; a regular user logging in through the management console; or account locked, deactivated or expired |
| `404` | Not found, including users and roles of other tenants, unknown permissions, and an unknown username/email on forgot-password |
| `405` | The HTTP method isn't supported (for example creating a permission) |
| `412` | Invalid or expired token on refresh/validation (changes to 401 in step 9) |
| `422` | Request body failed validation (the `errors` list shows which field) |
