# Customer Preferences Feature - Implementation Summary

## Overview
Implemented a comprehensive Customer Preferences system for the Trading Platform that allows individual traders to persist and manage their client-side settings and defaults. Preferences are stored at the account level using a nullable JSONB column and can override platform-wide defaults managed via Spring configuration.

## Architecture & Design

### Technology Stack
- **Database**: PostgreSQL with nullable JSONB column (no separate preference tables)
- **Persistence**: MyBatis (annotation-based SQL mapper)
- **Framework**: Spring Boot 3.4.0 with Spring Security + OAuth2
- **Configuration**: Spring @ConfigurationProperties for environment-specific defaults
- **Serialization**: Jackson ObjectMapper for JSON conversion
- **Testing**: JUnit 5, Mockito with test stub implementations

### Data Model

#### Domain Models
1. **CustomerPreference** - Immutable model representing per-account preferences
   - `accountId`: Long (PK, FK to accounts table)
   - `preferences`: Map<String, Object> (deserialized from JSONB)
   - `lastModified`: LocalDateTime

#### Database Schema
- **accounts** table (existing):
  - `preferences` (JSONB, nullable)
    - NULL = account inherits all Spring config defaults
    - Non-null = account has overrides, merged with defaults
  - Single column approach: simpler, more efficient, elegant NULL semantics

#### Platform Defaults Configuration
Defaults are stored in `application.properties` as Spring configuration:
```properties
trading-platform.preferences.defaults.NOTIFICATION_EMAIL=true
trading-platform.preferences.defaults.NOTIFICATION_TRADING_ALERTS=true
trading-platform.preferences.defaults.DISPLAY_THEME=light
trading-platform.preferences.defaults.DISPLAY_CURRENCY=USD
trading-platform.preferences.defaults.TRADING_DEFAULT_ORDER_TYPE=LIMIT
trading-platform.preferences.defaults.TRADING_MAX_ORDER_SIZE=10000
trading-platform.preferences.defaults.TIMEZONE=UTC
trading-platform.preferences.defaults.LANGUAGE=en
```

**Advantages of Configuration-Driven Defaults**:
- No database migration needed to change defaults
- Environment-specific (dev/staging/prod) via property profiles
- Deployment-friendly: change via environment variables or config files
- Faster: no database query to get defaults


## Feature Implementation

### 1. Configuration Layer (PreferenceDefaults)
**File**: `src/main/java/com/tradingplatform/config/PreferenceDefaults.java`

Spring @ConfigurationProperties component that loads defaults from application.properties:
- Prefix: `trading-platform.preferences.defaults.*`
- Loads at application startup via Spring autoconfiguration
- Provides `getDefaults()` and `getDefaultsCopy()` methods
- Defensive copying prevents external modification

**Key Benefit**: Centralized, environment-aware configuration without database queries

### 2. Database Layer (PreferenceMapper)
**File**: `src/main/java/com/tradingplatform/api/mappers/PreferenceMapper.java`

MyBatis mapper interface with operations on accounts.preferences JSONB column:
- `findPreferencesByAccountIdRaw(Long accountId)` - Direct SELECT from accounts.preferences
- `findPreferencesByAccountIdIncludingNull()` - Handles NULL preferences
- `getPreferencesForAccount(Long accountId)` - Returns Optional<Map>
- `updatePreferences(Long accountId, Map preferences)` - UPDATE accounts SET preferences
- `resetPreferences(Long accountId)` - UPDATE accounts SET preferences = NULL
- `accountExists(Long accountId)` - Verify account exists
- `parseJson(String json)` - Helper to deserialize JSONB

**Key Design**: Returns empty Map when preferences is NULL, signaling service layer to use config defaults

### 3. Service Interface (IPreferenceService)
**File**: `src/main/java/com/tradingplatform/api/services/IPreferenceService.java`

Service contract for preference management - allows easy testing with mock/stub implementations.

### 4. Service Layer (PreferenceService)
**File**: `src/main/java/com/tradingplatform/api/services/PreferenceService.java`

Business logic and preference management implementing IPreferenceService:

