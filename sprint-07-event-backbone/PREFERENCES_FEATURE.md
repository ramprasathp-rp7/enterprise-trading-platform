# Customer Preferences Feature - Implementation Summary

## Overview
Implemented a comprehensive Customer Preferences system for the Trading Platform that allows individual traders to persist and manage their client-side settings and defaults. Preferences are stored at the account level and can override platform-wide defaults.

## Architecture & Design

### Technology Stack
- **Database**: PostgreSQL (JSONB columns for flexible preference storage)
- **Persistence**: MyBatis (annotation-based SQL mapper)
- **Framework**: Spring Boot 3.4.0 with Spring Security + OAuth2
- **Serialization**: Jackson ObjectMapper for JSON conversion
- **Testing**: JUnit 5, Mockito

### Data Model

#### Domain Models
1. **CustomerPreference** - Immutable model representing per-account preferences
   - `accountId`: Long (PK, FK to accounts table)
   - `preferences`: Map<String, Object> (JSONB)
   - `lastModified`: LocalDateTime

2. **PlatformDefaults** - Immutable model for system-wide defaults
   - `id`: Long (always 1, enforced at DB level)
   - `defaults`: Map<String, Object> (JSONB)
   - `lastUpdated`: LocalDateTime

#### Database Schema
- **customer_preferences** table:
  - `account_id` (PK, FK to accounts)
  - `preferences` (JSONB)
  - `last_modified` (timestamp)
  - Index on account_id for performance

- **platform_defaults** table:
  - `id` (PK, constrained to 1 via CHECK)
  - `defaults` (JSONB)
  - `last_updated` (timestamp)

### Default Preferences
The system comes with sensible defaults for all customers:
```json
{
  "NOTIFICATION_EMAIL": true,
  "NOTIFICATION_TRADING_ALERTS": true,
  "DISPLAY_THEME": "light",
  "DISPLAY_CURRENCY": "USD",
  "TRADING_DEFAULT_ORDER_TYPE": "LIMIT",
  "TRADING_MAX_ORDER_SIZE": 10000,
  "TIMEZONE": "UTC",
  "LANGUAGE": "en"
}
```

## Feature Implementation

### 1. Database Layer (PreferenceMapper)
**File**: `src/main/java/com/tradingplatform/api/mappers/PreferenceMapper.java`

MyBatis mapper interface with CRUD operations:
- `findPreferencesByAccountId(Long accountId)` - Retrieves customer preferences
- `findPlatformDefaults()` - Retrieves system defaults
- `upsertPreferences(Long accountId, String preferencesJson)` - Insert or update (PostgreSQL ON CONFLICT)
- `deletePreferences(Long accountId)` - Delete account preferences
- `existsByAccountId(Long accountId)` - Check if preferences exist

**Key Design Decision**: Preferences are passed as JSON strings to the mapper for type safety and to leverage PostgreSQL's native JSONB handling.

### 2. Service Layer (PreferenceService)
**File**: `src/main/java/com/tradingplatform/api/services/PreferenceService.java`

Business logic and preference management:

**Core Methods**:
- `getPreferencesForAccount(Long accountId)` - Returns merged preferences (account + platform defaults)
- `updateAccountPreferences(Long accountId, Map<String, Object> preferences)` - Partial update with validation
- `deleteAccountPreferences(Long accountId)` - Resets to platform defaults
- `getPlatformDefaults()` - Returns system-wide defaults

**Key Features**:
- **Merge Strategy**: Account-level preferences override platform defaults
- **Partial Updates**: Only provided keys are updated, others retain their values
- **Validation**: Extensible validation framework for preference keys/values
- **JSON Serialization**: ObjectMapper handles Map ↔ JSON conversion
- **Account Verification**: All methods verify account exists before operation

### 3. REST API (PreferenceController)
**File**: `src/main/java/com/tradingplatform/api/controllers/PreferenceController.java`

RESTful endpoints for preference management:

| Endpoint | Method | Purpose | Authorization |
|----------|--------|---------|-----------------|
| `/api/v1/accounts/{id}/preferences` | GET | Retrieve merged preferences | Account-level |
| `/api/v1/accounts/{id}/preferences` | PUT | Update preferences (partial) | Account-level |
| `/api/v1/accounts/{id}/preferences` | DELETE | Reset to platform defaults | Account-level |
| `/api/v1/preferences/defaults` | GET | Get platform defaults | Public (no auth) |

**Security**:
- Account-level endpoints use `AccountAccessAuthorizer` to enforce that users can only access their own account's preferences
- Platform defaults endpoint is public (informational)

