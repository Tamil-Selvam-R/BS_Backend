# BuildSmart - Resource Allocation & Workforce Management Module

## Project Overview

This is the **Resource Allocation & Workforce Management** module (Module 4.3) of the **BuildSmart** platform.
BuildSmart is a web-based platform for construction companies, contractors, and project managers
to plan, monitor, and control construction projects.

This module handles:
- Creating and managing resources (Labor and Equipment)
- Allocating resources to construction projects
- Calculating total cost based on resource type, skill level, and duration
- Communicating with other microservices (IAM, Site Operations, Financials)

---

## Tech Stack

| Technology             | Version    |
|------------------------|------------|
| Java                   | 21         |
| Spring Boot            | 3.2.5      |
| Spring Cloud           | 2023.0.0   |
| Spring Data JPA        | 3.2.x      |
| Spring Security        | 6.2.x      |
| Spring Cloud OpenFeign | 4.1.x      |
| Netflix Eureka Client  | 4.1.x      |
| MySQL                  | 8.x        |
| Lombok                 | 1.18.36    |
| JJWT (JWT Library)     | 0.12.6     |
| SpringDoc OpenAPI      | 2.5.0      |
| Maven                  | 3.x        |

---

## Project Structure

```
src/main/java/com/buildsmart/resource_allocation/
│
├── ResourceAllocationApplication.java       (Main class with @EnableFeignClients, @EnableDiscoveryClient)
│
├── model/                                   (JPA Entity classes)
│   ├── Resource.java                        (Resource entity - Labor or Equipment)
│   └── Allocation.java                      (Allocation entity - links Resource to Project)
│
├── repository/                              (Spring Data JPA Repositories)
│   ├── ResourceRepository.java              (CRUD + custom queries for Resource)
│   └── AllocationRepository.java            (CRUD + custom queries for Allocation)
│
├── service/                                 (Business Logic layer)
│   ├── ResourceService.java                 (Interface for Resource operations)
│   ├── ResourceServiceImpl.java             (Implementation with validations and cost calculation)
│   ├── AllocationService.java               (Interface for Allocation operations)
│   └── AllocationServiceImpl.java           (Implementation with validations and cost calculation)
│
├── controller/                              (REST API endpoints)
│   ├── ResourceController.java              (CRUD endpoints for Resource - /api/resources)
│   └── AllocationController.java            (CRUD endpoints for Allocation - /api/allocations)
│
├── dto/                                     (Data Transfer Objects)
│   ├── ResourceDTO.java                     (DTO for Resource data)
│   ├── AllocationDTO.java                   (DTO for Allocation input data)
│   ├── AllocationResponseDTO.java           (DTO for Allocation response with resource details)
│   ├── ResourceCostDTO.java                 (DTO to send cost data to Financials module)
│   └── ResourceAllocationEventDTO.java      (DTO to send allocation event to Site Operations module)
│
├── exception/                               (Custom Exception handling)
│   ├── BadRequestException.java             (400 - Bad Request)
│   ├── ResourceNotFoundException.java       (404 - Not Found)
│   ├── ResourceAlreadyAllocatedException.java (409 - Conflict)
│   ├── ErrorResponse.java                   (Error response structure)
│   └── GlobalExceptionHandler.java          (Catches all exceptions globally)
│
├── utility/                                 (Utility classes)
│   └── IdGeneratorUtil.java                 (Custom ID generator - RESBS001, ALCBS001, etc.)
│
├── security/                                (JWT Security layer)
│   ├── JwtUtil.java                         (Parses JWT token, extracts email, role, userId)
│   ├── JwtAuthenticationFilter.java         (Filter that validates JWT on every request)
│   └── SecurityConfig.java                  (Spring Security configuration - stateless, JWT based)
│
├── client/                                  (Feign Client for calling other microservices)
│   ├── IAMServiceClient.java                (Feign Client interface for IAM module)
│   └── dto/
│       ├── UserDTO.java                     (DTO to receive user data from IAM)
│       └── IAMApiResponse.java              (Generic wrapper for IAM API response)
│
└── config/                                  (Configuration classes)
    └── OpenApiConfig.java                   (Swagger/OpenAPI configuration with JWT support)
```