**Core Methods**:
- `getPreferencesForAccount(Long accountId)` - Returns merged preferences (account + Spring config defaults)
- `updateAccountPreferences(Long accountId, Map<String, Object> preferences)` - Partial update with validation
- `deleteAccountPreferences(Long accountId)` - Sets column to NULL (resets to defaults)
- `getPlatformDefaults()` - Returns Spring configuration defaults (no DB query)
- `validatePreferences(Map<String, Object> preferences)` - Validates preference keys/values

**Key Features**:
- **NULL-Aware Merge**: If preferences column is NULL, returns copy of Spring config defaults; if not NULL, merges account preferences with defaults (account overrides)
- **Partial Updates**: Only provided keys are updated, others retain their values
- **Validation**: Extensible validation framework for preference keys/values
- **JSON Serialization**: ObjectMapper handles Map ↔ JSON conversion
- **Account Verification**: All methods verify account exists via AccountService before operation

**Constructor Dependencies** (injected by Spring):
- `PreferenceMapper preferenceMapper`
- `AccountService accountService`
- `ObjectMapper objectMapper`
- `PreferenceDefaults preferenceDefaults`
### 5. REST API (PreferenceController)
**File**: `src/main/java/com/tradingplatform/api/controllers/PreferenceController.java`

RESTful endpoints for preference management:

| Endpoint | Method | Purpose | Authorization |
|----------|--------|---------|-----------------|
| `/api/v1/accounts/{id}/preferences` | GET | Retrieve merged preferences | Account-level |
| `/api/v1/accounts/{id}/preferences` | PUT | Update preferences (partial) | Account-level |
| `/api/v1/accounts/{id}/preferences` | DELETE | Reset to platform defaults | Account-level |
| `/api/v1/preferences/defaults` | GET | Get platform defaults | Public (no auth) |

**Response Models**:
- GET account preferences: `PreferenceResponse` (accountId, preferences Map, lastModified)
- PUT account preferences: `PreferenceResponse` (updated merged preferences)
- DELETE: 204 No Content (no response body)
- GET defaults: `PlatformDefaultsResponse` (defaults Map)

**Security**:
- Account-level endpoints use `AccountAccessAuthorizer` to enforce that users can only access their own account's preferences
- Platform defaults endpoint is public (informational)

### 6. Exception Handling
**File**: `src/main/java/com/tradingplatform/domain/exception/InvalidPreferenceException.java`

Custom exception for preference validation errors:
- Extends `DomainException`
- Error Code: `PREF-422` (Unprocessable Entity)
- Caught by `GlobalExceptionHandler` and mapped to 422 response

### 7. Data Transfer Objects (DTOs)

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

Comprehensive test coverage with 13 test cases:
- Platform defaults retrieval
- Preference retrieval with NULL handling (inherit defaults)
- Preference retrieval with partial overrides (merge behavior)
- Partial updates with validation
- Null and empty preference handling
- Account existence verification
- JSON serialization/deserialization

**Test Strategy**: 
- Uses test stub implementations (PreferenceDefaultsStub extending PreferenceDefaults) to avoid Mockito/ByteBuddy compatibility issues with Java 25
- PreferenceMapper is mocked (can be mocked in Java 25)
- Real AccountService instance created with mocked mappers
- lenient() wrapper on mock setup to allow unused stubs

**Test Execution**: All 13 tests PASSING ✓

### Integration Tests
**File**: `src/test/java/com/tradingplatform/api/controllers/PreferenceControllerTest.java`

8 integration test cases covering:
- GET /api/v1/accounts/{id}/preferences - success (merged preferences)
- GET /api/v1/accounts/{id}/preferences - authorization failure (403)
- PUT /api/v1/accounts/{id}/preferences - success with partial update
- PUT /api/v1/accounts/{id}/preferences - invalid preferences (422)
- PUT /api/v1/accounts/{id}/preferences - authorization failure (403)
- DELETE /api/v1/accounts/{id}/preferences - success (204 no content)
- DELETE /api/v1/accounts/{id}/preferences - authorization failure (403)
- GET /api/v1/preferences/defaults - success (200 with defaults)

