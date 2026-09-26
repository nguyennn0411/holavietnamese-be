# SEP490 Backend - Clean Architecture Skeleton

Đây là khung Spring Boot backend theo Clean Architecture.
Chỉ dựng cấu trúc project, chưa có business logic, CRUD, database hay authentication.

## Dependency direction

adapter/in -> application -> domain
adapter/out implements application/port/out

Domain không phụ thuộc Spring, JPA hoặc framework bên ngoài.

## Project structure

src/main/java/com/sep490/backend
├── BackendApplication.java
├── domain
│   ├── entity
│   ├── valueobject
│   └── exception
├── application
│   ├── port
│   │   ├── in
│   │   └── out
│   ├── usecase
│   └── dto
│       ├── request
│       └── response
├── adapter
│   ├── in
│   │   └── web
│   │       ├── controller
│   │       └── mapper
│   └── out
│       └── persistence
│           ├── entity
│           ├── repository
│           └── mapper
├── infrastructure
│   ├── config
│   └── exception
└── common
    ├── constant
    └── util

## Ý nghĩa layer

- domain: Entity, Value Object và rule nghiệp vụ thuần Java.
- application/port/in: interface các use case mà bên ngoài được phép gọi.
- application/port/out: interface application cần để giao tiếp DB/service ngoài.
- application/usecase: implementation của use case.
- adapter/in/web: REST Controller và mapper request/response.
- adapter/out/persistence: adapter kết nối persistence, repository implementation.
- infrastructure/config: Spring Bean/configuration.
- infrastructure/exception: global exception handling phía framework.
- common: constant/util dùng chung, hạn chế chứa business logic.

## Run

```bash
mvn spring-boot:run
```

Yêu cầu Java 21.
"# holavietnamese-be" 
