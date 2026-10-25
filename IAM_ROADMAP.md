# IAM System - Future Features Roadmap

This document outlines important features to consider for the evolution of the IAM system, moving beyond its current foundational capabilities towards a more robust and enterprise-grade solution.

## High-Priority Features (Security & Usability)

1.  **Multi-Factor Authentication (MFA):**
    *   **Description:** Implement support for various MFA methods (e.g., TOTP, SMS, hardware tokens) to significantly enhance user account security.
    *   **Why it's important:** Prevents unauthorized access even if passwords are compromised.

## Mid-Priority Features (Integration & Management)

2.  **Single Sign-On (SSO):**
    *   **Description:** Integrate with external Identity Providers (IdPs) using industry standards like OpenID Connect (OIDC) or SAML 2.0.
    *   **Why it's important:** Provides seamless access to multiple applications with a single set of credentials, enhancing user experience and simplifying access management.

3.  **User Groups:**
    *   **Description:** Introduce the concept of user groups to logically organize users. Roles and permissions can then be assigned to groups, simplifying management for larger user bases.
    *   **Why it's important:** Streamlines user and access management, especially in organizations with many users and varying access needs.

## Long-Term / Advanced Features

4.  **User Provisioning/Deprovisioning:**
    *   **Description:** Implement automated user lifecycle management (creation, update, deletion) with integrated applications using protocols like SCIM.
    *   **Why it's important:** Ensures consistency of user data across systems and automates onboarding/offboarding processes.

5.  **Fine-Grained Access Control (FGAC) / Attribute-Based Access Control (ABAC):**
    *   **Description:** Evolve beyond simple RBAC to allow more dynamic authorization decisions based on attributes of the user, resource, and environment.
    *   **Why it's important:** Enables highly flexible and context-aware access policies, suitable for complex enterprise environments.

6. **Session Management Enhancements:**
    *   **Description:** Implement more advanced session management features, such as concurrent session control, configurable session lifetimes, and forced session termination.
    *   **Why it's important:** Enhances security by providing greater control over active user sessions.

7. **Identity Federation:**
    *   **Description:** Establish trust relationships with external identity providers to allow users from other organizations to access your system.
    *   **Why it's important:** Facilitates B2B integrations and partner access.

8. **API Key Management:**
    *   **Description:** Develop a system for generating, revoking, and managing API keys for programmatic access, allowing specific permissions to be associated with each key.
    *   **Why it's important:** Provides a secure and auditable way for applications and services to interact with your system.

9. **Emergency Access/Break-Glass Accounts:**
    *   **Description:** Define and implement secure procedures for granting temporary, highly privileged access in emergency situations, with strict auditing.
    *   **Why it's important:** Ensures business continuity during critical incidents while maintaining security. 
10. **Per-Tenant Signing Keys:**
    *   **Description:** Give every tenant its own RSA key pair for signing tokens, instead of one platform key set. Private keys are stored encrypted (under a platform master key, or in a KMS/HSM), every tenant publishes its own JWKS (e.g. `/tenants/{id}/.well-known/jwks.json`), and root users can rotate their tenant's key. Tokens already carry a `kid` and a tenant-specific `iss`, so the token format doesn't change.
    *   **Why it's important:** A leaked or compromised key affects one tenant only, and a tenant can rotate its keys on its own schedule.

11. **Client-Scoped Regular Users:**
    *   **Description:** Regular users are tenant-scoped today: every application client of the tenant accepts them. Let a tenant admin optionally attach a regular user to one or more specific clients. A user without client attachments stays tenant-scoped (accepted by every application client, as now); a user attached to clients gets tokens only through those clients, and login or refresh through any other client is refused. This lets a tenant keep a separate user base per application.
    *   **Why it's important:** Tenants with several unrelated applications (e.g. a customer app and a partner portal) can keep their user bases apart without creating a tenant per application.

12. **Self-Registration of Regular Users (required in the future):**
    *   **Description:** Today only management users create regular users (`POST /api/users`). Like Auth0, let the public register themselves as regular users of a tenant: the tenant's own portal (frontend) shows a sign-up screen and calls a public registration endpoint with the tenant's `client_id`, which identifies the tenant (the same way login does). Each tenant decides whether self-registration is allowed (a tenant setting, switched by the root or a management user with permission; off means only management users create regular users, as today), with email verification of new accounts. Together with item 11, users who register through a client can be attached to that client.
    *   **Why it's important:** Consumer-facing tenants need end users to sign up on their own, while other tenants (internal tools, B2B) must be able to keep sign-up closed.