**Test Strategy**:
- @WebMvcTest with MockMvc for testing controller layer
- Uses @SpyBean wrapping a test stub implementation (PreferenceServiceStub implementing IPreferenceService)
- @TestConfiguration provides the stub bean to Spring context
- AccountAccessAuthorizer is @MockBean (isolated from real authorization)

**Test Execution**: All 8 tests PASSING ✓

## Comprehensive Testing Guide

### 1. Unit Tests Only
```bash
cd sprint-07-event-backbone

# Run only service unit tests (13 tests)
mvn test -Dtest=PreferenceServiceTest

# Expected Output:
# [INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS
```

### 2. Integration Tests Only
```bash
cd sprint-07-event-backbone

# Run only controller integration tests (8 tests)
mvn test -Dtest=PreferenceControllerTest

# Expected Output:
# [INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS
```

### 3. All Preference Tests
```bash
cd sprint-07-event-backbone

# Run both test classes (21 tests total)
mvn test -Dtest=PreferenceServiceTest,PreferenceControllerTest

# Expected Output:
# [INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS
```

### 4. Full Project Test Suite
```bash
cd sprint-07-event-backbone

# Run all tests in the project (including preference tests)
mvn test

# Expected Output includes:
# [INFO] Tests run: 111 (or current total), Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS
```

### 5. Compilation Check
```bash
cd sprint-07-event-backbone

# Verify code compiles without errors
mvn clean test-compile

# Expected Output:
# [INFO] BUILD SUCCESS
```

### 6. Full Build and Install
```bash
cd sprint-07-event-backbone

# Clean build with all tests (no skip)
mvn clean install

# Expected Output:
# [INFO] BUILD SUCCESS
```

### 7. Run Application and Manual Testing

#### Start the application:
```bash
cd sprint-07-event-backbone
mvn spring-boot:run

# Application will start on http://localhost:8080
# Default OAuth2 configuration applies
```

#### Test with curl commands (requires valid JWT token):

**1. Get Platform Defaults (No authentication required)**
```bash
curl -X GET http://localhost:8080/api/v1/preferences/defaults \
  -H "Content-Type: application/json"

# Expected Response (200 OK):
{
  "defaults": {
    "NOTIFICATION_EMAIL": true,
    "NOTIFICATION_TRADING_ALERTS": true,
    "DISPLAY_THEME": "light",
    "DISPLAY_CURRENCY": "USD",
    "TRADING_DEFAULT_ORDER_TYPE": "LIMIT",
    "TRADING_MAX_ORDER_SIZE": 10000,
    "TIMEZONE": "UTC",
    "LANGUAGE": "en"
  }
}
```

**2. Get Account Preferences (First call - NULL in DB, returns defaults)**
```bash
curl -X GET http://localhost:8080/api/v1/accounts/123/preferences \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json"

# Expected Response (200 OK):
{
  "accountId": 123,
  "preferences": {
    "NOTIFICATION_EMAIL": true,
    "NOTIFICATION_TRADING_ALERTS": true,
    "DISPLAY_THEME": "light",
    "DISPLAY_CURRENCY": "USD",
    "TRADING_DEFAULT_ORDER_TYPE": "LIMIT",
    "TRADING_MAX_ORDER_SIZE": 10000,
    "TIMEZONE": "UTC",
    "LANGUAGE": "en"
  },
  "lastModified": "2026-09-16T10:00:00"
}
```

**3. Update Account Preferences (Partial update)**
```bash
curl -X PUT http://localhost:8080/api/v1/accounts/123/preferences \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "preferences": {
      "DISPLAY_THEME": "dark",
      "LANGUAGE": "fr",
      "TIMEZONE": "Europe/Paris"
    }
  }'

# Expected Response (200 OK):
{
  "accountId": 123,
  "preferences": {
    "NOTIFICATION_EMAIL": true,              # from defaults
    "NOTIFICATION_TRADING_ALERTS": true,     # from defaults
    "DISPLAY_THEME": "dark",                 # from update
    "DISPLAY_CURRENCY": "USD",               # from defaults
    "TRADING_DEFAULT_ORDER_TYPE": "LIMIT",   # from defaults
    "TRADING_MAX_ORDER_SIZE": 10000,         # from defaults
    "TIMEZONE": "Europe/Paris",              # from update
    "LANGUAGE": "fr"                         # from update
  },
  "lastModified": "2026-09-16T10:05:00"
}
```

