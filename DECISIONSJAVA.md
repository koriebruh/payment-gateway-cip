# DECISIONS_JAVA.md — Architecture Decisions & Conventions

> Java 25 + Spring Boot 4 | Microservice | Fintech Grade
> Referenced by AGENT_CONTEXT.md

---

## ADR-001: DTO — Records Only

**Decision:** All DTOs use Java Record, never class with getters/setters.

**Why:** Immutable by default, concise, Java 25 idiomatic.

```java
public record PaymentRequest(
        @NotBlank String orderId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String channel,
        @NotBlank String paymentMethod
) {}
```

---

## ADR-002: Null Handling — Optional Always

**Decision:** Never return null. Use `Optional<T>` for nullable returns.

**Why:** Explicit contract, eliminates NullPointerException at call site.

```java
Optional<Transaction> findByOrderId(String orderId);
```

**Exception:** Internal private methods where null is impossible — document it.

---

## ADR-003: Exception Handling — BusinessException Hierarchy

**Decision:** All domain errors throw `BusinessException` subclasses. Never throw raw `RuntimeException`.

**Why:** Centralized error response, consistent HTTP status mapping.

```java
throw BusinessException.notFound("Transaction not found: " + orderId);
throw BusinessException.invalidChannel(channel);
throw BusinessException.duplicateRequest(idempotencyKey);
```

**Exception hierarchy:**
```
BusinessException
├── NotFoundException           → 404
├── InvalidRequestException     → 400
├── DuplicateRequestException   → 409
├── UnauthorizedException       → 401
└── ServiceUnavailableException → 503
```

---

## ADR-004: Dependency Injection — Constructor Only

**Decision:** Always constructor injection. Never `@Autowired` on field.

**Why:** Testable, immutable dependencies, explicit contracts.

```java
public class PaymentService {
    private final TransactionRepository repository;

    public PaymentService(TransactionRepository repository) {
        this.repository = repository;
    }
}
```

**Note:** `@Qualifier` on constructor param is acceptable when multiple beans exist.

---

## ADR-005: Logging — Structured Key=Value

**Decision:** Use `@Slf4j`. Always log in `key=value` format. Never log sensitive data.

**Why:** Machine-parseable, Jaeger/ELK compatible, compliance-safe.

```java
log.info("payment.processed id={} status={} traceId={}", tx.getId(), tx.getStatus(), traceId);
        log.warn("payment.rejected reason={} orderId={}", reason, orderId);
log.error("payment.failed orderId={} error={}", orderId, ex.getMessage());
```

**NEVER log:**
- JWT tokens / Bearer tokens
- Account numbers / Card numbers
- Passwords / secrets
- Full request body without masking

---

## ADR-006: Transactional Outbox Pattern

**Decision:** Never publish Kafka events directly inside a DB transaction. Always use Outbox table.

**Why:** Guarantees atomicity between DB write and event publish. Prevents lost events or phantom events.

```java
@Transactional
public void processPayment(PaymentRequest request) {
    var tx = transactionRepository.save(buildTransaction(request));

    outboxRepository.save(OutboxEvent.of(
            "payment.processed",
            tx.getId().toString(),
            serialize(buildPaymentProcessedEvent(tx))
    ));
    // Separate outbox publisher scheduler picks this up and publishes to Kafka
}
```

**Outbox table schema:**
```sql
CREATE TABLE outbox_events (
                               id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                               aggregate_type VARCHAR(100) NOT NULL,
                               aggregate_id   VARCHAR(255) NOT NULL,
                               event_type     VARCHAR(100) NOT NULL,
                               payload        JSONB        NOT NULL,
                               status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                               created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                               published_at   TIMESTAMPTZ,
                               retry_count    INT          NOT NULL DEFAULT 0
);
```

---

## ADR-007: Saga Pattern — Choreography Based

**Decision:** Use choreography-based Saga for distributed transactions. No orchestrator service, no 2PC.

**Why:** Loose coupling, each service owns its state, no single point of failure.

