# ERP Migration Router — UML Class Design

> **Document 3 of 4** · [Architecture](01-architecture.md) · [Database schema](02-database-schema.md) · [UML class design](03-uml-class-design.md) ·
>
> Java 25 · Spring Boot 4 · Root package `com.bardaghji.erpmigration`

Notation: `<<record>>` = Java record, `<<Entity>>` = JPA entity, `+` public, `-` private,
`$` static. Getters, constructors and Spring annotations are omitted unless they carry design meaning.

---

## 1. Maven modules and dependencies

```mermaid
flowchart TB
    PARENT["erp-migration-router (parent pom)"]
    CM["canonical-model<br/>library jar"]
    OA["order-adapter<br/>Spring Boot app"]
    RS["reconciliation-service<br/>Spring Boot app"]
    AV["auth-validator<br/>Spring Boot app"]
    ME["mock-ecc<br/>Spring Boot app"]
    MS["mock-s4<br/>Spring Boot app"]

    PARENT --- CM & OA & RS & AV & ME & MS
    OA -- "depends on" --> CM
    RS -- "depends on" --> CM
```

Two deliberate rules:

1. **Only `canonical-model` is shared.** It is the integration contract between the adapter (producer
   of canonical results) and reconciliation (consumer). Nothing else is shared — no "common utils" module.
2. **The mocks do not share DTOs with the adapter.** They represent external systems owned by someone
   else. If the adapter and the mocks used the same classes, a mapping bug could never show up as a
   contract mismatch — both sides would change together.

---

## 2. `canonical-model`

```mermaid
classDiagram
    class CanonicalOrder {
        <<record>>
        +String orderId
        +String customerId
        +LocalDate orderDate
        +BigDecimal totalAmount
        +String currency
        +OrderStatus status
        +List~CanonicalOrderItem~ items
    }
    class CanonicalOrderItem {
        <<record>>
        +int lineNumber
        +String sku
        +BigDecimal quantity
        +String unit
    }
    class OrderStatus {
        <<enumeration>>
        OPEN
        IN_PROGRESS
        COMPLETED
    }
    class CanonicalSerializer {
        -JsonMapper mapper
        +toJson(CanonicalOrder order) String
        +fromJson(String json) CanonicalOrder
        +sha256Hex(String json) String
    }
    class ErpSystem {
        <<enumeration>>
        ECC
        S4
    }
    class CallMode {
        <<enumeration>>
        PRIMARY
        SHADOW
    }
    class MigrationPhase {
        <<enumeration>>
        LEGACY
        SHADOW
        CUTOVER
    }
    class CallOutcome {
        <<enumeration>>
        SUCCESS
        NOT_FOUND
        UNMAPPABLE
        ERP_UNAVAILABLE
        REJECTED
    }

    CanonicalOrder "1" *-- "1..*" CanonicalOrderItem : items
    CanonicalOrder --> OrderStatus
    CanonicalSerializer ..> CanonicalOrder : serializes
```

| Class | Package | Design notes |
|---|---|---|
| `CanonicalOrder`, `CanonicalOrderItem` | `…canonical` | Immutable records. The compact constructor sorts `items` by `lineNumber` and wraps them in `List.copyOf`, so two logically equal orders are always built identically. |
| `OrderStatus` | `…canonical` | No `UNKNOWN` value: an unrecognized ERP status is a mapping failure (`UNMAPPABLE`), not a valid canonical state. |
| `CanonicalSerializer` | `…canonical` | Deterministic JSON: `ORDER_MAP_ENTRIES_BY_KEYS`, `SORT_PROPERTIES_ALPHABETICALLY`, `BigDecimal` written as plain string after `stripTrailingZeros()`. Deterministic output is what makes `payload_hash` comparison valid. |
| `ErpSystem`, `CallMode`, `MigrationPhase`, `CallOutcome` | `…contract` | Values written to `integration.erp_call` and read back by reconciliation. Shared so both services agree on the codes. |

---

## 3. `order-adapter`

### 3.1 Request handling and ERP strategy

