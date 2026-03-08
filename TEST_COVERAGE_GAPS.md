# Test Coverage Gap Analysis

## Current State: 3 test classes covering 3 out of ~15 testable source classes

| Source Class | Test Exists? | Coverage Level |
|---|---|---|
| `DefaultCyclesClient` | Yes | Good |
| `CyclesRequestBuilderService` | Yes | Good |
| Model DTOs (toMap/fromMap) | Yes | Good |
| **`CyclesLifecycleService`** | **No** | **None - CRITICAL** |
| **`CyclesAspect`** | **No** | **None - HIGH** |
| **`CyclesExpressionEvaluator`** | **No** | **None - HIGH** |
| **`CyclesValueResolutionService`** | **No** | **None - HIGH** |
| **`InMemoryCommitRetryEngine`** | **No** | **None - MEDIUM** |
| **`CyclesAutoConfiguration`** | **No** | **None - MEDIUM** |
| **`CyclesContextHolder`** | **No** | **None - LOW** |
| **`CyclesReservationContext`** | **No** | **None - LOW** |
| **`CyclesResponse`** | **No** | **None - LOW** |
| **`ValidationUtils`** | **No** | **None - LOW** |

## Estimated overall coverage: ~20-25%

## Gap Details

### 1. CyclesLifecycleService - CRITICAL

Core orchestrator with the most complex business logic and zero tests.

Missing scenarios:
- Happy path: reserve -> execute -> commit lifecycle
- DENY decision -> throws CyclesProtocolException
- ALLOW_WITH_CAPS decision -> proceeds with caps in context
- Dry-run mode (both ALLOW and DENY outcomes)
- Guarded method throws -> reservation released
- Commit fails with retryable error (5xx/transport) -> retry engine scheduled
- Commit fails with RESERVATION_FINALIZED/EXPIRED -> skips release
- Commit fails with non-retryable 4xx -> releases reservation
- resolveActualAmount: SpEL actual(), useEstimateIfActualNotProvided, missing actual
- resolveEstimateExpression: value() vs estimate(), both set throws, neither set throws
- Heartbeat scheduling and cancellation
- Context holder set/clear lifecycle
- Null reservationId after successful reservation

### 2. CyclesAspect - HIGH

- Nested @Cycles detection -> IllegalStateException
- Default actionKind/actionName from class/method names
- Custom actionKind/actionName from annotation
- Delegation to CyclesLifecycleService

### 3. CyclesExpressionEvaluator - HIGH

- SpEL evaluation with method parameter binding
- #result variable binding for actual-cost expressions
- Literal numeric expressions
- Null result -> IllegalArgumentException
- Negative result -> IllegalArgumentException

### 4. CyclesValueResolutionService - HIGH

- Priority: annotation -> config -> resolver
- Returns null when all sources empty
- Config field switch for all 6 fields
- Unknown field name

### 5. InMemoryCommitRetryEngine - MEDIUM

- Retry disabled -> no-op
- Successful retry
- Exponential backoff with max delay cap
- Max attempts exhaustion
- Non-retryable vs retryable errors

### 6. CyclesAutoConfiguration - MEDIUM

- Bean creation
- Missing baseUrl/apiKey validation
- ConditionalOnMissingBean behavior

### 7. CyclesResponse - LOW

- Factory methods
- Status classification boundary conditions
- getBodyAttributeAsString edge cases
- getErrorResponse parsing

### 8. CyclesContextHolder / CyclesReservationContext - LOW

- ThreadLocal lifecycle
- Context state accessors

### 9. ValidationUtils - LOW

- putIfNotBlank, requireNotBlank, resolve, hasText edge cases
