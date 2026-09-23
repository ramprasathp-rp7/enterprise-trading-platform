# Sprint 6: Trade API

Spring Boot REST API for a trading platform with account management, order placement, order cancellation, and position tracking.

## Prerequisites

- **Java**: 21 or higher
- **Maven**: 3.9.x or higher
- **PostgreSQL**: 14+ (or H2 for testing)
- **Git**: For version control

## Project Structure

```
sprint-06-trade-api/
├── src/
│   ├── main/
│   │   ├── java/com/tradingplatform/
│   │   │   ├── api/
│   │   │   │   ├── controllers/      # REST endpoints
│   │   │   │   ├── services/         # Business logic
│   │   │   │   ├── mappers/          # MyBatis data access
│   │   │   │   ├── security/         # Authorization
│   │   │   │   └── exception/        # Error handling
│   │   │   └── domain/
│   │   │       ├── model/            # Domain entities
│   │   │       ├── dto/              # Data transfer objects
│   │   │       ├── enums/            # Enumerations
│   │   │       ├── exception/        # Domain exceptions
│   │   │       └── rules/            # Business rules
│   │   └── resources/
│   │       └── application.properties
│   └── test/                         # Unit tests
├── pom.xml                           # Maven dependencies
└── README.md                         # This file
```

## Setup & Installation

### 1. Clone the Repository
```bash
cd ~/Neueda/chennai-capstone-SE2-team1
git clone <repository-url>
cd sprint-06-trade-api
```

### 2. Configure Database

#### PostgreSQL (Production)
Update `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/trade_db
spring.datasource.username=your_username
spring.datasource.password=your_password
```

#### H2 (Development/Testing - Default)
Uses in-memory H2 database by default.

### 3. Build the Project
```bash
mvn clean install
```

### 4. Run Tests
```bash
mvn test
```

### 5. Start the Application
```bash
mvn spring-boot:run
```

The API will start on `http://localhost:8080`

## API Endpoints

### Authentication

All endpoints require a valid JWT bearer token:
```bash
Authorization: Bearer <your_jwt_token>
```

---

## Account Endpoints

### 1. Get Account Details

**Endpoint**: `GET /api/v1/accounts/{id}`

```bash
curl -X GET http://localhost:8080/api/v1/accounts/1 \
  -H "Authorization: Bearer your_jwt_token" \
  -H "Content-Type: application/json"
```

**Response** (200 OK):
```json
{
  "id": 1,
  "accountId": "ACC-000001",
  "holderName": "Priya Menon",
  "cashBalance": 24500.75,
  "currency": "USD",
  "status": "ACTIVE",
  "version": 0,
  "createdOn": "2026-01-10T09:00:00Z",
  "lastUpdated": "2026-01-10T09:00:00Z"
}
```

---

### 2. Get Account Balance

**Endpoint**: `GET /api/v1/accounts/{id}/balance`

```bash
curl -X GET http://localhost:8080/api/v1/accounts/1/balance \
  -H "Authorization: Bearer your_jwt_token" \
  -H "Content-Type: application/json"
```

**Response** (200 OK):
```json
{
  "accountId": "ACC-000001",
  "balance": 24500.75,
  "currency": "USD"
}
```

---

### 3. Get Account Positions

**Endpoint**: `GET /api/v1/accounts/{id}/positions`

```bash
curl -X GET http://localhost:8080/api/v1/accounts/1/positions \
  -H "Authorization: Bearer your_jwt_token" \
  -H "Content-Type: application/json"
```

**Response** (200 OK):
```json
[
  {
    "symbol": "INFY.NS",
    "instrumentId": 10,
    "quantity": 100,
    "averageCost": 2500.00,
    "totalValue": 250000.00
  },
  {
    "symbol": "RELIANCE.NS",
    "instrumentId": 5,
    "quantity": 50,
    "averageCost": 3000.00,
    "totalValue": 150000.00
  }
]
```

---

### 4. Get Order History

**Endpoint**: `GET /api/v1/accounts/{id}/orders`

**Query Parameters**:
- `status` (optional): Filter by OrderStatus (NEW, FILLED, CANCELLED, REJECTED)
- `from` (optional): Start date (ISO format)
- `to` (optional): End date (ISO format)

```bash
# All orders
curl -X GET http://localhost:8080/api/v1/accounts/1/orders \
  -H "Authorization: Bearer your_jwt_token" \
  -H "Content-Type: application/json"

# Filtered by status
curl -X GET "http://localhost:8080/api/v1/accounts/1/orders?status=FILLED" \
  -H "Authorization: Bearer your_jwt_token" \
  -H "Content-Type: application/json"

# Filtered by date range
curl -X GET "http://localhost:8080/api/v1/accounts/1/orders?from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z" \
  -H "Authorization: Bearer your_jwt_token" \
  -H "Content-Type: application/json"

# Combined filters
curl -X GET "http://localhost:8080/api/v1/accounts/1/orders?status=FILLED&from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z" \
  -H "Authorization: Bearer your_jwt_token" \
  -H "Content-Type: application/json"
```

