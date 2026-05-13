# BuildSmart - Construction Project Management Backend

Modular Monolith backend system built with Java, Spring Boot, MySQL, Hibernate, and Spring Data JPA.

## Package Structure

```
com.buildsmart
├── projectmanager     # Project & Task management
├── finance            # Budget, Expense, Payment management
├── common             # Shared exceptions, enums, utilities
└── config             # Security configuration
```

## Tech Stack

- **Java 21**
- **Spring Boot 4.0.4**
- **Spring Data JPA** with Hibernate
- **MySQL**
- **Lombok**
- **Bean Validation**

## ID Formats (Custom Generated)

| Entity  | Format      | Example    |
|---------|-------------|------------|
| Project | CHEBS + YY + NNN | CHEBS26001 |
| Task    | Department prefix + NNN | FINBS001, VENBS001, SAFBS001, SITBS001 |
| Budget  | BUDBS + NNN | BUDBS001   |
| Expense | EXPBS + NNN | EXPBS001   |
| Invoice | INVBS + NNN | INVBS001   |
| Payment | PAYBS + NNN | PAYBS001   |

## API Endpoints

### Project Manager
- `POST /api/projects` - Create project
- `GET /api/projects` - List projects (paginated)
- `GET /api/projects/{projectId}` - Get project
- `PUT /api/projects/{projectId}` - Update project
- `DELETE /api/projects/{projectId}` - Delete project

- `POST /api/tasks` - Create task
- `GET /api/tasks/{taskId}` - Get task
- `GET /api/tasks/project/{projectId}` - List tasks by project
- `PUT /api/tasks/{taskId}` - Update task
- `PATCH /api/tasks/{taskId}/status?status=PENDING` - Update task status
- `DELETE /api/tasks/{taskId}` - Delete task

### Finance
- `POST /api/budgets` - Create budget
- `GET /api/budgets/{budgetId}` - Get budget
- `GET /api/budgets/project/{projectId}` - List budgets by project
- `PUT /api/budgets/{budgetId}` - Update budget
- `DELETE /api/budgets/{budgetId}` - Delete budget

- `POST /api/expenses` - Create expense
- `GET /api/expenses/{expenseId}` - Get expense
- `GET /api/expenses/project/{projectId}` - List expenses by project
- `GET /api/expenses/pending` - List pending expenses
- `PUT /api/expenses/{expenseId}` - Update expense
- `DELETE /api/expenses/{expenseId}` - Delete expense

- `POST /api/invoices` - Create invoice (required before payments)
- `GET /api/invoices/{invoiceId}` - Get invoice

- `POST /api/payments` - Create payment
- `GET /api/payments/{paymentId}` - Get payment
- `GET /api/payments/invoice/{invoiceId}` - List payments by invoice
- `GET /api/payments` - List all payments
- `PUT /api/payments/{paymentId}` - Update payment
- `DELETE /api/payments/{paymentId}` - Delete payment

## Setup

1. **MySQL**: Ensure MySQL is running. Update `application.properties` with your credentials:
   ```
   spring.datasource.username=root
   spring.datasource.password=your_password
   ```

2. **Run**: `./mvnw spring-boot:run` (or `mvn spring-boot:run`)

3. **Database**: Tables are auto-created via `spring.jpa.hibernate.ddl-auto=update`

## Validations

- **Project**: No duplicate projectId, unique projectName, startDate ≠ endDate, endDate > startDate, budget > 0
- **Task**: plannedEnd > plannedStart, must belong to existing project
- **Budget**: One category per project (no duplicate Electrical budget for same project), amounts ≥ 0
- **Payment**: amount ≥ 0, invoiceId required