```mermaid
classDiagram
    class OrderController {
        -OrderIntegrationService service
        +getOrder(String warehouseCode, String orderId, RequestHeaders headers) CanonicalOrder
    }
    class RequestContext {
        <<record>>
        +String correlationId
        +String warehouseCode
        +String orderId
        +MigrationPhase phase
        +CallMode mode
        +String clientId
        +from(RequestHeaders headers, String wh, String orderId, ErpSystem self) RequestContext$
    }
    class OrderIntegrationService {
        -ErpSourceAdapter adapter
        -ResilientErpExecutor executor
        -ErpCallRecorder recorder
        +handle(RequestContext ctx) CanonicalOrder
    }
    class ErpSourceAdapter {
        <<interface>>
        +system() ErpSystem
        +fetch(String orderId) ErpFetchResult
    }
    class EccSourceAdapter {
        -EccClient client
        -EccOrderMapper mapper
        +system() ErpSystem
        +fetch(String orderId) ErpFetchResult
    }
    class S4SourceAdapter {
        -S4Client client
        -S4OrderMapper mapper
        +system() ErpSystem
        +fetch(String orderId) ErpFetchResult
    }
    class ErpFetchResult {
        <<record>>
        +String rawPayload
        +CanonicalOrder order
    }
    class ResilientErpExecutor {
        -CircuitBreakerRegistry breakers
        -BulkheadRegistry bulkheads
        +execute(ErpSystem erp, CallMode mode, Supplier~T~ call) T
    }
    class EccClient {
        -RestClient restClient
        +getSalesOrder(String vbeln) EccRawResponse
    }
    class S4Client {
        -RestClient restClient
        +getSalesOrder(String salesOrder) S4RawResponse
    }

    OrderController --> OrderIntegrationService
    OrderController ..> RequestContext : creates
    OrderIntegrationService --> ErpSourceAdapter
    OrderIntegrationService --> ResilientErpExecutor
    OrderIntegrationService --> ErpCallRecorder
    ErpSourceAdapter <|.. EccSourceAdapter
    ErpSourceAdapter <|.. S4SourceAdapter
    ErpSourceAdapter ..> ErpFetchResult : returns
    EccSourceAdapter --> EccClient
    EccSourceAdapter --> EccOrderMapper
    S4SourceAdapter --> S4Client
    S4SourceAdapter --> S4OrderMapper
```

**Strategy pattern, selected by configuration.** Exactly one `ErpSourceAdapter` bean exists per
container:

```java
@Component
@ConditionalOnProperty(name = "adapter.erp-system", havingValue = "S4")
class S4SourceAdapter implements ErpSourceAdapter {  }
```

`adapter-ecc` runs with `adapter.erp-system=ECC`, `adapter-s4` with `S4`. The orchestration code
(`OrderIntegrationService`) never knows which ERP it talks to.

**`RequestContext.from(…)` validates the headers set by nginx** and rejects impossible combinations with
`400`: missing correlation ID, `X-Call-Mode: SHADOW` received by the ECC adapter, or `SHADOW` mode
with a phase other than `SHADOW`. These mirror the `ck_erp_call_shadow_consistency` database constraint,
so an inconsistent call fails at the door rather than at insert time.

**Orchestration** — the whole service method:

```java
public CanonicalOrder handle(RequestContext ctx) {
    long start = System.nanoTime();
    try {
        ErpFetchResult result = executor.execute(adapter.system(), ctx.mode(),
                () -> adapter.fetch(ctx.orderId()));
        recorder.recordSuccess(ctx, adapter.system(), result, elapsedMs(start));
        return result.order();
    } catch (IntegrationException ex) {
        recorder.recordFailure(ctx, adapter.system(), ex, elapsedMs(start));
        throw ex;                       // → GlobalExceptionHandler → Problem Details
    }
}
```

**`ResilientErpExecutor`** picks the breaker named `"<erp>-<mode>"` (`ecc-primary`, `s4-primary`,
`s4-shadow`) and, for `SHADOW` only, wraps the call in the `s4-shadow` bulkhead. It translates
Resilience4j exceptions into domain exceptions: `CallNotPermittedException` → `ErpUnavailableException`,
`BulkheadFullException` → `ShadowRejectedException`. Nothing outside this class imports Resilience4j.

### 3.2 ERP payloads and mapping