**4. Get Account Preferences Again (After update)**
```bash
curl -X GET http://localhost:8080/api/v1/accounts/123/preferences \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json"

# Expected Response (200 OK):
# Same as step 3 response - preferences persist in database
```

**5. Update with Invalid Preferences (Empty request)**
```bash
curl -X PUT http://localhost:8080/api/v1/accounts/123/preferences \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{ "preferences": {} }'

# Expected Response (422 Unprocessable Entity):
{
  "error": "Invalid preferences",
  "message": "Preferences cannot be null or empty"
}
```

**6. Delete Account Preferences (Reset to defaults)**
```bash
curl -X DELETE http://localhost:8080/api/v1/accounts/123/preferences \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json"

# Expected Response (204 No Content):
# Empty body, preferences column set to NULL in database
```

**7. Verify Reset (Get preferences after delete)**
```bash
curl -X GET http://localhost:8080/api/v1/accounts/123/preferences \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json"

# Expected Response (200 OK):
# All preferences now show defaults again (preferences column is NULL)
{
  "accountId": 123,
  "preferences": {
    "NOTIFICATION_EMAIL": true,
    "NOTIFICATION_TRADING_ALERTS": true,
    "DISPLAY_THEME": "light",
    "DISPLAY_CURRENCY": "USD",
    "TRADING_DEFAULT_ORDER_TYPE": "LIMIT",
    "TRADING_MAX_ORDER_SIZE": 10000,
    "TIMEZONE": "UTC",
    "LANGUAGE": "en"
  },
  "lastModified": "2026-09-16T10:10:00"
}
```

**8. Unauthorized Access (without JWT or wrong account)**
```bash
# Without JWT
curl -X GET http://localhost:8080/api/v1/accounts/123/preferences \
  -H "Content-Type: application/json"

# Expected Response (401 Unauthorized):
# Spring Security rejects unauthenticated request

# With JWT for different account
curl -X GET http://localhost:8080/api/v1/accounts/456/preferences \
  -H "Authorization: Bearer <JWT_TOKEN_FOR_ACCOUNT_789>" \
  -H "Content-Type: application/json"

# Expected Response (403 Forbidden):
# AccountAccessAuthorizer rejects cross-account access
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

// Example: Validate numeric ranges
if (entry.getKey().equals("TRADING_MAX_ORDER_SIZE")) {
    Integer size = (Integer) entry.getValue();
    if (size <= 0 || size > 1000000) {
        throw new InvalidPreferenceException("Order size must be between 1 and 1000000");
    }
}
```

### 2. Preference Change Audit Logging
Add audit trail to track when preferences change:
```java
// In PreferenceService.updateAccountPreferences():
auditLog.log(AuditEvent.PREFERENCE_UPDATED, 
    accountId, 
    existingPrefs, 
    newPreferences);
```

### 3. Business Logic Integration
Use preferences in trading logic:
```java
// Example: Validate order against account preferences
public boolean isOrderSizeValid(Long accountId, int orderQuantity) {
    Map<String, Object> prefs = getPreferencesForAccount(accountId);
    Integer maxSize = (Integer) prefs.get("TRADING_MAX_ORDER_SIZE");
    return orderQuantity <= maxSize;
}

// Example: Apply user's default order type
public Order createOrderWithDefaults(Long accountId, OrderRequest request) {
    Map<String, Object> prefs = getPreferencesForAccount(accountId);
    String orderType = (String) prefs.getOrDefault("TRADING_DEFAULT_ORDER_TYPE", "LIMIT");
    // ... use orderType
}
```

### 4. Admin Preferences Management
Add admin endpoints for updating platform defaults without code changes:
```java
@PostMapping("/admin/preferences/defaults")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<PlatformDefaultsResponse> updatePlatformDefaults(
        @RequestBody Map<String, Object> defaults) {
    // Update application.properties or database-backed config
    preferenceDefaults.updateDefaults(defaults);
    return ResponseEntity.ok(new PlatformDefaultsResponse(preferenceDefaults.getDefaults()));
}
```