```
PAYMENT FLOW SAGA:

PaymentService        CoreBankingService     BillerService
     │                       │                     │
     │── payment.initiated ─►│                     │
     │                       │── debit.requested ─►│
     │◄─ debit.completed ────│                     │
     │                       │◄── bill.paid ────────│
     │── payment.completed ─►│                     │

COMPENSATING (on failure):
     │◄─ bill.failed ────────│  (after debit succeeded)
     │── debit.reversal ────►│  (compensating transaction)
     │── payment.failed ────►│
```

**Rules:**
- Each step publishes success OR failure event
- Each service listens to relevant events only
- Compensating action MUST be idempotent
- Saga state tracked via `outbox_events` + transaction status

---

## ADR-008: Dead Letter Queue (DLQ)

**Decision:** Every Kafka consumer topic has a corresponding DLQ topic. Failed messages never silently dropped.

**Why:** Observability, replayability, no silent data loss.

```java
@KafkaListener(topics = "payment.initiated")
public void handlePaymentInitiated(PaymentInitiatedEvent event) {
    try {
        paymentService.process(event);
    } catch (RetryableException ex) {
        throw ex; // Resilience4j retry handles this
    } catch (NonRetryableException ex) {
        dlqPublisher.send("payment.initiated.dlq", event, ex.getMessage());
        log.error("payment.dlq.sent orderId={} reason={}", event.orderId(), ex.getMessage());
    }
}
```

**DLQ naming convention:** `{original-topic}.dlq`

**DLQ message must contain:**
- Original message payload
- Failure reason + timestamp
- Retry count + service name

---

## ADR-009: Idempotency — DB Level

**Decision:** Idempotency enforced at DB level via unique constraint, not application level.

**Why:** Application-level check has race condition under concurrent requests.

```sql
ALTER TABLE transactions
    ADD CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key);
```

```java
public Mono<Transaction> savePending(PaymentRequest request, String idempotencyKey) {
    return transactionRepository.save(buildTransaction(request, idempotencyKey))
            .onErrorMap(DataIntegrityViolationException.class,
                    ex -> BusinessException.duplicateRequest(idempotencyKey));
}
```

---

## ADR-010: Stateless Service

**Decision:** Zero in-memory state between requests. All shared state in Redis or DB.

**Why:** Horizontal scalability, no sticky sessions, cloud-native.

```java
public Mono<Void> cacheTransaction(String key, Transaction tx) {
    return redisTemplate.opsForValue()
            .set(key, tx, Duration.ofHours(24));
}
```

---

## ADR-011: Circuit Breaker — Resilience4j

**Decision:** All external service calls wrapped with `@CircuitBreaker` + `@TimeLimiter`. Always define fallback.

**Why:** Prevent cascade failures, fail fast, protect downstream.

```java
@CircuitBreaker(name = "corebank", fallbackMethod = "coreBankFallback")
@TimeLimiter(name = "corebank")
public Mono<CoreBankingResponse> callCoreBank(String account, BigDecimal amount) {
    return coreBankingClient.debit(account, amount);
}

private Mono<CoreBankingResponse> coreBankFallback(String account, BigDecimal amount, Throwable ex) {
    log.warn("corebank.circuit-open account={} reason={}", account, ex.getMessage());
    return Mono.error(BusinessException.serviceUnavailable("CoreBanking"));
}
```

**Config in `application.yml`:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      corebank:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
  timelimiter:
    instances:
      corebank:
        timeout-duration: 3s
