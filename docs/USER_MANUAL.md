# IAM System: User Manual

How to use the system as a developer through Swagger UI or Postman.

> **Matches the code as of step 6b (lightweight security principal).** This manual is updated in every step that changes how the system is
> used. When something here is temporary, it says which step changes it (see `docs/IMPLEMENTATION_PLAN.md`).

---

## 1. Concepts (as they work today)

| Concept | Today |
|---|---|
| **Tenant** | One organization. Created by **tenant signup**, which also creates the tenant's **root user**. |
| **Root user** | Exactly one per tenant. Holds every permission. Can create users, permissions and roles. |
| **Permission** | A pair `serviceName` + `action`, where action is one of `READ`, `WRITE`, `UPDATE`, `DELETE`. The system's own endpoints are protected by permissions of the service **`IAM`** (`IAM:READ`, `IAM:WRITE`, …). |
| **Role** | A named set of permissions, owned by one tenant (names are unique within the tenant). Users get permissions only through roles. |
| **"Management" user** | Not a separate type yet (step 7). Any user holding `IAM:*` permissions through a role acts as one. |
| **"Regular" user** | Not a separate type yet (step 7). A user without `IAM:*` permissions can log in, refresh tokens and change their own password. |
| **Tenant isolation** | Every user and role belongs to one tenant. With the right `IAM:*` permission you see and manage your **whole tenant**, and nothing else: users and roles of other tenants answer `404`. |
| **No self-modification** | Nobody can change their own roles, or change or delete a role they hold. Another user with the permission must do it. |

**Important limitations today:**
- **There are no default permissions or roles yet** (they come in step 7). After signup, the root user must create the
  `IAM` permissions and the roles first. See the walkthrough below.
- **Permissions are still shared by all tenants** (until step 7): every root user can create, change and delete them for everyone.
- **Usernames and emails are globally unique** across all tenants (until step 8).

---

## 2. Start the system

| How | Command | URL |
|---|---|---|
| Docker (app + MySQL) | `docker compose up --build app db` | `http://localhost:8080` |
| Docker (with ELK logging) | `docker compose up --build` | app `:8080`, Kibana `:5601` |
| Local JVM (needs MySQL on `localhost:3366`, e.g. `docker compose up db`) | `./gradlew bootRun` | `http://localhost:8080` (override with `SERVER_PORT=8081` in `.env`) |

The database schema is created automatically (Flyway) on first start. Mail settings (`SPRING_MAIL_USERNAME`,
`SPRING_MAIL_PASSWORD` in `.env`) are only needed for password reset.

---

## 3. Using Swagger UI

1. Open **`http://localhost:8080/swagger-ui.html`**.
2. Call `POST /api/tenants/signup`, then `POST /api/auth/authenticate`, and copy the `access_token` from the response.
3. Click **Authorize** (top right) and paste the token **without** the `Bearer ` prefix. Swagger adds it.
4. All protected endpoints now send the token. Access tokens expire after **5 minutes**: log in again (or refresh) and re-authorize.

## 4. Using Postman

1. Create an environment with `baseUrl = http://localhost:8080`, `accessToken`, `refreshToken`.
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
→ `201 Created` with the tenant and its root user. A taken tenant name, username or email gives `400`.

### Step 2: Log in as root (public)
`POST /api/auth/authenticate`
```json
{ "username": "globex-root", "password": "Root1234!" }
```
→ `200` with `access_token` (valid 5 minutes) and `refresh_token` (valid 24 hours). Use the access token as `Bearer` from now on.

### Step 3: Create the IAM permissions (root, once per system)
Permissions don't exist until someone creates them (temporary until step 7). Create each of `READ`, `WRITE`, `UPDATE`, `DELETE`:

`POST /api/permissions`
```json
{ "serviceName": "IAM", "action": "READ", "description": "View users, roles and permissions" }
```
→ `200` with the permission's `id`. Creating the same `serviceName` + `action` twice gives `400`.
`GET /api/permissions` lists them all.

What each IAM permission allows:

| Permission | Allows |
|---|---|
| `IAM:READ` | List/view users, roles and permissions |
| `IAM:WRITE` | Create users, roles and permissions |
| `IAM:UPDATE` | Update roles and permissions; assign/remove permissions on roles; assign/remove roles on users |
| `IAM:DELETE` | Delete users, roles and permissions |

### Step 4: Create a role and give it permissions (root)
`POST /api/roles`
```json
{ "name": "USER_MANAGER", "description": "Can create and view users" }
```
→ `201` with the role's `id`. The role belongs to your tenant; another tenant may use the same name. A name already used in your
tenant gives `400`. Then attach permissions:

`PUT /api/roles/permissions`
```json
{ "roleId": 1, "permissionIds": [1, 2] }
```
→ `204`. (`DELETE /api/roles/permissions` with the same body removes them.)

