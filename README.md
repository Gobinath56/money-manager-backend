# Money Manager Backend

Spring Boot backend application with MongoDB for the Money Manager system.

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- MongoDB Atlas account (or local MongoDB)

## Setup Instructions

### 1. Configure MongoDB

Edit `src/main/resources/application.properties`:

```properties
spring.data.mongodb.uri=mongodb+srv://<username>:<password>@<cluster-url>/money-manager?retryWrites=true&w=majority
```

Replace:
- `<username>` with your MongoDB username
- `<password>` with your MongoDB password
- `<cluster-url>` with your MongoDB Atlas cluster URL

### 2. Build the Project

```bash
mvn clean install
```

### 3. Run the Application

```bash
mvn spring-boot:run
```

The server will start on `http://localhost:5000`

## API Endpoints

### Transactions

- **GET** `/api/transactions` - Get all transactions
- **GET** `/api/transactions/{id}` - Get transaction by ID
- **POST** `/api/transactions` - Create new transaction
- **PUT** `/api/transactions/{id}` - Update transaction (12-hour restriction applies)
- **DELETE** `/api/transactions/{id}` - Delete transaction
- **GET** `/api/transactions/filter` - Get filtered transactions
  - Query params: `division`, `category`, `startDate`, `endDate`
- **GET** `/api/transactions/dashboard` - Get dashboard data

### Request Body Example (POST/PUT)

```json
{
  "type": "INCOME",
  "amount": 5000.00,
  "description": "Monthly Salary",
  "category": "SALARY",
  "division": "PERSONAL",
  "date": "2026-02-06T10:30:00"
}
```

### Response Example

```json
{
  "id": "65f1234567890abcdef12345",
  "type": "INCOME",
  "amount": 5000.00,
  "description": "Monthly Salary",
  "category": "SALARY",
  "division": "PERSONAL",
  "date": "2026-02-06T10:30:00",
  "createdAt": "2026-02-06T10:30:00",
  "updatedAt": "2026-02-06T10:30:00"
}
```

## Features

- ✅ Create, Read, Update, Delete transactions
- ✅ 12-hour edit restriction (throws 403 error after 12 hours)
- ✅ Filter by division, category, date range
- ✅ Dashboard with monthly, weekly, yearly summaries
- ✅ MongoDB integration
- ✅ CORS enabled for React frontend
- ✅ Input validation
- ✅ Global exception handling

## Transaction Types

- **INCOME**: SALARY, FREELANCE, INVESTMENT, OTHER
- **EXPENSE**: FUEL, MOVIE, FOOD, LOAN, MEDICAL, OTHER

## Divisions

- OFFICE
- PERSONAL

## Project Structure

```
src/main/java/com/moneymanager/
├── config/          # CORS configuration
├── controller/      # REST controllers
├── dto/            # Data Transfer Objects
├── exception/      # Custom exceptions and handlers
├── model/          # Entity models
├── repository/     # MongoDB repositories
└── service/        # Business logic
```

## Technologies Used

- Spring Boot 3.2.0
- Spring Data MongoDB
- Lombok
- Maven
- Java 17