```

---

## ADR-012: Observability — Micrometer Observation API

**Decision:** Use `@Observed` for tracing. Never use deprecated `@Timed`.

**Why:** Spring Boot 4 unified observation API, compatible with OpenTelemetry + Jaeger.

```java
@Observed(name = "payment.process", contextualName = "processPayment")
public PaymentResponse processPayment(PaymentRequest request) { ... }
```

---

## ADR-013: Switch Expression — Pattern Matching Always

**Decision:** Use switch expression with pattern matching. No `instanceof` chains.

**Why:** Java 21+ idiomatic, exhaustive matching, readable.

```java
private String extractToken(Authentication auth) {
    return switch (auth) {
        case JwtAuthenticationToken jwt -> jwt.getToken().getTokenValue();
        case null, default             -> throw BusinessException.unauthorized();
    };
}
```

---

## ADR-014: String Formatting — .formatted() Always

**Decision:** Use `String.formatted()`. Never string concatenation or `String.format()`.

**Why:** Readable, consistent, Java 25 idiomatic.

```java
var message = "Payment failed orderId=%s reason=%s".formatted(orderId, reason);
```

---

## ADR-015: Reactive — R2DBC Non-Blocking Always

**Decision:** All DB operations via R2DBC reactive. Never `.block()` inside reactive pipeline.

**Why:** Non-blocking I/O, compatible with WebFlux, scalable under load.

```java
public Mono<Transaction> findByOrderId(String orderId) {
    return transactionRepository.findByOrderId(orderId)
            .switchIfEmpty(Mono.error(
                    BusinessException.notFound("Transaction: " + orderId)));
}
```

---

## ADR-016: N+1 Query Prevention — Batch Loading Always

**Decision:** Never query inside a loop. Always use batch fetch or `IN` clause.

**Why:** N+1 = N+1 round trips to DB. At 100 records = 101 queries. Kills performance at scale.

```java
// Batch fetch with IN clause
Flux<Transaction> findAllByOrderIdIn(List<String> orderIds);

// JOIN via @Query for related data
@Query("""
        SELECT t.*, u.name AS user_name
        FROM transactions t
        JOIN users u ON u.id = t.user_id
        WHERE t.status = :status
        """)
Flux<TransactionWithUser> findByStatusWithUser(String status);
```

**Rules:**
- Use `findAll...In(List<ID>)` for batch fetch
- For related data, use explicit JOIN — never lazy load
- Never `.flatMap(id -> repo.findById(id))` on a list

---

## ADR-017: Keyset Pagination — Never Offset for Large Data

**Decision:** Use keyset pagination (cursor-based) for all paginated queries. Never `OFFSET` on large tables.

**Why:** `OFFSET N` scans and discards N rows every time. Keyset is O(1) regardless of page depth.

```java
public record PageRequest(String lastSeenId, Instant lastSeenCreatedAt, int size) {}

@Query("""
        SELECT * FROM transactions
        WHERE (created_at, id) < (:lastCreatedAt, :lastId)
        ORDER BY created_at DESC, id DESC
        LIMIT :size
        """)
Flux<Transaction> findNextPage(Instant lastCreatedAt, String lastId, int size);

public record PageResponse<T>(List<T> data, String nextCursor, boolean hasMore) {}
```

**Cursor encoding — opaque to client:**
```java
private String encodeCursor(String id, Instant createdAt) {
    var raw = "%s|%s".formatted(id, createdAt.toString());
    return Base64.getEncoder().encodeToString(raw.getBytes());
}
```

---

## ADR-018: Pessimistic Lock — When to Use

**Decision:** Use pessimistic lock when concurrent modification is highly likely and conflict cost is high.

**When to use:**
- Financial debit/credit operations
- Inventory decrement
- Any read-modify-write on critical data where conflict is expected

```java
@Query("SELECT * FROM accounts WHERE id = :id FOR UPDATE")
Mono<Account> findByIdForUpdate(String id);

@Transactional
public Mono<Account> debit(String accountId, BigDecimal amount) {
    return findByIdForUpdate(accountId)
            .flatMap(account -> {
                if (account.balance().compareTo(amount) < 0) {
                    return Mono.error(BusinessException.insufficientBalance(accountId));
                }
                return accountRepository.save(account.debit(amount));
            });
}
```

**Rules:**
- Always set transaction timeout to prevent deadlock starvation
- Lock smallest scope possible — row level, not table level
- Always acquire locks in consistent order (account A before B) to prevent deadlock

---

## ADR-019: Optimistic Lock — When to Use

**Decision:** Use optimistic lock when concurrent modification is rare but must be detected.

**When to use:**
- User profile / config updates
- Low-contention data where conflicts are rare

```java
@Table("transactions")
public class Transaction {
    @Id UUID id;