### 5. Preference Groups & Categories
Organize related preferences into groups:
```java
public enum PreferenceGroup {
    NOTIFICATIONS("Email & Alert Settings"),
    DISPLAY("UI Customization"),
    TRADING("Trading Defaults"),
    LOCALIZATION("Language & Timezone");
}

// Return grouped preferences
Map<PreferenceGroup, Map<String, Object>> getGroupedPreferences(Long accountId) {
    Map<String, Object> prefs = getPreferencesForAccount(accountId);
    return Stream.of(PreferenceGroup.values())
        .collect(Collectors.toMap(
            Function.identity(),
            group -> filterByGroup(prefs, group)
        ));
}
```

### 6. Preference Import/Export
Allow users to backup and restore preferences:
```java
// Export as JSON
public String exportPreferences(Long accountId) {
    Map<String, Object> prefs = getPreferencesForAccount(accountId);
    return objectMapper.writeValueAsString(prefs);
}

// Import from JSON
public void importPreferences(Long accountId, String json) {
    Map<String, Object> prefs = objectMapper.readValue(json, Map.class);
    updateAccountPreferences(accountId, prefs);
}
```

## Files Created/Modified

### New Files
1. `sprint-03-trade-database/migrations/003_create_customer_preferences.sql` - Database migration (adds nullable preferences JSONB column to accounts table)
2. `sprint-07-event-backbone/src/main/java/com/tradingplatform/config/PreferenceDefaults.java` - Spring @ConfigurationProperties component
3. `sprint-07-event-backbone/src/main/java/com/tradingplatform/domain/model/CustomerPreference.java` - Domain model
4. `sprint-07-event-backbone/src/main/java/com/tradingplatform/domain/dto/PreferenceRequest.java` - Request DTO
5. `sprint-07-event-backbone/src/main/java/com/tradingplatform/domain/dto/PreferenceResponse.java` - Response DTO
6. `sprint-07-event-backbone/src/main/java/com/tradingplatform/domain/dto/PlatformDefaultsResponse.java` - Defaults response DTO
7. `sprint-07-event-backbone/src/main/java/com/tradingplatform/domain/exception/InvalidPreferenceException.java` - Custom exception
8. `sprint-07-event-backbone/src/main/java/com/tradingplatform/api/mappers/PreferenceMapper.java` - MyBatis mapper
9. `sprint-07-event-backbone/src/main/java/com/tradingplatform/api/services/IPreferenceService.java` - Service interface
10. `sprint-07-event-backbone/src/main/java/com/tradingplatform/api/services/PreferenceService.java` - Service implementation
11. `sprint-07-event-backbone/src/main/java/com/tradingplatform/api/controllers/PreferenceController.java` - REST controller
12. `sprint-07-event-backbone/src/test/java/com/tradingplatform/api/services/PreferenceServiceTest.java` - Unit tests (13 tests)
13. `sprint-07-event-backbone/src/test/java/com/tradingplatform/api/controllers/PreferenceControllerTest.java` - Integration tests (8 tests)

### Modified Files
1. `sprint-07-event-backbone/src/main/resources/application.properties` - Added preference defaults configuration (8 key-value pairs)
2. `sprint-07-event-backbone/src/main/java/com/tradingplatform/domain/enums/ErrorCode.java` - Added INVALID_PREFERENCE code
3. `sprint-07-event-backbone/src/main/java/com/tradingplatform/api/exception/GlobalExceptionHandler.java` - Added InvalidPreferenceException handler

## Design Rationale: Why This Approach?

### Nullable JSONB Column vs. Separate Tables
**Chosen Approach**: Single nullable JSONB column in accounts table

**Advantages**:
- ✅ Simpler schema - no JOIN required, one table maintains relationship
- ✅ Elegant NULL semantics - NULL naturally means "use defaults"
- ✅ More efficient queries - single index scan vs. multi-table join
- ✅ Flexible schema - new preference keys added without migration
- ✅ No orphaned records - preferences automatically deleted with account (foreign key cascade)
- ✅ Fewer hot spots - one table vs. two reduces lock contention

