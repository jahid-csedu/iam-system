# User Management System

A user-management-system with the following functionalities:

## Register a User

Endpoint: `/user/register`

Users can register to the system by making a POST request to this endpoint with the required information in the request body.

## Authenticate a User

Endpoint: `/user/authenticate`

Registered users can authenticate themselves to the system by providing their `username` and `password` in the request body. On successful authentication, the system will return a `access_token` and a `refresh_token` that can be used to access secured endpoints.

## Refresh Token

Endpoint: `/user/token/refresh`

Users can refresh their `access_token` by making a POST request to this endpoint with the `refresh_token` in the request body. The system will return a new `access_token` that can be used to access secured endpoints.

## Secured Endpoints

All endpoints except the above three endpoints are secured and require a valid `access_token` to be provided in the `Authorization` header.

Example:
Authorization: Bearer <access_token>


## Note

Ensure to store the `access_token` and `refresh_token` securely on the client-side as they grant access to secured endpoints.

## Developed By:
### Md. Jahid Hasan
LinkedIn Profile:
[https://www.linkedin.com/in/jahid-csedu/](https://www.linkedin.com/in/jahid-csedu/)