    @Version
    Long version; // auto-incremented on every update
}

@Transactional
public Mono<Transaction> updateStatus(String id, TransactionStatus status) {
    return transactionRepository.findById(id)
            .flatMap(tx -> transactionRepository.save(tx.withStatus(status)))
            .onErrorMap(OptimisticLockingFailureException.class,
                    ex -> BusinessException.conflict(
                            "Transaction modified concurrently: " + id));
}
```

**Decision table:**
```
Scenario                         → Lock Type
────────────────────────────────────────────
Financial debit/credit           → Pessimistic
Inventory reservation            → Pessimistic
High concurrent writes same row  → Pessimistic
User profile / config update     → Optimistic
Low traffic, rare conflict       → Optimistic
```

---

## ADR-020: Projection — Never SELECT * for Lists

**Decision:** Always use projection for list queries. Never fetch full entity when only subset needed.

**Why:** Reduces data transfer, avoids loading unused JSONB/LOB columns, faster query plan.

```java
public record TransactionSummary(String id, String orderId, String status, BigDecimal amount) {}

@Query("""
        SELECT id, order_id, status, amount
        FROM transactions
        WHERE user_id = :userId
        """)
Flux<TransactionSummary> findSummariesByUserId(String userId);
```

**Rules:**
- List endpoints → projection (summary fields only)
- Detail endpoint → full entity acceptable
- Never load `payload`, `metadata`, JSONB columns in list queries

---

## ADR-021: Batch Insert/Update — Never One by One

**Decision:** Use batch operations for multiple records. Never loop with single saves.

**Why:** N individual inserts = N round trips. Batch = 1 round trip.

```java
// Batch save
public Mono<Void> saveOutboxEvents(List<OutboxEvent> events) {
    return outboxRepository.saveAll(events).then();
}

// Bulk update via @Modifying
@Modifying
@Query("""
        UPDATE outbox_events
        SET status = 'PUBLISHED', published_at = NOW()
        WHERE id IN (:ids)
        """)
Mono<Integer> markAsPublished(List<UUID> ids);
```

---

## ADR-022: Index Awareness — Always Consider Query Plan

**Decision:** Every query on non-PK column must have corresponding index.

**Why:** Full table scan on large tables = query timeout in production.

```sql
CREATE INDEX idx_transactions_order_id    ON transactions(order_id);
CREATE INDEX idx_transactions_user_status ON transactions(user_id, status);
CREATE INDEX idx_transactions_created_at  ON transactions(created_at DESC);

-- Partial index for outbox polling — only PENDING rows
CREATE INDEX idx_outbox_pending ON outbox_events(created_at)
    WHERE status = 'PENDING';
```

**Rules:**
- Every `WHERE` clause on non-PK column → needs index
- Composite index: highest cardinality column first
- Use partial index for status-based queries
- Never index low-cardinality columns alone (boolean, 2-value enum)

---

## ADR-023: Caching Strategy — Redis TTL Always

**Decision:** All Redis cache entries must have explicit TTL. Always evict on write.

**Why:** Prevents memory leak, bounds stale data, predictable behavior.

```java
private static final Duration CACHE_TTL = Duration.ofMinutes(15);
private static final String   KEY_PREFIX = "payment:tx:";

public Mono<Transaction> getOrLoad(String orderId, Supplier<Mono<Transaction>> loader) {
    var key = KEY_PREFIX + orderId;
    return redisTemplate.opsForValue()
            .<Transaction>get(key)
            .switchIfEmpty(
                    loader.get().flatMap(tx ->
                            redisTemplate.opsForValue()
                                    .set(key, tx, CACHE_TTL)
                                    .thenReturn(tx))
            );
}