```mermaid
classDiagram
    class EccSalesOrder {
        <<record>>
        +String vbeln
        +String kunnr
        +String audat
        +String netwr
        +String waerk
        +String gbstk
        +List~EccSalesOrderItem~ items
    }
    class EccSalesOrderItem {
        <<record>>
        +String posnr
        +String matnr
        +String kwmeng
        +String vrkme
    }
    class S4SalesOrderResponse {
        <<record>>
        +S4SalesOrder d
    }
    class S4SalesOrder {
        <<record>>
        +String salesOrder
        +String soldToParty
        +String salesOrderDate
        +String totalNetAmount
        +String transactionCurrency
        +String overallSDProcessStatus
        +S4ItemCollection toItem
    }
    class S4ItemCollection {
        <<record>>
        +List~S4SalesOrderItem~ results
    }
    class S4SalesOrderItem {
        <<record>>
        +String salesOrderItem
        +String material
        +String requestedQuantity
        +String requestedQuantityUnit
    }
    class EccOrderMapper {
        -UomMappingService uom
        +toCanonical(EccSalesOrder src) CanonicalOrder
        -mapStatus(String gbstk) OrderStatus
    }
    class S4OrderMapper {
        -UomMappingService uom
        +toCanonical(S4SalesOrder src) CanonicalOrder
        -mapStatus(String status) OrderStatus
        -parseODataDate(String value) LocalDate
    }
    class SapFormats {
        <<utility>>
        +stripLeadingZeros(String value) String$
        +parseDecimal(String value) BigDecimal$
        +parseLineNumber(String value) int$
        +parseSapDate(String yyyyMMdd) LocalDate$
    }
    class UomMappingService {
        -UomMappingRepository repository
        -Cache~UomMappingId, String~ cache
        +toCanonical(ErpSystem erp, String sourceUnit) String
    }

    EccSalesOrder "1" *-- "1..*" EccSalesOrderItem
    S4SalesOrderResponse *-- S4SalesOrder
    S4SalesOrder *-- S4ItemCollection
    S4ItemCollection "1" *-- "1..*" S4SalesOrderItem
    EccOrderMapper ..> EccSalesOrder : reads
    S4OrderMapper ..> S4SalesOrder : reads
    EccOrderMapper ..> SapFormats
    S4OrderMapper ..> SapFormats
    EccOrderMapper --> UomMappingService
    S4OrderMapper --> UomMappingService
```

| Class | Notes |
|---|---|
| `EccSalesOrder` & items | Field names mapped with `@JsonProperty("VBELN")` etc. Everything is `String`, as SAP sends it; typing happens in the mapper, where failures can be reported precisely. |
| `S4SalesOrderResponse` / `S4ItemCollection` | Model the OData v2 envelope: the entity is wrapped in `d`, and the expanded navigation `to_Item` in `results`. |
| `EccOrderMapper`, `S4OrderMapper` | Pure functions of their input + the UoM table: no I/O except the cached lookup. Unit-tested with one test per row of the mapping table (architecture §9). Any parse failure throws `UnmappableOrderException` with the field name and raw value. |
| `S4OrderMapper.parseODataDate` | `"/Date(1789171200000)/"` → epoch millis → `LocalDate` in UTC. |
| `UomMappingService` | Caffeine cache, 60 s TTL. Unknown unit → `UnmappableOrderException("Unknown unit of measure 'KAR' for ERP S4")`. |

### 3.3 Persistence and error handling