---

## Entity Details

### Resource Entity

| Field          | Type    | Description                                          |
|----------------|---------|------------------------------------------------------|
| resourceId     | String  | Primary Key (auto-generated: RESBS001, RESBS002...)  |
| type           | String  | "Labor" or "Equipment"                               |
| availability   | String  | "Available", "Unavailable", or "On Leave"            |
| numberOfLabors | Integer | Number of labors (only for Labor type)               |
| skillLevel     | String  | "Skilled", "Semi-Skilled", or "Unskilled" (Labor)    |
| equipmentName  | String  | Name of equipment (only for Equipment type)          |
| equipmentLevel | String  | "Heavy", "Medium", or "Light" (Equipment)            |
| costPerHour    | Double  | Auto-calculated based on skill/equipment level       |
| totalCost      | Double  | Total cost calculated when allocation is created     |

### Allocation Entity

| Field          | Type      | Description                                          |
|----------------|-----------|------------------------------------------------------|
| allocationId   | String    | Primary Key (auto-generated: ALCBS001, ALCBS002...)  |
| projectId      | String    | Project ID from Project Planning module              |
| resource       | Resource  | ManyToOne relation to Resource entity                |
| assignedDate   | LocalDate | Date when resource is assigned                       |
| releasedDate   | LocalDate | Date when resource will be released                  |
| status         | String    | "Active", "Pending", or "Released"                   |

---

## Cost Calculation Logic

### Labor Cost
```
totalCost = numberOfLabors x costPerHour x totalHours
totalHours = (releasedDate - assignedDate) in days x 8 hours per day
```

**Cost per hour by skill level:**
| Skill Level  | Cost Per Hour (INR) |
|--------------|---------------------|
| Unskilled    | 80.0                |
| Semi-Skilled | 120.0               |
| Skilled      | 250.0               |

### Equipment Cost
```
totalCost = costPerHour x totalHours
totalHours = (releasedDate - assignedDate) in days x 8 hours per day
```

**Cost per hour by equipment level:**
| Equipment Level | Cost Per Hour (INR) |
|-----------------|---------------------|
| Light           | 500.0               |
| Medium          | 1500.0              |
| Heavy           | 5000.0              |

---

## API Endpoints

### Resource Controller — `/api/resources`

| Method | Endpoint                  | Description                              |
|--------|---------------------------|------------------------------------------|
| POST   | /api/resources            | Create a new resource                    |
| GET    | /api/resources            | Get all resources                        |
| GET    | /api/resources/page       | Get resources with pagination and filter |
| GET    | /api/resources/{id}       | Get resource by ID                       |
| PUT    | /api/resources/{id}       | Update resource by ID                    |
| DELETE | /api/resources/{id}       | Delete resource by ID                    |
| GET    | /api/resources/type/{type}| Get resources by type (Labor/Equipment)  |
| GET    | /api/resources/available  | Get all available resources              |

**Pagination filters for GET /api/resources/page:**
- page (default: 0)
- size (default: 10)
- type (optional: "Labor" or "Equipment")
- availability (optional: "Available", "Unavailable", "On Leave")
- skillLevel (optional: "Skilled", "Semi-Skilled", "Unskilled")
- equipmentLevel (optional: "Heavy", "Medium", "Light")

### Allocation Controller — `/api/allocations`

