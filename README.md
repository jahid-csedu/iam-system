
# **IAM System**

## **Overview**
The Identity and Access Management (IAM) Service provides centralized authentication and authorization for multiple microservices. It supports hierarchical user management, dynamic role-based access control, and token-based authentication using JWT.

---

## **Features**
- **Authentication**:
    - Authenticate with username and password.
    - Token generation and validation using JWT.
- **Authorization**:
    - Permissions dynamically assigned based on roles and services.
    - Support for service-action-based permission checks.
- **User Management**:
    - Hierarchical user tree with root and non-root users.
    - Non-root users can only be created by authorized users.
    - Recursive user tree queries for descendant-based access control.
- **Microservice Integration**:
    - Centralized authorization for external microservices via REST API.
- **Security**:
    - Secure password storage using BCrypt.
    - Enforced authorization checks via the `/api/auth/authorize` endpoint.

---

## **Tech Stack**
- **Programming Language**: Java (JDK 17)
- **Frameworks**:
    - Spring Boot
    - Spring Security
- **Build Tool**: Maven
- **Database**: MySQL
- **JWT Library**: Integrated for token-based authentication.

## **Endpoints**

### **Authentication**
1. **Authentication**
    - **URL**: `/api/auth/authenticate`
    - **Method**: `POST`
    - **Request**:
      ```json
      {
        "username": "user",
        "password": "securepassword"
      }
      ```
    - **Response**:
      ```json
      {
         "username": "user",
         "access_token": "JWT_TOKEN",
         "refresh_token": "REFRESH_TOKEN"
      }
      ```

2. **Validate Token**
    - **URL**: `/api/auth/token/validate`
    - **Method**: `POST`
    - **Request**:
      ```json
      {
        "token": "JWT_TOKEN"
      }
      ```
    - **Response**:
      ```json
      {
        "valid": true,
        "message": "Valid"
      }
      ```

3. **Authorize**
    - **URL**: `/api/auth/authorize`
    - **Method**: `POST`
    - **Header**: `Authorization` (Bearer token)
    - **Request**:
      ```json
      {
        "username": "user",
        "serviceName": "service-name",
        "action": "READ"
      }
      ```
    - **Response**:
      ```json
      {
        "authorized": true,
        "message": "Access granted"
      }
      ```

4. **Token Refresh**
    - **URL**: `/api/auth/token/refresh`
    - **Method**: `POST`
    - **Request**:
      ```json
      {
         "username": "root2",
         "refresh_token": "REFRESH_TOKEN"
      }
      ```
    - **Response**:
      ```json
      {
         "username": "user",
         "access_token": "JWT_TOKEN",
         "refresh_token": "REFRESH_TOKEN"
      }
      ```

---

### **User Management**
1. **Create User**
    - **URL**: `/api/users/register`
    - **Method**: `POST`
    - **Permissions Required**: `IAM:WRITE`
    - **Request**:
      ```json
      {
         "username": "root",
         "password": "password",
         "fullName": "Root User",
         "email": "root@mail.com",
         "rootUser": true
      }
      ```
    - **Response**:
      ```json
      {
         "username": "root",
         "password": null,
         "fullName": "Root User",
         "email": "root@mail.com",
         "rootUser": true,
         "roleIds": []
      }
      ```

2. **Find All Descendants**
    - **URL**: `/api/users`
    - **Method**: `GET`
    - **Permissions Required**: `IAM:READ`
    - **Description**: Returns all users under the logged-in user tree.

3. **Find By ID**
    - **URL**: `/api/users/{id}`
    - **Method**: `GET`
    - **Permissions Required**: `IAM:READ`
    - **Description**: Returns user details if the user is within the logged-in user's tree.

---

## **Setup Instructions**

### **1. Prerequisites**
- JDK 17 or higher
- Maven 3.6+
- MySQL database

### **2. Clone the Repository**
```bash
git clone https://github.com/jahid-csedu/iam-system.git
cd iam-system
```

### **3. Configure Database**
Update the `application.yml` file with your database details:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/<db_name>
    username: <username>
    password: <password>
```

### **4. Build the Project**
```bash
./mvnw clean install
```

### **5. Run the Application**
```bash
./mvnw spring-boot:run
```

---

## **Testing**
### **Unit Tests**
Run the unit tests:
```bash
./mvnw test
```

### **Integration Tests**
Integration tests use Testcontainers to spin up a MySQL/PostgreSQL container:
```bash
./mvnw verify
```

---

## **Future Enhancements**
- Policy-based permissions similar to AWS IAM.
- Support for OAuth2 and OpenID Connect.
- Role delegation and inheritance.
- Logging and auditing features.

---