```mermaid
classDiagram
    class ErpCall {
        <<Entity>>
        -Long id
        -String correlationId
        -ErpSystem erpSystem
        -CallMode callMode
        -MigrationPhase migrationPhase
        -String warehouseCode
        -String orderId
        -String clientId
        -CallOutcome outcome
        -String canonicalPayload
        -String payloadHash
        -String rawPayload
        -String errorCode
        -String errorMessage
        -int latencyMs
        -Instant createdAt
        +success(RequestContext ctx, ErpSystem erp, String json, String hash, String raw, int ms) ErpCall$
        +failure(RequestContext ctx, ErpSystem erp, IntegrationException ex, int ms) ErpCall$
    }
    class ErpCallRepository {
        <<interface>>
        JpaRepository of ErpCall, Long
    }
    class UomMapping {
        <<Entity>>
        -UomMappingId id
        -String canonicalUnit
        -String description
        -Instant updatedAt
    }
    class UomMappingId {
        <<record>>
        +ErpSystem erpSystem
        +String sourceUnit
    }
    class UomMappingRepository {
        <<interface>>
        JpaRepository of UomMapping, UomMappingId
    }
    class ErpCallRecorder {
        -ErpCallRepository repository
        -CanonicalSerializer serializer
        -MeterRegistry meters
        +recordSuccess(RequestContext ctx, ErpSystem erp, ErpFetchResult result, long latencyMs) void
        +recordFailure(RequestContext ctx, ErpSystem erp, IntegrationException ex, long latencyMs) void
    }
    class IntegrationException {
        <<abstract>>
        +outcome() CallOutcome*
        +rawPayload() String
    }
    class OrderNotFoundException
    class UnmappableOrderException
    class ErpUnavailableException
    class ShadowRejectedException
    class GlobalExceptionHandler {
        +handleIntegration(IntegrationException ex, HttpServletRequest req) ProblemDetail
        +handleBadHeaders(InvalidRequestContextException ex) ProblemDetail
    }

    ErpCallRepository ..> ErpCall : manages
    UomMappingRepository ..> UomMapping : manages
    UomMapping *-- UomMappingId
    ErpCallRecorder --> ErpCallRepository
    ErpCallRecorder ..> ErpCall : creates
    IntegrationException <|-- OrderNotFoundException
    IntegrationException <|-- UnmappableOrderException
    IntegrationException <|-- ErpUnavailableException
    IntegrationException <|-- ShadowRejectedException
    GlobalExceptionHandler ..> IntegrationException : handles
```

| Exception | `outcome()` | HTTP (Problem Details) |
|---|---|---|
| `OrderNotFoundException` | `NOT_FOUND` | 404 |
| `UnmappableOrderException` | `UNMAPPABLE` | 422 |
| `ErpUnavailableException` | `ERP_UNAVAILABLE` | 503 |
| `ShadowRejectedException` | `REJECTED` | 503 (response discarded by nginx anyway) |

- `IntegrationException` extends `RuntimeException`; `rawPayload()` carries the ERP response when one
  was received (e.g. unmappable payload), so the failure row still holds the evidence.
- **`ErpCallRecorder` is best-effort:** it catches every persistence exception, logs it with the
  correlation ID and increments `erp_call_record_failures_total`. A duplicate
  `(correlation_id, erp_system)` — an nginx upstream retry — is caught as `DataIntegrityViolationException`
  and ignored. Recording must never change what the WMS receives.
- `ErpCall` exposes **static factories** instead of a public constructor + setters, so a success row
  without a payload (or a failure row with one) cannot be built — the same rule as the
  `ck_erp_call_success_payload` DB constraint, enforced first in Java.
- Enum fields use `@Enumerated(EnumType.STRING)`; `canonicalPayload` / `rawPayload` use
  `@JdbcTypeCode(SqlTypes.JSON)` over a `String`.

### 3.4 Package layout

```
com.bardaghji.erpmigration.adapter
├── api            OrderController, RequestContext, GlobalExceptionHandler
├── application    OrderIntegrationService, ErpFetchResult, exceptions
├── erp
│   ├── ErpSourceAdapter
│   ├── ecc        EccSourceAdapter, EccClient, EccSalesOrder*, EccOrderMapper
│   └── s4         S4SourceAdapter, S4Client, S4SalesOrder*, S4OrderMapper
├── mapping        SapFormats, UomMappingService
├── resilience     ResilientErpExecutor
└── persistence    ErpCall, ErpCallRepository, ErpCallRecorder, UomMapping*, UomMappingRepository
```

Dependency direction: `api → application → erp / resilience / persistence`. `erp.ecc` and `erp.s4`
never reference each other (checked with an ArchUnit test).

---

## 4. `reconciliation-service`

### 4.1 Reconciliation engine