| Method | Endpoint                              | Description                                |
|--------|---------------------------------------|--------------------------------------------|
| POST   | /api/allocations                      | Create a new allocation                    |
| GET    | /api/allocations                      | Get all allocations                        |
| GET    | /api/allocations/page                 | Get allocations with pagination and filter |
| GET    | /api/allocations/{id}                 | Get allocation by ID                       |
| PUT    | /api/allocations/{id}                 | Update allocation by ID                    |
| DELETE | /api/allocations/{id}                 | Delete allocation by ID                    |
| GET    | /api/allocations/project/{projectId}  | Get allocations by project ID              |
| GET    | /api/allocations/resource/{resourceId}| Get allocations by resource ID             |

**Pagination filters for GET /api/allocations/page:**
- page (default: 0)
- size (default: 10)
- projectId (optional)
- resourceId (optional)
- status (optional: "Active", "Released", "Pending")

---

## Business Logic Validations

### Resource Validations
- Type must be "Labor" or "Equipment"
- Availability must be "Available", "Unavailable", or "On Leave"
- Equipment cannot have "On Leave" status (only Labor can)
- Labor requires: numberOfLabors (1-500) and skillLevel
- Equipment requires: equipmentName and equipmentLevel
- Cannot change type while resource has active allocations
- Cannot delete resource with active or pending allocations

### Allocation Validations
- Project ID is required
- Resource ID is required and must exist in database
- Resource must be "Available" (not "Unavailable" or "On Leave")
- Assigned date cannot be in the past
- Released date is required and cannot be before assigned date
- Released date cannot be same as assigned date
- Released date cannot be more than 1 year from assigned date
- Status must be "Active", "Released", or "Pending"
- Cannot create allocation with "Released" status
- Cannot update a released allocation
- Cannot change project ID or assigned date for active allocation
- Duplicate active allocation for same resource and project is not allowed

---

## Microservice Communication

### Service Registry
- Registers with **Eureka Server** at `http://localhost:8761/eureka/`
- Service name: `resource-allocation`
- Runs on port: `8081`

### Feign Client - IAM Module
Calls the IAM module (port 8082) for user information:

| Feign Method       | IAM Endpoint             | Purpose                          |
|--------------------|--------------------------|----------------------------------|
| getUserProfile()   | GET /users/profile       | Get current user profile         |
| checkUserRole()    | GET /users/check-role/{role} | Check if user has a role     |
| getUserById()      | GET /users/{userId}      | Get user by ID                   |
| getUserByEmail()   | GET /users/by-email      | Get user by email                |

All Feign calls forward the JWT token in the Authorization header.

### Communication Flow
```
1. Site Operations module  -->  YOUR module (requests resource)
2. YOUR module             -->  IAM module (validates user via Feign Client)
3. YOUR module             -->  Finance module (sends totalCost for budget approval via Feign Client)
4. Finance module          -->  YOUR module (returns Approved / Rejected)
5. YOUR module             -->  Site Operations (sends allocation confirmation)
```

---

## Security

### JWT Token Validation
- Every API request (except /actuator and /swagger-ui) requires a valid JWT token
- Token must be sent in the Authorization header as: `Bearer <token>`
- The JWT token is created by the IAM module when a user logs in
- This module only validates the token (does not create tokens)
- JWT secret must match the IAM module secret

### How JWT validation works in this module
1. Request comes in with `Authorization: Bearer <token>` header
2. `JwtAuthenticationFilter` extracts the token
3. `JwtUtil` parses the token and extracts email and role
4. Spring SecurityContext is set with the user details
5. If token is invalid or missing, the request is rejected with 401

### Roles from IAM module
- ADMIN
- PROJECT_MANAGER
- SITE_ENGINEER
- SAFETY_OFFICER
- VENDOR
- FINANCE_OFFICER

---

## Custom ID Generation

IDs are auto-generated using `IdGeneratorUtil`:

| Entity     | ID Format   | Example                      |
|------------|-------------|------------------------------|
| Resource   | RESBS + 3 digits | RESBS001, RESBS002, RESBS003 |
| Allocation | ALCBS + 3 digits | ALCBS001, ALCBS002, ALCBS003 |

The system reads the last ID from the database and increments the number by 1.

---

