# Library Management System (Spring Boot Multi-Module Project)

A simple, beginner-friendly **Spring Boot 3.x** multi-module Maven project using **Java 21**, **MySQL**, and **Liquibase** for database migrations.

This project implements a **Library Management System** with basic CRUD operations for Books and Users, organized using Clean Architecture principles.

---

## Project Structure

The project is split into four distinct Maven modules, coordinated by a Parent POM:

```text
library-management/
│
├── pom.xml (Parent POM)                     # Version management & module definitions
│
├── common/                                  # Shared DTOs, custom exceptions & global handling
│   ├── pom.xml
│   └── src/main/java/com/example/library/common/
│       ├── api/ApiResponse.java             # Generic JSON response envelope
│       └── exception/
│           ├── ResourceNotFoundException.java
│           └── GlobalExceptionHandler.java  # Controller advice for error mapping
│
├── book/                                    # Book domain module (CRUD)
│   ├── pom.xml
│   └── src/main/java/com/example/library/book/
│       ├── controller/BookController.java
│       ├── dto/BookRequest.java             # Record DTO (validations included)
│       ├── dto/BookResponse.java            # Record DTO
│       ├── model/Book.java                  # JPA Entity
│       ├── repository/BookRepository.java
│       └── service/
│           ├── BookService.java
│           └── BookServiceImpl.java
│
├── user/                                    # User domain module (CRUD)
│   ├── pom.xml
│   └── src/main/java/com/example/library/user/
│       ├── controller/UserController.java
│       ├── dto/UserRequest.java             # Record DTO (validations included)
│       ├── dto/UserResponse.java            # Record DTO
│       ├── model/User.java                  # JPA Entity
│       ├── repository/UserRepository.java
│       └── service/
│           ├── UserService.java
│           └── UserServiceImpl.java
│
└── application/                             # Runnable entrypoint module
    ├── pom.xml
    └── src/main/
        ├── java/com/example/library/LibraryManagementApplication.java  # Spring Boot runner
        └── resources/
            ├── application.yml              # Configuration (MySQL, logging properties)
            └── db/changelog/                # Liquibase migration change sets
```

### Module Responsibilities
* **Parent (`pom.xml`)**: Manages common dependency versions (`dependencyManagement` and `pluginManagement`) to avoid duplication.
* **`common`**: Houses shared code such as standard controllers advice, custom exceptions, and API models. Does not contain any database code.
* **`book` & `user`**: Self-contained domain modules containing entities, controllers, and services. Each depends on the `common` module.
* **`application`**: Aggregates all domain modules and spins up the Spring context. It is the only runnable module.

---

## Design Choices & Best Practices
* **Java Records**: Leveraged for DTOs (`BookRequest`, `BookResponse`, etc.) to provide immutable and clean data containers.
* **Constructor Injection**: Implemented using Lombok `@RequiredArgsConstructor` on Services and Controllers.
* **Global Exception Handling**: Automatically translates custom exceptions and validation failures into standard `ApiResponse` payloads.
* **Automatic Scanning**: Shared base package `com.example.library` allows Spring Boot to scan components, entities, and JPA repositories across all modules automatically.
* **Liquibase Schema Control**: Database creation and modification is version-controlled via XML changeSets, and Hibernate auto-DDL update is disabled (`validate` mode).

---

## Prerequisites
1. **Java 21** or higher.
2. **MySQL** instance running.

---

## Setup & Running

### 1. Database Creation
Create a MySQL database named `library`. You can do this using standard SQL:
```sql
CREATE DATABASE library;
```

### 2. Configure Database Credentials (Optional)
If your MySQL credentials differ from the defaults (`username: root`, `password: Th@ni2005`, `url: localhost:3306`), edit the `application/src/main/resources/application.yml` file, or set the environment variables:
* `SPRING_DATASOURCE_URL`
* `SPRING_DATASOURCE_USERNAME`
* `SPRING_DATASOURCE_PASSWORD`

### 3. Build the Project
Open a terminal in the `library-management` root directory and compile all modules:
* **Windows (using Maven Wrapper):**
  ```bash
  .\mvnw clean install
  ```
* **macOS/Linux (using Maven Wrapper):**
  ```bash
  ./mvnw clean install
  ```
* **Using Global Maven:**
  ```bash
  mvn clean install
  ```

### 4. Run the Application
Start the Spring Boot server:
* **Using Maven Wrapper:**
  ```bash
  .\mvnw spring-boot:run -pl application
  ```
* **Using Global Maven:**
  ```bash
  mvn spring-boot:run -pl application
  ```
The server will start on port `8080`.

---

## API Endpoints

### Book REST APIs

| Method | Endpoint | Request Body | Description |
| :--- | :--- | :--- | :--- |
| **POST** | `/books` | `BookRequest` (JSON) | Create a new book |
| **GET** | `/books` | *None* | Get list of all books |
| **GET** | `/books/{id}` | *None* | Get book by ID |
| **PUT** | `/books/{id}` | `BookRequest` (JSON) | Update a book by ID |
| **DELETE**| `/books/{id}` | *None* | Delete a book by ID |

#### Sample Book Request JSON
```json
{
  "title": "Clean Architecture",
  "author": "Robert C. Martin",
  "isbn": "9780134494166"
}
```

### User REST APIs

| Method | Endpoint | Request Body | Description |
| :--- | :--- | :--- | :--- |
| **POST** | `/users` | `UserRequest` (JSON) | Create a new user |
| **GET** | `/users` | *None* | Get list of all users |
| **GET** | `/users/{id}` | *None* | Get user by ID |
| **PUT** | `/users/{id}` | `UserRequest` (JSON) | Update a user by ID |
| **DELETE**| `/users/{id}` | *None* | Delete a user by ID |
| **POST** | `/users/{userId}/borrow/{bookId}` | *None* | Borrow a book (links a book to a user) |
| **POST** | `/users/{userId}/return/{bookId}` | *None* | Return a book (unlinks a book from a user) |

#### Sample User Request JSON
```json
{
  "name": "Jane Doe",
  "email": "jane.doe@example.com"
}
```

---

## Response Envelope
All API requests return a consistent JSON response envelope:
```json
{
  "success": true,
  "message": "Book created successfully",
  "data": {
    "id": 1,
    "title": "Clean Architecture",
    "author": "Robert C. Martin",
    "isbn": "9780134494166"
  },
  "timestamp": "2026-07-06T13:00:00"
}
```
In case of errors (e.g. invalid fields), the schema reflects:
```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "email": "Invalid email format"
  },
  "timestamp": "2026-07-06T13:02:00"
}
```