### 4. Exception Handling
**File**: `src/main/java/com/tradingplatform/domain/exception/InvalidPreferenceException.java`

Custom exception for preference validation errors:
- Extends `DomainException`
- Error Code: `PREF-422` (Unprocessable Entity)
- Updated `ErrorCode` enum with new code
- Updated `GlobalExceptionHandler` with mapping

### 5. Data Transfer Objects (DTOs)

1. **PreferenceRequest** (`domain/dto/`)
   - Contains Map<String, Object> preferences
   - Used for request body validation

2. **PreferenceResponse** (`domain/dto/`)
   - Contains `accountId`, `preferences` (merged), `lastModified`
   - Returned by GET/PUT endpoints

3. **PlatformDefaultsResponse** (`domain/dto/`)
   - Contains `defaults` (Map)
   - Returned by platform defaults endpoint

## Testing

### Unit Tests
**File**: `src/test/java/com/tradingplatform/api/services/PreferenceServiceTest.java`

Comprehensive test coverage with 16 test cases:
- Preference retrieval with merge logic
- Partial updates and existing preference handling
- Null/empty preference validation
- Account existence verification
- Platform defaults retrieval
- Customer preference getter

**Mock Strategy**: AccountService and PreferenceMapper are mocked for isolation

### Integration Tests
**File**: `src/test/java/com/tradingplatform/api/controllers/PreferenceControllerTest.java`

10 test cases covering:
- Authorization checks (account-level access control)
- CRUD operations on preferences
- Error handling (403 Unauthorized, 422 Unprocessable Entity)
- Response structure and content validation
- Public endpoint access (platform defaults)

## API Usage Examples

### Get User's Preferences
```bash
GET /api/v1/accounts/123/preferences
Authorization: Bearer <JWT_TOKEN_FOR_ACCOUNT_123>

Response (200 OK):
{
  "accountId": 123,
  "preferences": {
    "NOTIFICATION_EMAIL": true,
    "DISPLAY_THEME": "dark",
    "LANGUAGE": "fr",
    "TIMEZONE": "UTC",
    ... // includes all platform defaults
  },
  "lastModified": "2026-09-15T10:30:45"
}
```

### Update Preferences (Partial)
```bash
PUT /api/v1/accounts/123/preferences
Authorization: Bearer <JWT_TOKEN_FOR_ACCOUNT_123>
Content-Type: application/json

Request Body:
{
  "preferences": {
    "DISPLAY_THEME": "dark",
    "LANGUAGE": "fr"
  }
}

Response (200 OK):
{
  "accountId": 123,
  "preferences": {
    "NOTIFICATION_EMAIL": true,      // from platform defaults
    "DISPLAY_THEME": "dark",          // from update
    "LANGUAGE": "fr",                 // from update
    "TIMEZONE": "UTC",                // from platform defaults
    ... // all merged preferences
  },
  "lastModified": "2026-09-15T10:35:12"
}
```

### Delete Preferences (Reset to Defaults)
```bash
DELETE /api/v1/accounts/123/preferences
Authorization: Bearer <JWT_TOKEN_FOR_ACCOUNT_123>

Response (204 No Content)
```

### Get Platform Defaults
```bash
GET /api/v1/preferences/defaults

Response (200 OK):
{
  "defaults": {
    "NOTIFICATION_EMAIL": true,
    "DISPLAY_THEME": "light",
    "TIMEZONE": "UTC",
    ...
  }
}
```

## Extension Points

### 1. Preference Validation
The `validatePreferences()` method in PreferenceService can be extended with type-specific validation:
```java
// Example: Validate TIMEZONE
if (entry.getKey().equals("TIMEZONE")) {
    try {
        ZoneId.of((String) entry.getValue());
    } catch (Exception e) {
        throw new InvalidPreferenceException("Invalid timezone: " + entry.getValue());
    }
}
```

### 2. Business Logic Integration
The service can be extended to enforce preferences in trading logic:
```java
// Example: Use TRADING_MAX_ORDER_SIZE in order validation
public boolean isOrderSizeValid(Long accountId, int orderQuantity) {
    Map<String, Object> prefs = getPreferencesForAccount(accountId);
    Integer maxSize = (Integer) prefs.get("TRADING_MAX_ORDER_SIZE");
    return orderQuantity <= maxSize;
}
```

### 3. Admin Preferences Management
Can add admin endpoints for updating platform defaults:
```java
@PostMapping("/admin/preferences/defaults")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<PlatformDefaultsResponse> updatePlatformDefaults(...)
```

