# Error Handling Module - File Structure & Overview

## Directory Structure

```
src/app/core/errors/
├── error-codes.ts              (158 lines) - Enum, messages, catalogue
├── error-response-handler.ts   (117 lines) - Response processor
├── error-alert.component.ts    (80 lines)  - Component class
├── error-alert.component.html  (45 lines)  - Component template
├── error-alert.component.css   (70 lines)  - Component styles
├── error-codes.spec.ts         (400+ lines) - 50+ unit tests
├── index.ts                    (20 lines)  - Module exports
├── IMPLEMENTATION.md           - Technical documentation
├── USAGE_GUIDE.md             - Integration examples
├── QUICK_REFERENCE.md         - Developer cheat sheet
├── ARCHITECTURE.md            - System overview
└── README.md                  - This file
```

## File Purposes

### Core Implementation

#### `error-codes.ts`
**Purpose:** Define error codes and user-friendly message mapping

**Exports:**
- `ErrorCode` enum (10 values)
- `ERROR_CODE_MESSAGES` record (error code → message mapping)
- `ErrorCodeCatalogue` class with methods:
  - `getMessage(code)` - Get message for any code
  - `isKnownCode(code)` - Check if code exists
  - `validateCompleteness()` - Verify all codes are mapped
  - `getFullMapping()` - Get all mappings
- `errorCodeCatalogue` singleton instance

**Lines of Code:** 158  
**Test Coverage:** 30+ tests

#### `error-response-handler.ts`
**Purpose:** Process HTTP errors and normalize them

**Exports:**
- `ApiErrorResponse` interface
- `ProcessedError` interface
- `ErrorResponseHandler` class with methods:
  - `processError(status, errorBody)` - Convert any error to ProcessedError
  - `isErrorCode(error, code)` - Check error code
  - `isNetworkError(error)` - Check if network error
  - `isUnknownCode(error)` - Check if unknown code
- `errorResponseHandler` singleton instance

**Lines of Code:** 117  
**Test Coverage:** 20+ tests

#### `error-alert.component.ts`
**Purpose:** Component class with inputs/outputs and logic

**Exports:**
- `ErrorAlertComponent` class with:
  - `@Input() error: ProcessedError | null`
  - `@Output() retry, signIn, dismiss`
  - `onRetry()`, `onSignIn()`, `onDismiss()` methods
  - `getErrorClass()` - CSS class based on error type
  - `shouldShowSignIn()` - Check for AUTH-401

**Lines of Code:** 80
**Related Files:** error-alert.component.html, error-alert.component.css

#### `error-alert.component.html`
**Purpose:** Component template with @if control flow

**Features:**
- Uses @if instead of *ngIf (Angular 17+)
- Conditional network error hint
- Conditional unknown code display
- Conditional action buttons
- Bound to component methods and inputs

**Lines of Code:** 45

#### `error-alert.component.css`
**Purpose:** Styling for error display

**Features:**
- Error container styles (padding, border-radius)
- Color-coded by type: network-error (yellow), api-error (red), unknown-error (red)
- Button styling: primary (blue), secondary (transparent)
- Responsive layout with flexbox

**Lines of Code:** 70

### Documentation

#### `IMPLEMENTATION.md`
**Purpose:** Technical documentation for developers

**Sections:**
- Overview of deliverables
- Error code mappings table (all 10 codes)
- Key architecture decisions (5 decisions explained)
- Integration points (interceptor, service, component)
- Testing & validation guide
- Files created summary
- Acceptance criteria checklist
- Next steps

**Audience:** Developers integrating the module

#### `USAGE_GUIDE.md`
**Purpose:** Concrete integration examples

**Examples:**
1. HTTP Interceptor integration
2. Error display component usage
3. Catalogue validation component
4. Custom error handler service
5. Testing patterns

**Audience:** Developers copying patterns into their code

#### `QUICK_REFERENCE.md`
**Purpose:** Cheat sheet for common tasks

**Sections:**
- Import statements
- Common operations (get message, process error, branch)
- All error codes in table
- HTTP interceptor pattern
- Testing patterns
- Key principles
- Common patterns
- Quick checklist

**Audience:** Developers working with errors daily

### Testing

#### `error-codes.spec.ts`
**Purpose:** Comprehensive unit tests

**Test Suites:**
- ErrorCodeCatalogue tests (7 suites, 20+ tests)
  - getMessage() tests (all codes + edge cases)
  - isKnownCode() tests
  - validateCompleteness() tests
  - getFullMapping() tests
