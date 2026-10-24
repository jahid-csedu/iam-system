# IAM System: User Manual

How to use the system as a developer through Swagger UI or Postman.

> **Matches the code as of step 5 (tenant foundation).** This manual is updated in every step that changes how the system is
> used. When something here is temporary, it says which step changes it (see `docs/IMPLEMENTATION_PLAN.md`).

---

## 1. Concepts (as they work today)

| Concept | Today |
|---|---|
| **Tenant** | One organization. Created by **tenant signup**, which also creates the tenant's **root user**. |
| **Root user** | Exactly one per tenant. Holds every permission. Can create users, permissions and roles. |
| **Permission** | A pair `serviceName` + `action`, where action is one of `READ`, `WRITE`, `UPDATE`, `DELETE`. The system's own endpoints are protected by permissions of the service **`IAM`** (`IAM:READ`, `IAM:WRITE`, …). |
| **Role** | A named set of permissions. Users get permissions only through roles. |
| **"Management" user** | Not a separate type yet (step 7). Any user holding `IAM:*` permissions through a role acts as one. |
| **"Regular" user** | Not a separate type yet (step 7). A user without `IAM:*` permissions can log in, refresh tokens and change their own password. |
| **Visibility** | A user sees and manages only the users they created, directly or indirectly. Root sees everyone created in its tree. |

**Important limitations today:**
- **There are no default permissions or roles yet** (they come in step 7). After signup, the root user must create the
  `IAM` permissions and the roles first. See the walkthrough below.
- **Permissions and roles are not tenant-scoped yet** (step 6): they are shared by all tenants, and every root user can change all of them.
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
→ `200` with the role's `id`. Then attach permissions. Use this endpoint: `permissionIds` in the create body is currently ignored (known issue).

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
→ `204`. Bob now acts as a "management" user with `IAM:READ` + `IAM:WRITE`: he can create users and see the users he created.
(`DELETE /api/users/roles` with the same body removes roles.) Changing a user's roles can invalidate the tokens they already
hold, so they may need to log in again.

### Step 7: Create a "regular" user
Log in as Bob and `POST /api/users` again (e.g. `carol`), without assigning roles. Carol can log in, refresh her tokens and
change her own password, and gets `403` on every management endpoint.

---

## 6. Everyday operations

| What | Request | Who |
|---|---|---|
| Change own password | `PATCH /api/users/password` `{ "oldPassword": "...", "newPassword": "..." }` | Any logged-in user |
| Change a subordinate's password | `PATCH /api/users/password?username=carol` `{ "newPassword": "..." }` | Root user, for users in its tree |
| Refresh tokens | `POST /api/auth/token/refresh` `{ "username": "bob", "refresh_token": "..." }` | Public (needs a valid refresh token) |
| List users | `GET /api/users` (only users you created, directly or indirectly) | `IAM:READ` |
| Find a user | `GET /api/users/{id}`, `/api/users/by-username?username=`, `/api/users/by-email?email=` | `IAM:READ` |
| Delete a user | `DELETE /api/users/{id}` | `IAM:DELETE` (a root user can only delete itself) |
| Roles | `GET /api/roles`, `GET /api/roles/{id}`, `GET /api/roles/name/{name}`, `PUT /api/roles/{id}`, `DELETE /api/roles/{id}` | `IAM:READ` / `UPDATE` / `DELETE` |
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
- **Lockout:** 5 failed logins lock the account for 15 minutes.
- **Password expiry:** passwords expire after 90 days; an expired password blocks login.
- **One root per tenant**, created only by signup; a root user can't be deleted by anyone else.

## 9. Status codes

| Code | Meaning here |
|---|---|
| `200` / `201` / `204` | OK / created / done without a body |
| `400` | Already exists (tenant, user, permission), bad password policy, wrong old password, invalid OTP |
| `401` | Not logged in (missing or invalid token) or wrong username/password |
| `403` | Logged in but missing the required permission, or account locked or expired |
| `404` | Not found, including users outside your hierarchy |
| `412` | Invalid or expired token on refresh/validation (changes to 401 in step 9) |
| `422` | Request body failed validation (the `errors` list shows which field) |