**Trade-offs**:
- No separate audit table for platform defaults (use config files or separate audit table if needed)
- Schema queries require JSONB operators (but well-supported in PostgreSQL)

### Spring Configuration Properties vs. Database Table
**Chosen Approach**: application.properties with Spring @ConfigurationProperties

**Advantages**:
- ✅ Environment-aware - dev/staging/prod via property profiles
- ✅ Fast - no database query, loaded at startup
- ✅ Immutable defaults - prevents accidental modification in production
- ✅ Deployment-friendly - change via environment variables without migration
- ✅ Version control - defaults tracked in Git alongside code
- ✅ No bootstrap chicken-egg problem - available before database queries

**Trade-offs**:
- Requires restart to change defaults (can be mitigated with feature flags)
- Less dynamic than database-driven (by design for safety)

### Test Stub Strategy for Java 25 Compatibility
**Chosen Approach**: Inner class stub implementations instead of Mockito mocks

**Rationale**:
- Java 25 restricts ByteBuddy inline instrumentation (needed for Mockito mocking)
- Stub implementations extend actual classes to retain behavior while avoiding mock instrumentation
- @SpyBean wraps stub bean for partial mocking where needed (e.g., AccountAccessAuthorizer)
- Cleaner than trying to work around JVM security restrictions

**Trade-offs**:
- Stubs are less isolated than true mocks
- Additional code to write vs. mock declarations
- Benefit: works reliably in Java 25+, clear intent in test code

## Performance Considerations

1. **Database Indexing**: Index on accounts.preferences for JSONB queries (if doing JSONB queries)
2. **Query Plans**: 
   - Preference retrieval: Single table scan + optional index seek (O(1))
   - No joins: Eliminates join overhead completely
3. **Connection Pooling**: Relies on existing HikariCP pool configuration
4. **Config Loading**: PreferenceDefaults loaded once at startup, cached in Spring bean

## Security Considerations

1. **Account-Level Isolation**: Preferences strictly isolated per account via JWT claims
2. **Authorization**: AccountAccessAuthorizer enforces users can only access their own preferences
3. **Input Validation**: Keys and values validated before persistence via validatePreferences()
4. **Error Messages**: Exception messages don't leak sensitive system information
5. **JSON Injection**: ObjectMapper safely handles JSON serialization/deserialization (no eval)
6. **SQL Injection**: MyBatis parameterized queries prevent SQL injection
7. **JSONB Injection**: PostgreSQL JSONB functions prevent JSON manipulation attacks

## Troubleshooting

### Issue: Tests fail with "Mockito cannot mock this class"
**Solution**: Verify test uses stub implementations, not @MockBean on PreferenceService
```java
// ✓ Correct: Use stub with @SpyBean
@SpyBean
private IPreferenceService preferenceService;  // wrapped spy around stub

// ✗ Wrong: Direct @MockBean on concrete class
@MockBean
private PreferenceService preferenceService;  // can't mock in Java 25
```

### Issue: Preferences not persisting
**Solution**: Verify:
1. Database migration ran successfully: `SELECT column_name FROM information_schema.columns WHERE table_name = 'accounts' AND column_name = 'preferences';`
2. PreferenceMapper correctly configured in application.properties
3. @Transactional on PreferenceService methods
4. Spring transaction manager configured

### Issue: Platform defaults not loading
**Solution**: Verify:
1. application.properties contains all 8 trading-platform.preferences.defaults.* keys
2. PreferenceDefaults bean created: check Spring startup logs for "PreferenceDefaults" 
3. No override in test profiles (check application-test.properties)

### Issue: 403 Forbidden on account preferences
**Solution**: Verify:
1. JWT token claims contain correct accountId
2. AccountAccessAuthorizer.assertTokenCanAccess() receives same accountId as request parameter
3. Spring Security filter chain passes token to PreferenceController

## Next Steps / Future Enhancements