```mermaid
classDiagram
    class ReconciliationScheduler {
        -ReconciliationService service
        +runBatch() void
    }
    class ReconciliationService {
        -PendingCallReader reader
        -OrderComparator comparator
        -CanonicalSerializer serializer
        -ReconciliationResultRepository repository
        -TransactionTemplate tx
        +reconcileBatch() int
        ~reconcile(ErpCallView primary, Optional~ErpCallView~ shadow) ReconciliationResult
    }
    class PendingCallReader {
        -JdbcClient jdbc
        +findPendingPrimaries(Duration grace, int limit) List~ErpCallView~
        +findShadow(String correlationId) Optional~ErpCallView~
    }
    class ErpCallView {
        <<record>>
        +long id
        +String correlationId
        +String warehouseCode
        +String orderId
        +CallOutcome outcome
        +String canonicalJson
        +String payloadHash
        +String errorCode
        +Instant createdAt
        +succeeded() boolean
    }
    class OrderComparator {
        +compare(CanonicalOrder ecc, CanonicalOrder s4) List~FieldDiff~
        -compareHeader(CanonicalOrder ecc, CanonicalOrder s4, List~FieldDiff~ out) void
        -compareItems(List~CanonicalOrderItem~ ecc, List~CanonicalOrderItem~ s4, List~FieldDiff~ out) void
    }
    class FieldDiff {
        <<record>>
        +String fieldName
        +String fieldPath
        +DiscrepancyType type
        +String eccValue
        +String s4Value
    }
    class DiscrepancyType {
        <<enumeration>>
        VALUE_DIFF
        MISSING_IN_S4
        MISSING_IN_ECC
    }

    ReconciliationScheduler --> ReconciliationService
    ReconciliationService --> PendingCallReader
    ReconciliationService --> OrderComparator
    ReconciliationService --> ReconciliationResultRepository
    PendingCallReader ..> ErpCallView : returns
    OrderComparator ..> FieldDiff : produces
    FieldDiff --> DiscrepancyType
```

- **`PendingCallReader` uses `JdbcClient`, not JPA.** It reads a table this service does not own
  (`integration.erp_call`); mapping it as a JPA entity would pretend to ownership and invite writes.
  A read-only view record + plain SQL (Q1 and Q2 in the database document) makes the contract explicit.
- **One transaction per reconciled request**, via `TransactionTemplate`. A `@Transactional` method
  called from inside the same class would bypass the Spring proxy and silently run without its own
  transaction. Per-item transactions mean one bad row never rolls back the whole batch, and a
  unique-key violation (overlapping runs) only skips that item.
- **Decision order in `reconcile`** (same as the architecture sequence diagram): primary failed →
  `PRIMARY_ERROR`; no shadow → `MISSING_SHADOW`; shadow failed → `SHADOW_ERROR`; equal hashes →
  `MATCH`; otherwise compare → `MISMATCH`.
- **`OrderComparator.compareItems`** indexes both lists by `lineNumber` (`Map<Integer, CanonicalOrderItem>`),
  then walks the union of keys: key only on ECC side → `MISSING_IN_S4`, only on S/4 side →
  `MISSING_IN_ECC`, both → compare `sku`, `quantity` (`compareTo`, not `equals`, so `2` = `2.000`), `unit`.
  O(n + m) instead of nested loops.
- `ReconciliationScheduler.runBatch()` uses `@Scheduled(fixedDelay = 15 s)` — *fixed delay*, not
  *fixed rate*, so a slow batch never overlaps the next one on the same instance.

### 4.2 Results and readiness

