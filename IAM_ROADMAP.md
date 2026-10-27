# IAM System: Feature Roadmap

Features planned after `docs/IMPLEMENTATION_PLAN.md`. The plan takes no new steps: anything new that comes up goes here
as a numbered item. Items are grouped by priority and, inside a group, ordered by work volume (smaller first, unless one
item needs another). Numbers are references used in the other documents; renumbering updates them everywhere.

Work volume: **S** = one step of a few commits · **M** = one larger step · **L** = several steps.

## Priority 1: needed soon (core of an Auth0-like product, or fixes known issues)

1. **Session management** (M)
   * **Description:** A cleanup job that purges ended sessions and used or expired refresh tokens; an absolute session
     lifetime per client (today a session slides forever while it's refreshed); users list their own sessions and sign
     out elsewhere or everywhere; admins end a user's sessions; optionally a limit on concurrent sessions.
   * **Why it's important:** Fixes two known issues (tables that only grow, sessions without an end) and gives users and
     admins control after a lost device or a suspicious login.
   * **Builds on:** the session and refresh-token tables (plan step 9b). Frontend gap G-19.

2. **Links in emails: reset and verification pages** (M, before item 3)
   * **Description:** An email the user didn't ask for must say where to go. Today the reset by a management user emails
     only a six-digit code, and the user doesn't know where to type it; verification codes are only sent when the user
     asks from inside an app, because there is no page to link to. Add links: the admin reset email links to the reset
     page, and new or changed addresses get a verification email with a link (then sent automatically). To decide with
     the frontend: where the page URL comes from (a per-client setting such as `password_reset_url`, like Auth0's
     application login URL, a tenant setting, or the hosted IAM pages), which client an admin reset uses (the admin picks
     one, or the tenant's default), and what the link carries: only `client_id` + identifier with the code typed by hand,
     or a long single-use token in the link instead of the code (never the short code itself in a URL, where browser
     history, proxies and logs keep it). Each gets its own email template.
   * **Why it's important:** Admin-initiated resets and verification of new accounts only work when the user can act
     on the email alone; self-registration (item 3) needs it.
   * **Builds on:** the email service and templates and the email verification codes (plan step 13); the frontend's
     auth pages (frontend steps 27 and 28). Was plan step 14 (moved here 2026-10-27).

3. **Self-registration of regular users** (M, required in the future)
   * **Description:** Today only management users create regular users (`POST /api/users`). Like Auth0, let the public
     register themselves as regular users of a tenant: the tenant's own portal shows a sign-up screen and calls a public
     registration endpoint with the tenant's `client_id`, which identifies the tenant (the same way login does). Each
     tenant decides whether self-registration is allowed (a tenant setting, switched by the root or a management user
     with permission; off means only management users create regular users, as today), with email verification of new
     accounts. Together with item 8, users who register through a client can be attached to that client.
   * **Why it's important:** Consumer-facing tenants need end users to sign up on their own, while other tenants
     (internal tools, B2B) must be able to keep sign-up closed.
   * **Builds on:** the `client_id` login model and the email service with verification (plan step 13). Frontend gap G-21.

4. **Self-service account deletion** (S, after item 3)
   * **Description:** `DELETE /api/users/{id}` refuses your own account, so nobody deletes themselves by mistake. A
     deliberate "delete my account" becomes its own endpoint: `DELETE /api/me` (or a client-facing equivalent for regular
     users) with the current password in the body, counted by `FailedPasswordCounter` like the email change; never the
     root (403); audited. To decide then: delete at once or deactivate first with a grace period; whether management
     users may use it. Frontend: a "Delete my account" danger zone on the account page, behind the password.
   * **Why it's important:** Self-registered users expect to be able to leave, and data-protection requests need it.
   * **Builds on:** item 3 (regular users with self-service); the rule "nobody deletes their own account" stays for the
     admin endpoint.

5. **Multi-factor authentication (MFA)** (L)
   * **Description:** TOTP first (authenticator apps, with recovery codes), later other methods (WebAuthn/passkeys,
     email codes). Login becomes: password ok → MFA challenge → tokens. A tenant or client policy decides whether MFA is
     optional or required; users enrol on their account page.
   * **Why it's important:** Prevents unauthorized access even when a password is compromised.
   * **Builds on:** the login flow in `AuthenticationService` and the lockout counter. Frontend gap G-24.

6. **Allowed web origins per client** (M, before item 7)
   * **Description:** Today the browser origins allowed to call the API are one platform-wide list
     (`iam.cors.allowed-origins`), set by whoever runs the IAM. A tenant's own single-page app on its own domain (calling
     login, refresh or forgot-password directly) needs the platform operator to add its origin. Like Auth0's "Allowed Web
     Origins", each client gets its own list of exact origins, edited with the client (`clients:update`) and validated
     like `CorsProperties`. A preflight carries no body and no header values, so it can't name the client (moving
     `client_id` into a header doesn't help; only the path or query string would). Proposed: the preflight allows the
     platform list plus the union of all active clients' lists (cached, refreshed when a list changes), and the actual
     request is checked exactly: public auth endpoints in `ClientResolver` (the origin must be in that client's list or
     the platform list, else 403), token calls in the JWT filter against the token's client; requests without `Origin`
     are unaffected. To decide then: one list or Auth0's separate CORS and web-origin lists, whether the management
     console stays platform-list only, and wildcard subdomains.
   * **Why it's important:** Tenants can integrate their own browser apps without the platform operator, and one
     tenant's page can't use another tenant's `client_id`.
   * **Builds on:** the CORS allowlist (`CorsConfig`, `CorsProperties`) and `ClientResolver`. Frontend step 29, gap G-26.
     Was plan step 15 (moved here 2026-10-27).

7. **OAuth2 / OpenID Connect provider** (L)
   * **Description:** Make the IAM a standard authorization server: registered redirect URIs per client, the
     authorization code flow with PKCE for browser and mobile apps, client secrets for confidential clients (then
     introspection requires client authentication), OIDC discovery, ID tokens and a userinfo endpoint, and a hosted login
     page that hands tokens back to the application. Consider Spring Authorization Server.
   * **Why it's important:** Applications can then use any standard OIDC library instead of calling the login API
     themselves, and the IAM can stand in for Auth0.
   * **Builds on:** the `client_id` model and the asymmetric tokens with JWKS (plan steps 8 and 9). Frontend gap G-3.

## Priority 2: valuable next

8. **Client-scoped regular users** (S)
   * **Description:** Regular users are tenant-scoped today: every application client of the tenant accepts them. Let a
     tenant admin optionally attach a regular user to one or more specific clients. A user without client attachments
     stays tenant-scoped (accepted by every application client, as now); a user attached to clients gets tokens only
     through those clients, and login or refresh through any other client is refused. This lets a tenant keep a separate
     user base per application.
   * **Why it's important:** Tenants with several unrelated applications (e.g. a customer app and a partner portal) can
     keep their user bases apart without creating a tenant per application.
   * **Builds on:** `ClientResolver` and the login and refresh checks.

9. **API key management** (M)
   * **Description:** Generate, revoke and rotate API keys for programmatic access (stored hashed, shown once), each with
     its own set of permissions and an optional expiry.
   * **Why it's important:** A secure and auditable way for scripts and services to call the management API without a
     user's password.
   * **Builds on:** a new authentication path that produces an `AuthenticatedActor`; the permission checks stay unchanged.

10. **User groups** (M)
    * **Description:** Organize users into groups; roles and permissions can be assigned to a group, and its members hold
      them.
    * **Why it's important:** Simplifies access management for tenants with many users.
    * **Builds on:** the permission resolution in `AuthorizationService` (user ∪ roles ∪ group roles), kept in agreement
      with `permissionsOf`.

11. **Maker-checker for role and permission changes** (M)
    * **Description:** An optional approval step, switched on per tenant by the root user: a change to a user's roles or
      permissions, or to a role's permissions, waits until a second user with the permission approves it.
    * **Why it's important:** The four-eyes principle that regulated organizations require for access changes.
    * **Builds on:** the access policies (`UserAccessPolicy`, `RoleAccessPolicy`) and the audit log.

12. **Per-tenant signing keys** (L)
    * **Description:** Give every tenant its own RSA key pair for signing tokens, instead of one platform key set.
      Private keys are stored encrypted (under a platform master key, or in a KMS/HSM), every tenant publishes its own
      JWKS (e.g. `/tenants/{id}/.well-known/jwks.json`), and root users can rotate their tenant's key. Tokens already
      carry a `kid` and a tenant-specific `iss`, so the token format doesn't change.
    * **Why it's important:** A leaked or compromised key affects one tenant only, and a tenant can rotate its keys on its
      own schedule.
    * **Builds on:** `SigningKeys` and `TokenVerifier` (plan step 9a).

## Priority 3: long-term / enterprise

13. **Emergency (break-glass) access** (M)
    * **Description:** Temporary, highly privileged access for emergencies: time-limited, justified, and strictly audited,
      with notifications to the tenant's root.
    * **Why it's important:** Business continuity during incidents without permanent over-privileged accounts.
    * **Builds on:** the platform permissions and grants (plan step 10) and the audit log.

14. **Login through external identity providers (SSO and federation)** (L)
    * **Description:** Let a tenant's users log in with an external identity provider over OIDC or SAML 2.0: social
      logins, or a partner organization's own IdP (a trust relationship per tenant), with users created or linked on
      their first login.
    * **Why it's important:** One set of credentials across applications, and partner access for B2B tenants without
      separate accounts.
    * **Builds on:** item 7 (OIDC on our side) and the client model.

15. **User provisioning (SCIM)** (L)
    * **Description:** Create, update and deactivate users and groups automatically from customers' HR or directory
      systems through the SCIM 2.0 protocol.
    * **Why it's important:** Keeps user data consistent across systems and automates onboarding and offboarding.
    * **Builds on:** stable tenants, users and groups (item 10).

16. **Attribute-based access control (ABAC)** (L)
    * **Description:** Authorization decisions that also consider attributes of the user, the resource and the
      environment (time, network, data owner), beyond roles and permissions.
    * **Why it's important:** Context-aware policies for complex enterprise environments.
    * **Builds on:** `AuthorizationService` gaining attribute inputs.
