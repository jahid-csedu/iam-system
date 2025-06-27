# IAM System: User & Access Management

## What Is This Project?

This project is a centralized **Identity and Access Management (IAM) system**. Think of it as a digital gatekeeper for a company's applications. It ensures that only the right people can access the right software and perform specific actions.

For example, you can use it to:
-   Give an "Admin" user full control over all applications.
-   Allow a "Sales" user to view customer data but not delete it.
-   Let a "Marketing" user access the company blog but not the financial software.

It's the backbone for ensuring your company's digital assets are secure and that users only have the permissions they need to do their jobs.

## Key Features

*   **User Login:** Securely log in with a username and password.
*   **User Management:** Create, view, and manage users. It supports a hierarchy, so a manager can see the activity of the employees they manage.
*   **Role Management:** Define roles (like "Admin," "Editor," or "Viewer") and assign them to users.
*   **Permissions:** Assign specific permissions to each role (e.g., `READ`, `WRITE`, `DELETE`). This gives you fine-grained control over what users can do.
*   **Centralized Control:** It’s designed to manage access for multiple applications from one central place.

## How to Run and Test the Project (Using Docker)

This project is set up to run easily using Docker. You only need to install Docker on your machine, and it will handle all the other dependencies.

### Step 1: Install Docker

If you don't have Docker installed, download and install Docker Desktop from the official website:

*   [Download Docker Desktop](https://www.docker.com/products/docker-desktop)

Follow the installation instructions for your operating system.

### Step 2: Run the Application

1.  Open a terminal or command prompt.
2.  Navigate to the project's root directory (the `iam-system` folder).
3.  Run the following command:
    ```bash
    docker-compose up --build
    ```

    This command will:
    *   Build the application's Docker image.
    *   Start the MySQL database.
    *   Start your IAM system application.

    This might take a few minutes the first time as it downloads necessary components and builds the application.

If everything is successful, you'll see logs from both the `db` and `app` services. Look for messages indicating that the Spring Boot application has started successfully (e.g., "Started IamSystemApplication").

### Step 3: Test the API with Postman

Now you can use a tool like Postman to interact with the system. Here are a few key operations you can test:

#### 1. Create a User

*   **Method:** `POST`
*   **URL:** `http://localhost:8080/api/users/register`
*   **Body (select `raw` and `JSON`):**
    ```json
    {
      "username": "testuser",
      "password": "password123",
      "fullName": "Test User",
      "email": "test@example.com",
      "rootUser": true
    }
    ```
*   **Action:** Click "Send." You should get a successful response.

#### 2. Log In

*   **Method:** `POST`
*   **URL:** `http://localhost:8080/api/auth/authenticate`
*   **Body (select `raw` and `JSON`):**
    ```json
    {
      "username": "testuser",
      "password": "password123"
    }
    ```
*   **Action:** Click "Send." The response will include an `access_token`. **Copy this token** for the next steps.

#### 3. Get All Users (Requires Authentication)

*   **Method:** `GET`
*   **URL:** `http://localhost:8080/api/users`
*   **Headers:**
    *   Add a new header with the key `Authorization`.
    *   For the value, type `Bearer ` (with a space at the end) and then paste the `access_token` you copied.
*   **Action:** Click "Send." You should see a list of all users in the system.

---

## Available API Endpoints

Here is a list of the main operations you can perform. For protected endpoints, you must include the `Authorization` header as described above.

### Authentication
| Action | Method | URL | Protected |
| --- | --- | --- | --- |
| Log In | `POST` | `/api/auth/authenticate` | No |
| Validate Token | `POST` | `/api/auth/token/validate` | No |
| Refresh Token | `POST` | `/api/auth/token/refresh` | No |
| Authorize Action | `POST` | `/api/auth/authorize` | Yes |

### User Management
| Action | Method | URL | Protected |
| --- | --- | --- | --- |
| Create User | `POST` | `/api/users/register` | No |
| Get All Users | `GET` | `/api/users` | Yes |
| Get User by ID | `GET` | `/api/users/{id}` | Yes |
| Get User by Username | `GET` | `/api/users/by-username` | Yes |
| Get User by Email | `GET` | `/api/users/by-email` | Yes |
| Delete User | `DELETE`| `/api/users/{id}` | Yes |
| Assign Roles to User | `PUT` | `/api/users/roles` | Yes |
| Remove Roles from User| `DELETE`| `/api/users/roles` | Yes |

### Role Management
| Action | Method | URL | Protected |
| --- | --- | --- | --- |
| Create Role | `POST` | `/api/roles` | Yes |
| Get All Roles | `GET` | `/api/roles` | Yes |
| Get Role by ID | `GET` | `/api/roles/{id}` | Yes |
| Update Role | `PUT` | `/api/roles/{id}` | Yes |
| Delete Role | `DELETE`| `/api/roles/{id}` | Yes |
| Assign Permissions | `PUT` | `/api/roles/permissions` | Yes |
| Remove Permissions | `DELETE`| `/api/roles/permissions` | Yes |

### Permission Management
| Action | Method | URL | Protected |
| --- | --- | --- | --- |
| Create Permission | `POST` | `/api/permissions` | Yes |
| Get All Permissions | `GET` | `/api/permissions` | Yes |
| Get Permission by ID| `GET` | `/api/permissions/{id}`| Yes |
| Update Permission | `PUT` | `/api/permissions/{id}`| Yes |
| Delete Permission | `DELETE`| `/api/permissions/{id}`| Yes |