## Files Created/Modified

### New Files
1. `sprint-03-trade-database/migrations/003_create_customer_preferences.sql` - Database migration
2. `sprint-07-event-backbone/src/main/java/com/tradingplatform/domain/model/CustomerPreference.java`
3. `sprint-07-event-backbone/src/main/java/com/tradingplatform/domain/model/PlatformDefaults.java`
4. `sprint-07-event-backbone/src/main/java/com/tradingplatform/domain/dto/PreferenceRequest.java`
5. `sprint-07-event-backbone/src/main/java/com/tradingplatform/domain/dto/PreferenceResponse.java`
6. `sprint-07-event-backbone/src/main/java/com/tradingplatform/domain/dto/PlatformDefaultsResponse.java`
7. `sprint-07-event-backbone/src/main/java/com/tradingplatform/domain/exception/InvalidPreferenceException.java`
8. `sprint-07-event-backbone/src/main/java/com/tradingplatform/api/mappers/PreferenceMapper.java`
9. `sprint-07-event-backbone/src/main/java/com/tradingplatform/api/services/PreferenceService.java`
10. `sprint-07-event-backbone/src/main/java/com/tradingplatform/api/controllers/PreferenceController.java`
11. `sprint-07-event-backbone/src/test/java/com/tradingplatform/api/services/PreferenceServiceTest.java`
12. `sprint-07-event-backbone/src/test/java/com/tradingplatform/api/controllers/PreferenceControllerTest.java`

### Modified Files
1. `sprint-07-event-backbone/src/main/java/com/tradingplatform/domain/enums/ErrorCode.java` - Added INVALID_PREFERENCE
2. `sprint-07-event-backbone/src/main/java/com/tradingplatform/api/exception/GlobalExceptionHandler.java` - Added InvalidPreferenceException handler

## Security Considerations

1. **Account-Level Isolation**: Preferences are strictly isolated per account via JWT token claims
2. **Authorization**: AccountAccessAuthorizer enforces that users can only access their own preferences
3. **Input Validation**: Keys and values are validated before persistence
4. **Error Messages**: Exception messages don't leak system information
5. **JSON Injection**: ObjectMapper safely handles JSON serialization/deserialization

## Performance Considerations

1. **Database Indexing**: Index on account_id in customer_preferences table for fast lookups
2. **JSONB Storage**: PostgreSQL JSONB supports efficient querying and indexing
3. **Caching**: Preferences are not cached by default (can be added via Spring Cache)
4. **Lazy Defaults Merge**: Platform defaults are only queried when needed

## Next Steps / Future Enhancements

1. **Admin UI**: Create admin endpoints for managing platform defaults
2. **Audit Logging**: Add preference change audit trail
3. **Preference History**: Track historical preference changes for compliance
4. **Validation Framework**: Add stricter type-specific validation (timezones, numbers, etc.)
5. **Preference Groups**: Organize related preferences into groups (notifications, display, trading)
6. **Feature Flags**: Integrate with feature flags for selective preference availability
7. **Preference Export/Import**: Allow users to backup and restore their preferences
8. **Preference Analytics**: Track which preferences are most commonly modified

## Installation & Setup

### 1. Run Database Migration
```bash
cd sprint-03-trade-database
./apply.sh  # or equivalent migration runner
```

The migration creates:
- `customer_preferences` table
- `platform_defaults` table with initial defaults

### 2. Rebuild Sprint-07
```bash
cd sprint-07-event-backbone
mvn clean install -DskipTests  # Build with new code
mvn test  # Run all tests including new preference tests
```

### 3. Deploy & Test
```bash
mvn spring-boot:run  # Start the application

# Test endpoints with curl
curl -H "Authorization: Bearer <TOKEN>" http://localhost:8080/api/v1/accounts/123/preferences
```

## Verification Checklist

- [ ] Database migration runs successfully
- [ ] All 16 service unit tests pass
- [ ] All 10 controller integration tests pass
- [ ] GET /api/v1/accounts/{id}/preferences returns 200 with merged preferences
- [ ] PUT /api/v1/accounts/{id}/preferences updates and returns merged preferences
- [ ] DELETE /api/v1/accounts/{id}/preferences returns 204 (no content)
- [ ] GET /api/v1/preferences/defaults returns platform defaults
- [ ] Unauthorized access returns 403 Forbidden
- [ ] Invalid preferences return 422 Unprocessable Entity
- [ ] Preferences are properly persisted in PostgreSQL JSONB column