public Mono<Boolean> evict(String orderId) {
    return redisTemplate.delete(KEY_PREFIX + orderId)
            .map(count -> count > 0);
}
```

**Cache key convention:** `{service}:{entity}:{id}`
```
payment:tx:550e8400-e29b-41d4-a716-446655440000
payment:account:ACC-123
```

**Rules:**
- Always evict on write/update — never serve stale after mutation
- TTL as safety net, not primary invalidation strategy

---

## ADR-024: API Versioning — URI Path Versioning

**Decision:** Version all public APIs via URI path. Never via header or query param.

**Why:** Explicit, cacheable, visible in logs and monitoring, easy to route via gateway.

```java
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentControllerV1 { ... }

@RestController
@RequestMapping("/api/v2/payments")
public class PaymentControllerV2 { ... }
```

**Rules:**
- Breaking change → new version (`v2`)
- Additive change (new optional field) → same version, backward compatible
- Deprecate old version with `Deprecation` response header
- Always maintain N-1 version minimum

---

## ADR-025: Validation — Bean Validation at Controller Boundary

**Decision:** Validate all incoming requests at controller boundary using Bean Validation. Never validate manually in service layer.

**Why:** Fail fast before business logic, consistent error format, declarative.

```java
public record PaymentRequest(
        @NotBlank(message = "orderId must not be blank")
        String orderId,

        @NotNull @Positive
        @DecimalMax(value = "999999999.99", message = "amount exceeds maximum")
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "^(MOBILE|WEB|API)$", message = "invalid channel")
        String channel
) {}

@PostMapping
public Mono<PaymentResponse> process(
        @Valid @RequestBody PaymentRequest request,
        @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
    return paymentService.processPayment(request, idempotencyKey);
}

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(WebExchangeBindException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> "%s: %s".formatted(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", errors.toString()));
    }
}
```

---

## QUICK REFERENCE

```
DTO              → Record only, with Bean Validation annotations
Null return      → Optional or throw BusinessException
Exception        → BusinessException hierarchy only
Injection        → Constructor only, no @Autowired
Logging          → @Slf4j, key=value, no sensitive data
Event publish    → Outbox pattern only
Dist. tx         → Saga choreography, no 2PC
Failed messages  → DLQ, never silent drop
Idempotency      → DB unique constraint, not app-level check
State            → Redis/DB, never in-memory
External calls   → @CircuitBreaker + @TimeLimiter + fallback always
Tracing          → @Observed, never @Timed
Pattern match    → Switch expression, no instanceof chain
String format    → .formatted(), no concatenation
DB access        → R2DBC reactive, never .block()
N+1 prevention   → Batch fetch / IN clause, never query in loop
Pagination       → Keyset cursor, never OFFSET on large tables
Pessimistic lock → Financial ops, high-contention (SELECT FOR UPDATE)
Optimistic lock  → Low-contention updates (@Version)
Projection       → Project fields only, never SELECT * for lists
Batch ops        → saveAll() / bulk @Modifying, never loop single saves
Index            → Every WHERE non-PK column needs index
Caching          → Redis with explicit TTL, evict on write
API version      → URI path /api/v1/, never header or query param
Validation       → @Valid at controller, Bean Validation on Record
Output format    → Code + Potential Issues + Future Improvements + Notes
```

---

## ADR-026: Code Generation Output Format

**Decision:** Every generated code must include analysis sections below the code.
Never generate code-only response without context.

**Why:** Prevents blind copy-paste, surfaces hidden risks, guides reviewer.

**Format:**

```
### Code
[generated code]

### ⚠️ Potential Issues
List any of the following if applicable:
- Concurrency / race condition risk
- Security concern (data exposure, auth gap)
- Partial failure scenario not handled
- Performance concern at scale

### 🔮 Future Improvements
List only if genuinely relevant:
- What to improve at 10x traffic
- Technical debt worth noting

### 📝 Notes
Any non-obvious decision made during generation,
or assumption taken about unclear requirements.
```

**Rules:**
- If no potential issues exist — write "None identified" not skip the section
- Future improvements only if genuinely relevant — skip if trivial
- Notes section only if assumption was made — skip otherwise
- Never add checklist — checklist is reviewer's job, not agent's output