1. **Preference Change Notifications**: Emit events when preferences change
2. **Audit Logging**: Add PreferenceChangeAuditEvent to track all modifications
3. **Preference History**: Maintain historical preference changes (temporal tables)
4. **Admin Overrides**: Allow admins to set preferences for other accounts
5. **Preference Export/Import**: UI for backup/restore user preferences
6. **Preference Analytics**: Track which preferences are most commonly modified
7. **Feature Flags**: Integrate preference availability with feature flags
8. **Preference Groups**: Organize preferences into logical groups (UI improvement)
9. **Validation Framework**: Strengthen type-specific validation (timezones, numbers, etc.)
10. **Cache Layer**: Add Spring Cache for frequently accessed preferences (dev/staging only)

## Installation & Setup

### 1. Run Database Migration
```bash
cd sprint-03-trade-database
./apply.sh  # or equivalent migration runner

# Migration creates or updates:
# - ALTER TABLE accounts ADD COLUMN preferences JSONB (nullable)
# - NULL preferences allow account to inherit Spring config defaults
# - Non-null preferences contain account-specific overrides
```

### 2. Verify Spring Configuration
Check `sprint-07-event-backbone/src/main/resources/application.properties` contains:
```properties
trading-platform.preferences.defaults.NOTIFICATION_EMAIL=true
trading-platform.preferences.defaults.NOTIFICATION_TRADING_ALERTS=true
trading-platform.preferences.defaults.DISPLAY_THEME=light
trading-platform.preferences.defaults.DISPLAY_CURRENCY=USD
trading-platform.preferences.defaults.TRADING_DEFAULT_ORDER_TYPE=LIMIT
trading-platform.preferences.defaults.TRADING_MAX_ORDER_SIZE=10000
trading-platform.preferences.defaults.TIMEZONE=UTC
trading-platform.preferences.defaults.LANGUAGE=en
```

### 3. Rebuild Sprint-07
```bash
cd sprint-07-event-backbone

# Full clean build with tests
mvn clean install

# Expected output:
# [INFO] Tests run: 111 (or current total)
# [INFO] BUILD SUCCESS
```

### 4. Run Tests to Verify
```bash
# Run preference tests specifically
mvn test -Dtest=PreferenceServiceTest,PreferenceControllerTest

# Expected output:
# [INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS
```

### 5. Deploy & Start Application
```bash
mvn spring-boot:run

# Application starts on http://localhost:8080
# PreferenceDefaults bean loads configuration on startup
# PreferenceController endpoints become available
```

## Verification Checklist

### Build & Tests
- [ ] Database migration applies successfully
- [ ] All 13 PreferenceServiceTest unit tests pass
- [ ] All 8 PreferenceControllerTest integration tests pass
- [ ] Full test suite runs with no regressions
- [ ] `mvn clean install` completes with BUILD SUCCESS

### API Endpoints
- [ ] GET /api/v1/preferences/defaults returns 200 with platform defaults
- [ ] GET /api/v1/accounts/{id}/preferences returns 200 with merged preferences
- [ ] PUT /api/v1/accounts/{id}/preferences updates and returns merged preferences
- [ ] DELETE /api/v1/accounts/{id}/preferences returns 204 No Content

### Behavior Verification
- [ ] New account (NULL preferences) returns only platform defaults
- [ ] After PUT update, account preferences persisted in database
- [ ] Partial updates preserve existing account preferences
- [ ] After DELETE, account preferences reset to defaults (column = NULL)
- [ ] Platform defaults reflect all 8 configured keys from application.properties

### Authorization & Security
- [ ] Unauthorized access (no JWT) returns 401
- [ ] Cross-account access (wrong account in token) returns 403
- [ ] Account-level access enforced via AccountAccessAuthorizer
- [ ] Invalid preferences (empty Map) return 422 Unprocessable Entity
- [ ] Platform defaults endpoint accessible without authentication

### Database Verification
```bash
# Connect to PostgreSQL and verify:
SELECT account_id, preferences FROM accounts WHERE account_id = 123;

# Expected for new account:
# account_id | preferences
# -----------+-------------
#   123      | null

# After PUT update:
# account_id | preferences
# -----------+----------------------------------------
#   123      | {"DISPLAY_THEME": "dark", ...}

# After DELETE:
# account_id | preferences
# -----------+-------------
#   123      | null
```