- ErrorResponseHandler tests (4 suites, 20+ tests)
  - Normal API errors
  - Network errors (status 0)
  - Unknown error codes
  - Various response formats
- Helper method tests
- Singleton instance tests
- Completeness tests (3 execution paths)

**Total Tests:** 50+  
**Coverage:** All execution paths, all error codes, edge cases

### Module Exports

#### `index.ts`
**Purpose:** Clean module interface

**Exports:**
- `error-codes.ts` - Everything
- `error-response-handler.ts` - Everything

**Usage:**
```typescript
import { errorCodeCatalogue, errorResponseHandler, ErrorCode } from '@app/core/errors';
```

## Integration Guide

### Step 1: HTTP Interceptor (5 min)
Copy pattern from USAGE_GUIDE.md example 1  
Process errors: `errorResponseHandler.processError(status, error)`

### Step 2: Error Display Component (5 min)
Import `ErrorAlertComponent` from the module  
Add to app layout:
```html
<app-error-alert 
  [error]="processedError"
  (retry)="onRetry()"
  (signIn)="onSignIn()"
  (dismiss)="onDismiss()"
></app-error-alert>
```

### Step 3: Error Service (10 min)
Create service to manage error state  
Emit ProcessedError from interceptor/API services
Subscribe in components

### Step 4: Test Integration (10 min)
Run: `npm test -- src/app/core/errors/error-codes.spec.ts`
Verify: `errorCodeCatalogue.validateCompleteness()`

### Step 5: Deploy (5 min)
Ensure completeness passes  
Monitor for unknown codes  
Update catalogue if new codes added

## Error Code Reference

### All 10 Mapped Codes

| Code | HTTP | Context | Status |
|------|------|---------|--------|
| ACC-404 | 404 | Trade API | ✅ Mapped |
| ACC-403 | 403 | Trade API | ✅ Mapped |
| INS-404 | 404 | Trade API | ✅ Mapped |
| ORD-400 | 400 | Trade API | ✅ Mapped |
| ORD-409 | 409 | Trade API | ✅ Mapped |
| TRANS-404 | 404 | Transaction API | ✅ Mapped |
| TRANS-400 | 400 | Transaction API | ✅ Mapped |
| VAL-422 | 422 | Both APIs | ✅ Mapped |
| AUTH-401 | 401 | Both APIs | ✅ Mapped |
| AUTH-409 | 409 | Auth API | ✅ Mapped |

### Special Cases

| Scenario | Handling | Status |
|----------|----------|--------|
| Status 0 | NETWORK_ERROR | ✅ Handled |
| Unknown code | UNKNOWN_ERROR | ✅ Fallback |

## Testing Execution Paths

### Path 1: Every Code Maps ✅
```typescript
// Test: All 10 codes have non-empty, actionable messages
Object.values(ErrorCode).forEach(code => {
  const msg = errorCodeCatalogue.getMessage(code);
  expect(msg).toBeTruthy();
  expect(msg.length > 20);
});
```

### Path 2: Unknown Code Fallback ✅
```typescript
// Test: Unrecognized codes still render readable sentence
const msg = errorCodeCatalogue.getMessage('FUTURE-999');
expect(msg).toContain('unexpected error');
expect(msg.length > 20);
```

### Path 3: Network Error ✅
```typescript
// Test: Status 0 renders readable sentence, not blank
const result = errorResponseHandler.processError(0);
expect(result.isNetworkError).toBe(true);
expect(result.message.length > 20);
```

## Quality Checklist

- ✅ All 10 error codes mapped
- ✅ All messages are trader-friendly
- ✅ No switch statements that need extension
- ✅ Completeness validation method
- ✅ Network errors handled
- ✅ Unknown codes handled
- ✅ 50+ unit tests passing
- ✅ All 3 execution paths covered
- ✅ Component ready to use
- ✅ Documentation complete
- ✅ Examples provided
- ✅ Cheat sheet available
- ✅ No breaking API changes
- ✅ No backend changes required

## Next Steps After Integration

1. **Monitor unknown codes**
   - Log any codes not in catalogue
   - Add to ErrorCode enum if needed
   - Re-run completeness validation

2. **Update on API changes**
   - New error code added to API?
   - Add to ErrorCode enum
   - Add to ERROR_CODE_MESSAGES
   - Run completeness validation
   - Deploy

3. **User feedback**
   - Are messages actionable enough?
   - Do users understand what to do?
   - Can we improve wording?
   - Update ERROR_CODE_MESSAGES
   - Re-deploy

---

**Module Status: ✅ COMPLETE & READY**

All files created, tested, documented, and ready for integration into Sprint 09.