**Response** (200 OK):
```json
[
  {
    "orderId": "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e",
    "accountId": 1,
    "symbol": "INFY.NS",
    "side": "BUY",
    "quantity": 100,
    "price": 2550.50,
    "executedPrice": 2550.50,
    "status": "FILLED",
    "idempotencyKey": "order-001",
    "createdOn": "2026-09-15T10:30:00Z"
  }
]
```

---

## Order Endpoints

### 1. Place an Order

**Endpoint**: `POST /api/v1/orders`

**Required Fields**:
- `accountId` (Long): Account ID > 0
- `symbol` (String): Trading symbol (1-20 chars)
- `side` (String): "BUY" or "SELL"
- `quantity` (Integer): > 0
- `price` (BigDecimal): > 0, max 2 decimal places
- `idempotencyKey` (String): 8-100 characters (unique identifier)

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer your_jwt_token" \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": 1,
    "symbol": "INFY.NS",
    "side": "BUY",
    "quantity": 100,
    "price": 2550.50,
    "idempotencyKey": "order-20260915-001"
  }'
```

**Response** (201 Created):
```json
{
  "orderId": "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e",
  "accountId": 1,
  "symbol": "INFY.NS",
  "side": "BUY",
  "quantity": 100,
  "price": 2550.50,
  "executedPrice": 2550.50,
  "status": "FILLED",
  "idempotencyKey": "order-20260915-001",
  "createdOn": "2026-09-15T10:30:00Z"
}
```

**Error Responses**:
- `400 Bad Request`: Validation failed (missing/invalid fields)
- `409 Conflict`: Duplicate idempotency key (ORD-409)
- `422 Unprocessable Entity`: Business rule violation (insufficient balance, account suspended, etc.)

---

### 2. Cancel an Order

**Endpoint**: `DELETE /api/v1/orders/{id}`

**Path Parameters**:
- `id` (String): Order UUID

```bash
curl -X DELETE http://localhost:8080/api/v1/orders/6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e \
  -H "Authorization: Bearer your_jwt_token" \
  -H "Content-Type: application/json"
```

**Response** (200 OK):
```json
{
  "orderId": "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e",
  "accountId": 1,
  "symbol": "INFY.NS",
  "side": "BUY",
  "quantity": 100,
  "price": 2550.50,
  "executedPrice": null,
  "status": "CANCELLED",
  "idempotencyKey": "order-20260915-001",
  "createdOn": "2026-09-15T10:30:00Z"
}
```

**Error Responses**:
- `404 Not Found`: Order not found
- `422 Unprocessable Entity`: Order cannot be cancelled (already filled/cancelled)

---

## Error Handling

### Common Error Codes

| Code | Status | Description |
|------|--------|-------------|
| ACC-401 | 401 | Unauthorized access |
| ACC-404 | 404 | Account not found |
| ORD-409 | 409 | Duplicate order (idempotency key already used) |
| VAL-422 | 422 | Validation failure (business rules) |
| SYS-500 | 500 | Internal server error |

### Error Response Format
```json
{
  "errorCode": "ORD-409",
  "message": "Duplicate order"
}
```

---

## Running with Bruno (API Client)

1. Import the provided Bruno collection
2. Set environment variables:
   - `base_url`: http://localhost:8080
   - `auth_token`: Your JWT token
3. Run individual requests or full test suite

---

## Testing

Run all tests:
```bash
mvn clean test
```

Run specific test class:
```bash
mvn test -Dtest=AccountServiceTest
```

Run with coverage:
```bash
mvn clean test jacoco:report
```

---

## Key Features

✅ **Account Management**: View account details, balance, positions, and order history

✅ **Order Placement**: Buy/Sell orders with idempotency protection

✅ **Order Cancellation**: Cancel NEW orders only

✅ **Position Tracking**: Real-time position updates with average cost calculation

✅ **Business Rules**: 
- Rule 1: Account existence check
- Rule 2: Account status (ACTIVE only)
- Rule 3: Instrument tradability
- Rule 4: Sufficient cash balance
- Rule 5: Position constraints
- Rule 6: Price validation
- Rule 7: Quantity validation
- Rule 8: Idempotency key uniqueness

✅ **Security**: OAuth2 resource server with JWT bearer tokens

✅ **Data Consistency**: Optimistic locking with version management

---

## Database Schema

Managed by Spring Boot migrations (sprint-03-trade-database):

- **accounts**: User trading accounts
- **instruments**: Tradable securities
- **orders**: Trade order history
- **positions**: Current holdings

---

## Development Notes

- **Framework**: Spring Boot 6.2.0
- **ORM**: MyBatis 3.5.14
- **Security**: Spring Security 6.4.1
- **Database Migrations**: Flyway
- **Build Tool**: Maven 3.x
- **Testing**: JUnit 5, Mockito

---

## Troubleshooting

### Application won't start
- Check PostgreSQL connection or ensure H2 is configured
- Verify Java version: `java -version` (should be 21+)
- Check Maven: `mvn -version`

### Tests failing
- Run: `mvn clean test`
- Check database migrations ran successfully
- Verify all dependencies installed: `mvn dependency:resolve`

### API returns 401 Unauthorized
- Ensure valid JWT token in Authorization header
- Token format: `Bearer <token>`

---

## Contact & Support

For issues or questions, contact the development team.

---

**Last Updated**: 2026-09-15