```mermaid
classDiagram
    class ReconciliationResult {
        <<Entity>>
        -Long id
        -String correlationId
        -String warehouseCode
        -String orderId
        -long primaryCallId
        -Long shadowCallId
        -ReconciliationStatus status
        -int discrepancyCount
        -String errorSummary
        -Instant primaryRecordedAt
        -Instant reconciledAt
        -List~FieldDiscrepancy~ discrepancies
        +of(ErpCallView primary, ErpCallView shadow, ReconciliationStatus status, List~FieldDiff~ diffs) ReconciliationResult$
    }
    class FieldDiscrepancy {
        <<Entity>>
        -Long id
        -ReconciliationResult result
        -String fieldName
        -String fieldPath
        -DiscrepancyType type
        -String eccValue
        -String s4Value
    }
    class ReconciliationStatus {
        <<enumeration>>
        MATCH
        MISMATCH
        SHADOW_ERROR
        MISSING_SHADOW
        PRIMARY_ERROR
    }
    class ReconciliationResultRepository {
        <<interface>>
        +findReadinessRow(String warehouseCode) Optional~ReadinessRow~
        +findTopDiscrepancies(String warehouseCode, int limit) List~FieldCount~
        +findByWarehouseAndStatus(String wh, ReconciliationStatus s, Pageable p) Page~ReconciliationResult~
    }
    class ReadinessRow {
        <<record>>
        +String warehouseCode
        +long sampleSize
        +long matches
        +BigDecimal matchRatePct
        +Instant lastTrafficAt
    }
    class FieldCount {
        <<record>>
        +String field
        +long occurrences
    }
    class ReadinessService {
        -ReconciliationResultRepository repository
        -CutoverCriteria criteria
        +readiness(String warehouseCode) WarehouseReadiness
    }
    class CutoverCriteria {
        <<record>>
        +int minSamples
        +BigDecimal minMatchRatePct
    }
    class WarehouseReadiness {
        <<record>>
        +String warehouseCode
        +int windowDays
        +long sampleSize
        +BigDecimal matchRatePct
        +Verdict verdict
        +List~FieldCount~ topDiscrepancies
    }
    class Verdict {
        <<enumeration>>
        READY
        NOT_READY
    }
    class ReconciliationController {
        -ReadinessService readinessService
        -ReconciliationResultRepository repository
        +readiness(String warehouse) WarehouseReadiness
        +results(String warehouse, ReconciliationStatus status, Pageable page) Page~ResultDto~
    }

    ReconciliationResult "1" *-- "0..*" FieldDiscrepancy : discrepancies
    ReconciliationResult --> ReconciliationStatus
    ReconciliationResultRepository ..> ReconciliationResult : manages
    ReconciliationResultRepository ..> ReadinessRow : returns
    ReadinessService --> ReconciliationResultRepository
    ReadinessService --> CutoverCriteria
    ReadinessService ..> WarehouseReadiness : builds
    WarehouseReadiness --> Verdict
    ReconciliationController --> ReadinessService
```

- `ReconciliationResult` ↔ `FieldDiscrepancy`: `@OneToMany(mappedBy = "result", cascade = ALL, orphanRemoval = true)`
  on the parent, `@ManyToOne(fetch = LAZY)` on the child. The `of(…)` factory sets
  `discrepancyCount = diffs.size()` itself, so the count can never disagree with the list
  (again mirroring a DB `CHECK`).
- `findReadinessRow` is a **native query on `v_warehouse_readiness`** mapped to the `ReadinessRow`
  record; `findTopDiscrepancies` is query Q3 of the database document.
- `CutoverCriteria` is a `@ConfigurationProperties("reconciliation.cutover-criteria")` record
  (`min-samples: 500`, `min-match-rate-pct: 99.5`), validated at start-up with `@Validated`.
- `ReadinessService.readiness(…)`: `READY` ⇔ `sampleSize ≥ minSamples` **and**
  `matchRatePct ≥ minMatchRatePct` (compared with `BigDecimal.compareTo`). Unknown warehouse
  (no rows) → `WarehouseNotFoundException` → `404`.
- `results(…)` returns `ResultDto` records, never entities, to avoid lazy-loading and exposing JPA
  internals through the API.

---

## 5. `auth-validator`

```mermaid
classDiagram
    class SecurityConfig {
        +securityFilterChain(HttpSecurity http) SecurityFilterChain
        +jwtDecoder(OAuth2ResourceServerProperties props) JwtDecoder
    }
    class AudienceValidator {
        -String requiredAudience
        +validate(Jwt token) OAuth2TokenValidatorResult
    }
    class OAuth2TokenValidator~Jwt~ {
        <<interface>>
        +validate(Jwt token) OAuth2TokenValidatorResult
    }
    class ValidationController {
        +validate(JwtAuthenticationToken auth, String requiredScope) ResponseEntity~Void~
    }

    OAuth2TokenValidator <|.. AudienceValidator
    SecurityConfig ..> AudienceValidator : registers
```

| Case | Handled by | Response to nginx |
|---|---|---|
| No / malformed / expired / badly signed token | Spring Security filter chain (`BearerTokenAuthenticationEntryPoint`) — controller never reached | `401` |
| Wrong audience or issuer | `JwtDecoder` validators (issuer + `AudienceValidator`) | `401` |
| Valid token, scope in `X-Required-Scope` missing from `scope` claim | `ValidationController` | `403` |
| Valid token with scope | `ValidationController` | `204` + `X-Client-Id` (from `azp` claim) |