## Configuration (application.properties)

| Property                          | Value                              | Description                    |
|-----------------------------------|------------------------------------|--------------------------------|
| server.port                       | 8081                               | Application port               |
| spring.application.name           | resource-allocation                | Service name for Eureka        |
| spring.datasource.url             | jdbc:mysql://localhost:3306/buildsmartwork | MySQL database URL      |
| iam.service.url                   | http://localhost:8082              | IAM module URL for Feign       |
| app.jwt.secret                    | mySecretKey12345...                | JWT secret (must match IAM)    |
| eureka.client.service-url.defaultZone | http://localhost:8761/eureka/  | Eureka server URL              |

---

## How to Run

### Prerequisites
- Java 21 (JDK 21) installed
- MySQL running on port 3306
- Database `buildsmartwork` will be auto-created
- Eureka Server running on port 8761
- IAM Service running on port 8082

### Steps

1. **Set JAVA_HOME to JDK 21** (important - Lombok does not support Java 24):
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
   ```

2. **Navigate to the project folder:**
   ```powershell
   cd C:\Users\2480337\Downloads\resource-allocation\resource-allocation
   ```

3. **Compile the project:**
   ```powershell
   .\mvnw.cmd compile
   ```

4. **Run the application:**
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

5. **Access the APIs:**
   - API Base URL: `http://localhost:8081/api/`
   - Swagger UI: `http://localhost:8081/swagger-ui.html`
   - Health Check: `http://localhost:8081/actuator/health`

### Testing with Postman
- First login through IAM module: `POST http://localhost:8082/api/auth/login`
- Copy the JWT token from the response
- Add header to all requests: `Authorization: Bearer <your-token>`

---

## Dependencies Added (pom.xml)

| Dependency                                | Purpose                                |
|-------------------------------------------|----------------------------------------|
| spring-boot-starter-web                   | REST API support                       |
| spring-boot-starter-data-jpa              | JPA and Hibernate for database         |
| spring-boot-starter-security              | Spring Security for JWT validation     |
| spring-boot-starter-validation            | Bean validation                        |
| spring-boot-starter-actuator              | Health check endpoints                 |
| spring-cloud-starter-openfeign            | Feign Client for calling other modules |
| spring-cloud-starter-netflix-eureka-client| Service registration with Eureka       |
| mysql-connector-j                         | MySQL database driver                  |
| lombok                                    | Reduces boilerplate code               |
| jjwt-api, jjwt-impl, jjwt-jackson        | JWT token parsing                      |
| springdoc-openapi-starter-webmvc-ui       | Swagger UI documentation               |
| spring-boot-devtools                      | Auto-restart during development        |

---

## Port Allocation for BuildSmart Microservices

| Service              | Port |
|----------------------|------|
| Eureka Server        | 8761 |
| Resource Allocation  | 8081 |
| IAM Service          | 8082 |
| Safety Service       | 8083 |

---

## Module Communication Diagram

```
                    +------------------+
                    |  Eureka Server   |
                    |    (Port 8761)   |
                    +--------+---------+
                             |
            +----------------+----------------+
            |                |                |
   +--------+-------+ +-----+------+ +-------+--------+
   |  IAM Service   | | Resource   | | Safety Service |
   |  (Port 8082)   | | Allocation | | (Port 8083)    |
   |                | | (Port 8081)| |                |
   +--------+-------+ +-----+------+ +-------+--------+
            |                |                |
            |    Feign Call  |                |
            +<---------------+                |
            |  (get user)    |                |
            |                |                |
            |                +<---------------+
            |                | (Site Ops calls|
            |                |  for resource) |
            |                |                |
            |                +--------------->+
            |                | (send allocation
            |                |  confirmation)
            |                |
            |        +-------+--------+
            |        | Finance Module |
            |        | (Future)       |
            |        +----------------+
            |                |
            |    Feign Call  |
            +<---------------+
               (send cost for
                budget approval)
```

