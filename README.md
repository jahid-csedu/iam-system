# User Management System

A user-management-system with the following functionalities:

## Register a User

Endpoint: `/user/register`  
HTTP Method: _POST_
#### Example Request Body:
```aidl,
{
    "username": "test_user",
    "password": "password",
    "fullName": "Test User",
    "email": "test@mail.com"
}
```

#### Example Response:
```aidl,
{
    "username": "test_user",
    "password": null,
    "fullName": "Test User",
    "email": "test@mail.com"
}
```

Users can register to the system by making a POST request to this endpoint with the required information in the request body.

## Authenticate a User

Endpoint: `/user/authenticate`  
HTTP Method: _POST_  
#### Example Request Body: 
```aidl,
{
    "username": "test_user",
    "password": "password"
}
```

#### Example Response:
```aidl,
{
    "username": "test_user",
    "refresh_token": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0X3VzZXIiLCJpYXQiOjE2NzYyMjA0ODYsImV4cCI6MTY3NjMwNjg4Nn0.dgBHtw5muRlqSgFBNVNY_40URezZ5K7H7ctB6BPbIpgsv_6YE8banGvGUawtPNfK",
    "access_token": "eyJhbGciOiJIUzI1NiJ9.eyJhdXRob3JpdGllcyI6W3siYXV0aG9yaXR5IjoiUk9MRV9VU0VSIn1dLCJzdWIiOiJ0ZXN0X3VzZXIiLCJpYXQiOjE2NzYyMjA0ODYsImV4cCI6MTY3NjIyMDc4Nn0.RkXuqS-bWZCCeEXDBiLhm6ofv1IaNbMm3f0sohRS8mM"
}
```

Registered users can authenticate themselves to the system by providing their `username` and `password` in the request body. On successful authentication, the system will return a `access_token` and a `refresh_token` that can be used to access secured endpoints.

## Refresh Token

Endpoint: `/user/token/refresh`  
HTTP Method: _POST_
#### Example Request Body:
```aidl,
{
    "username": "test_user",
    "refresh_token": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJqYWhpZCIsImlhdCI6MTY3NjIxNDQ2MiwiZXhwIjoxNjc2MzAwODYyfQ.ho_C5iSygz-Lr3Wkopn0LQ-UBEdOrtBKrf0SrBPxrlqxtRBqFWw4QjI2jk_Lmlj2"
}
```

#### Example Response:
```aidl,
{
    "username": "test_user",
    "refresh_token": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJqYWhpZCIsImlhdCI6MTY3NjIyMDM4NSwiZXhwIjoxNjc2MzA2Nzg1fQ.Fd80aBeHE-mrlydYwaNys16ZoaL_7EA5oHj7HU5SUjmYBWGv9-WB-S7UAzCdTqfL",
    "access_token": "eyJhbGciOiJIUzI1NiJ9.eyJhdXRob3JpdGllcyI6W3siYXV0aG9yaXR5IjoiUk9MRV9VU0VSIn1dLCJzdWIiOiJqYWhpZCIsImlhdCI6MTY3NjIyMDM4NSwiZXhwIjoxNjc2MjIwNjg1fQ.fSYhOQC_UVgDRAnLm_h008HnAnmF-FWIGlch51sJZnM"
}
```

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