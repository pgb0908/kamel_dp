# Apache Camel Integration Solution 소스코드 보고서

**프로젝트**: kamel_dp
**작성일**: 2026-02-23
**기술 스택**: Java 17 · Apache Camel 4.x · Quarkus · YAML DSL

---

## 1. 시스템 구조

Kaoto UI(온라인)에서 편집한 YAML 라우트를 Quarkus 런타임이 실행하는 **2-Plane 구조**입니다.

```
Kaoto UI (편집) ──▶ routes/*.camel.yaml ──▶ Quarkus Runtime (실행)
                                                    │
                                          Java Beans (비즈니스 로직)
```

---

## 2. 패키지 구성

| 패키지 | 클래스 수 | 역할 |
|--------|:---------:|------|
| `model/` | 2 | 입출력 DTO |
| `exception/` | 1 | 커스텀 예외 |
| `config/` | 1 | 인프라 설정 |
| (루트) — 기존 | 5 | 개별 주문 처리 Bean |
| (루트) — 신규 ★ | 4 | 배치 집계 Bean |

---

## 3. 클래스별 역할 요약

### 3-1. 데이터 모델

| 클래스 | 용도 |
|--------|------|
| `OrderRequest` | 주문 입력 DTO. `orderId·orderType·customerId·quantity` 필수 |
| `OrderResult` | 개별 주문 처리 결과 DTO. `status·processingNode·customerTier·processedAt` 포함 |

### 3-2. 예외 / 설정

| 클래스 | 용도 |
|--------|------|
| `OrderValidationException` | 유효성 실패 전용 예외 → `onException`이 잡아 HTTP 400 반환 |
| `IdempotentRepositoryConfig` | Caffeine 캐시 기반 중복 주문 방지. TTL 1일, 최대 10,000건 |

### 3-3. 개별 주문 처리 Bean (기존)

| 클래스 | 호출 메서드 | 역할 |
|--------|------------|------|
| `OrderValidator` | `validate()` | 필수값 검사 + orderType 정규화 + 헤더 설정 |
| `OrderEnricher` | `enrich()` | processingNode·receivedAt·customerTier 헤더 추가 |
| `OrderProcessor` | `processExpress()` `processStandard()` | 유형별 처리 → `OrderResult` 반환 |
| `OrderErrorHandler` | `createDeadLetterRecord()` | 재시도 소진 후 Dead Letter 레코드 생성 |
| `OrderFileParser` | `parseOrders()` | `inbox/` JSON 파일을 `List<OrderRequest>`로 파싱 (단건/배열 모두 처리) |

### 3-4. 배치 집계 Bean (신규 ★)

| 클래스 | 집계 단계 | correlationKey | 완료 조건 | 출력 |
|--------|:---------:|----------------|:---------:|------|
| `CustomerOrderAggregator` | AGG1 | `customerId` | size=10 OR 10초 | CustomerBatch Map |
| `TypeGroupAggregator` | AGG2 | `customerId-orderType` | 5초 | TypeSummary Map |
| `BatchReportCombiner` | AGG3 | `customerId` | 3초 | BatchReport Map (UUID batchId 포함) |
| `BatchFulfillmentHandler` | — | — | — | 분기 처리 + 보상 레코드 |

**BatchFulfillmentHandler 메서드**

| 메서드 | 역할 |
|--------|------|
| `prepareHeaders()` | BatchReport → choice 분기용 헤더 추출 |
| `processBulk()` | BULK 처리 결과 생성 (`totalQuantity > 100`) |
| `processVip()` | VIP 처리 결과 생성 (`customerTier == VIP`) |
| `processStandard()` | STANDARD 처리 결과 생성 |
| `compensate()` | 실패 시 보상 레코드 생성 |

---

## 4. 데이터 흐름

### 4-1. 개별 주문 파이프라인 (기존)

```
POST /api/orders          File inbox/
      │                       │
  validate               parseOrders
      │                       │
  seda:incomingOrders ◀───────┘
      │
  idempotentConsumer (중복 제거)
      │
  enrich (노드·등급 추가)
      │
  choice ┬─ EXPRESS  → processExpress  → processed-results/
         └─ STANDARD → processStandard → processed-results/
              (실패) → Dead Letter     → error/
```

### 4-2. 배치 집계 파이프라인 (신규 ★)

```
POST /api/orders/batch
      │
  validate → seda:orderBatch
      │
  AGG1 (customerId, size=10 OR 10초)
  → CustomerBatch { customerId, customerTier, orders:[] }
      │
  split(orders) + AGG2 (customerId-orderType, 5초)
  → TypeSummary { orderType, count, totalQuantity, orderIds }
      │
  AGG3 (customerId, 3초)
  → BatchReport { batchId(UUID), typeSummaries, totalQuantity }
      │
  choice ┬─ totalQuantity > 100  → BULK  → batch-reports/
         ├─ customerTier == VIP  → VIP   → batch-reports/
         └─ otherwise           → STD   → batch-reports/
              (실패) → compensate         → error/
```

---

## 5. 출력 파일 정리

| 디렉토리 | 파일명 패턴 | 생성 조건 |
|----------|-------------|-----------|
| `processed-results/` | `express-{orderId}-{ts}.json` | EXPRESS 주문 처리 완료 |
| `processed-results/` | `standard-{orderId}-{ts}.json` | STANDARD 주문 처리 완료 |
| `batch-reports/` | `bulk-{batchId}-{customerId}-{ts}.json` | totalQuantity > 100 |
| `batch-reports/` | `vip-{batchId}-{customerId}-{ts}.json` | customerTier == VIP |
| `batch-reports/` | `standard-{batchId}-{customerId}-{ts}.json` | 일반 배치 |
| `error/` | `dead-letter-{orderId}-{ts}.json` | 재시도 3회 소진 |
| `error/` | `compensation-{batchId}-{customerId}-{ts}.json` | 배치 처리 실패 |

---

## 6. 기존 대비 신규 추가 기능

| 기능 | 기존 | 신규 |
|------|:----:|:----:|
| Aggregate EIP | ✗ | ✓ |
| 다단계 연쇄 집계 (3단) | ✗ | ✓ |
| Size OR Timeout 완료 조건 | ✗ | ✓ |
| Split + Aggregate 중첩 | ✗ | ✓ |
| 복합 correlationKey | ✗ | ✓ |
| AggregationStrategy 구현 | ✗ | ✓ |
| doTry / doCatch (로컬) | ✗ | ✓ |
| Compensating Transaction | ✗ | ✓ |
| 3-way Choice 분기 | ✗ | ✓ |
| UUID 배치 ID 자동 생성 | ✗ | ✓ |