### Step 5: Create a user in your tenant (needs `IAM:WRITE`)
`POST /api/users`
```json
{ "username": "bob", "password": "Bob12345!", "fullName": "Bob Manager", "email": "bob@globex.com" }
```
→ `201`. The user joins the caller's tenant, and `createdBy` is the caller.

### Step 6: Give the user a role (needs `IAM:UPDATE`)
`PUT /api/users/roles`
```json
{ "username": "bob", "roleIds": [1] }
```
→ `204`. Bob now acts as a "management" user with `IAM:READ` + `IAM:WRITE`: he can create users and see every user of the tenant.
Bob can't change his own roles, or change the `USER_MANAGER` role while he holds it (`403`).
(`DELETE /api/users/roles` with the same body removes roles.) Changing a user's roles revokes the tokens they already
hold (`401`), so they log in again. Changing the permissions of a role applies to its users immediately, without new tokens.

### Step 7: Create a "regular" user
Log in as Bob and `POST /api/users` again (e.g. `carol`), without assigning roles. Carol can log in, refresh her tokens and
change her own password, and gets `403` on every management endpoint.

---

## 6. Everyday operations

| What | Request | Who |
|---|---|---|
| Change own password | `PATCH /api/users/password` `{ "oldPassword": "...", "newPassword": "..." }` | Any logged-in user |
| Change another user's password | `PATCH /api/users/password?username=carol` `{ "newPassword": "..." }` | Root user, for any user of its tenant |
| Refresh tokens | `POST /api/auth/token/refresh` `{ "username": "bob", "refresh_token": "..." }` | Public (needs a valid refresh token) |
| List users | `GET /api/users?page=0&size=20` → `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`, ordered by id; `size` is capped at 100 | `IAM:READ` |
| Find a user | `GET /api/users/{id}`, `/api/users/by-username?username=`, `/api/users/by-email?email=` | `IAM:READ` |
| Delete a user | `DELETE /api/users/{id}` (users they created stay, without a creator) | `IAM:DELETE` (a root user can only delete itself) |
| Roles | `GET /api/roles`, `GET /api/roles/{id}`, `GET /api/roles/name/{name}`, `PUT /api/roles/{id}` `{ "name", "description" }`, `DELETE /api/roles/{id}` (also removes it from its users) | `IAM:READ` / `UPDATE` / `DELETE`, never on a role you hold |
| Role permissions | `PUT /api/roles/permissions` / `DELETE /api/roles/permissions` `{ "roleId", "permissionIds" }` | `IAM:UPDATE`, never on a role you hold |
| Permissions | `GET /api/permissions/{id}`, `GET /api/permissions/name/{serviceName}`, `PUT /api/permissions/{id}`, `DELETE /api/permissions/{id}` | `IAM:READ` / `UPDATE` / `DELETE` |
| Forgot password | `POST /api/password/reset-request?email=...`, then `POST /api/password/reset?otp=...&email=...` (emails a new password; needs mail settings) | Public |

## 7. For other applications (verifying users)

| What | Request | Notes |
|---|---|---|
| Is this access token valid? | `POST /api/auth/token/validate` `{ "token": "..." }` → `{ "status": true }` | Public. Checks signature and expiry only (revocation in step 9). |
| May this user do X? | `POST /api/auth/authorize` with the user's `Bearer` token, body `{ "serviceName": "BILLING", "action": "READ" }` → `{ "authorized": true }` | The service and action must exist as a permission in one of the user's roles; root is always authorized. |

---

## 8. Rules worth knowing

- **Token lifetimes:** access token 5 minutes, refresh token 24 hours (hard-coded until step 8/9).
- **Lockout:** 5 failed logins lock the account for 15 minutes. While locked, the user's existing tokens are rejected too.
- **Revocation:** any change to a user (roles, password, profile, lock, deactivation) revokes the access and refresh tokens
  issued before it. Permissions are checked against the database on every request, so role changes apply immediately.
- **Password expiry:** passwords expire after 90 days; an expired password blocks login.
- **One root per tenant**, created only by signup; a root user can't be deleted by anyone else.
- **Tenant isolation:** users and roles of another tenant are reported as not found (`404`), never as forbidden.
- **Usernames and emails** stay unique across all tenants until step 8. From then on they are unique per tenant, except
  that a root user's username and email stay unique across all users of all tenants.

## 9. Status codes

| Code | Meaning here |
|---|---|
| `200` / `201` / `204` | OK / created / done without a body |
| `400` | Already exists (tenant, user, role name in your tenant, permission), bad password policy, wrong old password, invalid OTP, negative page or page size below 1 |
| `401` | Not logged in: missing, malformed, expired or revoked access token, a locked or deactivated account, or wrong username/password |
| `403` | Logged in but missing the required permission, changing your own roles or a role you hold, or account locked or expired |
| `404` | Not found, including users and roles of other tenants |
| `412` | Invalid or expired token on refresh/validation (changes to 401 in step 9) |
| `422` | Request body failed validation (the `errors` list shows which field) |