`JwtDecoder` is built from Keycloak's **JWKS URI on the internal hostname**
(`http://keycloak:8080/realms/erp-migration/protocol/openid-connect/certs`) and validates the issuer
against the **external hostname** (`http://localhost:8180/realms/erp-migration`). Clients obtain tokens
through `localhost:8180`, so that is the `iss` claim; building the decoder from an issuer URI with the
internal hostname would reject every token with an issuer mismatch. The JWKS is fetched once and cached,
so validating a request needs no network call to Keycloak. `X-Required-Scope` is trusted because only nginx can reach
this service.

---

## 6. ERP mocks

```mermaid
classDiagram
    class EccSalesOrderController {
        -EccSampleOrderFactory factory
        +getSalesOrder(String vbeln) ResponseEntity~Map~
    }
    class EccSampleOrderFactory {
        +build(String orderId) Map~String,Object~
    }
    class S4SalesOrderController {
        -S4SampleOrderFactory factory
        -ScenarioProperties scenarios
        +getSalesOrder(String salesOrder) ResponseEntity~Map~
    }
    class S4SampleOrderFactory {
        +build(String orderId, DiscrepancyScenario scenario) Map~String,Object~
    }
    class DiscrepancyScenario {
        <<enumeration>>
        NONE
        AMOUNT_OFF_BY_ONE_CENT
        MISSING_LINE
        UNKNOWN_UNIT
        +forOrderId(String orderId, boolean enabled) DiscrepancyScenario$
    }
    class ScenarioProperties {
        <<record>>
        +boolean enabled
    }

    EccSalesOrderController --> EccSampleOrderFactory
    S4SalesOrderController --> S4SampleOrderFactory
    S4SalesOrderController --> ScenarioProperties
    S4SampleOrderFactory ..> DiscrepancyScenario
```

| Mock | Endpoint | Shape |
|---|---|---|
| `mock-ecc` | `GET /sap/ecc/salesorders/{vbeln}` | Flat ECC JSON: `VBELN`, `KUNNR`, `AUDAT`, `NETWR`, `WAERK`, `GBSTK`, `ITEMS[]` (`POSNR`, `MATNR`, `KWMENG`, `VRKME`) |
| `mock-s4` | `GET /sap/opu/odata/sap/API_SALES_ORDER_SRV/A_SalesOrder('{id}')?$expand=to_Item` | OData v2 envelope `{ "d": { …, "to_Item": { "results": [ … ] } } }` |

- Both mocks build plain `Map` JSON on purpose: they are "someone else's system" and do not share or
  mirror the adapter's DTO classes.
- Both return `404` for order IDs starting with `99`, to exercise the adapter's `NOT_FOUND` path.
- `DiscrepancyScenario.forOrderId` implements the seeded-scenario table of the architecture document
  (§8.4): last digit `7` → amount +0.01, `8` → missing line, `9` → unit `KAR`; everything else `NONE`.
  With `mock.s4.scenarios.enabled=false` it always returns `NONE`.

---

## 7. Design patterns summary

| Pattern | Where | Why |
|---|---|---|
| Strategy | `ErpSourceAdapter` + `@ConditionalOnProperty` | One orchestration, pluggable ERP |
| Adapter / Mapper | `EccOrderMapper`, `S4OrderMapper` | Isolate each ERP's vocabulary from the canonical model |
| Canonical Data Model | `canonical-model` module | Consumers and reconciliation depend on one model, not on N ERP formats |
| Static factory methods | `ErpCall.success/failure`, `ReconciliationResult.of` | Make invalid states unconstructible |
| Circuit Breaker + Bulkhead | `ResilientErpExecutor` | Isolate shadow experiments from live S/4 traffic |
| Repository / read model | JPA repositories; `PendingCallReader` + `ErpCallView` | Own data via JPA, foreign data via explicit read-only SQL |
| Strangler Fig | nginx routing table | Migrate warehouse by warehouse behind a stable API |
| Parallel Run (shadow traffic) | nginx `mirror` + reconciliation | Prove equivalence on real traffic before cutover |
