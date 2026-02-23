# Batch Order Aggregation & Fulfillment — 호출 흐름 및 검증 가이드

## 1. 전체 호출 흐름

```
POST /api/orders/batch  (OrderRequest JSON)
         │
         ▼
┌─────────────────────────────────────────────────────────────────────┐
│ Route 1: order-batch-entry-route                                    │
│  unmarshal JSON → OrderRequest                                      │
│  bean:orderValidator.validate()                                     │
│    └─ 실패 → onException → HTTP 400 반환 (즉시 종료)               │
│  to: seda:orderBatch  (waitForTaskToComplete=Never)                 │
│  → HTTP 202 Accepted 즉시 반환                                      │
└─────────────────────┬───────────────────────────────────────────────┘
                      │ (비동기)
                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│ Route 2: order-batch-agg1-route                                     │
│  from: seda:orderBatch  (concurrentConsumers=2)                     │
│                                                                     │
│  aggregate(correlationKey=customerId)                               │
│    strategy: customerOrderAggregator                                │
│    완료 조건: size=10  OR  timeout=10초                             │
│    출력: CustomerBatch Map                                          │
│      { customerId, customerTier, orders:[{orderId,orderType,...}] } │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
                      ▼ direct:agg2Input
┌─────────────────────────────────────────────────────────────────────┐
│ Route 3: order-batch-agg2-route                                     │
│  setHeader(customerId ← body[customerId])                           │
│  setHeader(customerTier ← body[customerTier])                       │
│                                                                     │
│  split(body[orders])  ── orders 리스트를 개별 order Map으로 분할   │
│    └─ setHeader(orderType ← body[orderType])                        │
│                                                                     │
│    aggregate(correlationKey=customerId-orderType)                   │
│      strategy: typeGroupAggregator                                  │
│      완료 조건: timeout=5초                                         │
│      출력: TypeSummary Map                                          │
│        { customerId, customerTier, orderType,                       │
│          count, totalQuantity, orderIds:[...] }                     │
└─────────────────────┬───────────────────────────────────────────────┘
                      │  (orderType별로 각각 emit)
                      ▼ direct:agg3Input
┌─────────────────────────────────────────────────────────────────────┐
│ Route 4: order-batch-agg3-route                                     │
│                                                                     │
│  aggregate(correlationKey=body[customerId])                         │
│    strategy: batchReportCombiner                                    │
│    완료 조건: timeout=3초                                           │
│    출력: BatchReport Map                                            │
│      { batchId(UUID), customerId, customerTier,                     │
│        typeSummaries:[TypeSummary,...],                             │
│        totalQuantity, totalOrders, createdAt }                      │
│                                                                     │
│  bean:batchFulfillmentHandler.prepareHeaders()                      │
│    → 헤더 설정: batchId, customerId, customerTier, totalQuantity    │
│                                                                     │
│  choice                                                             │
│    when: totalQuantity > 100  → direct:bulkFulfillment             │
│    when: customerTier == VIP  → direct:vipFulfillment              │
│    otherwise                  → direct:standardFulfillment         │
└──────────┬──────────────────┬──────────────────┬────────────────────┘
           │                  │                  │
           ▼                  ▼                  ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────────────┐
│ Route 5: BULK    │ │ Route 6: VIP     │ │ Route 7: STANDARD        │
│ doTry:           │ │ doTry:           │ │ doTry:                   │
│  processBulk()   │ │  processVip()    │ │  processStandard()       │
│  marshal JSON    │ │  marshal JSON    │ │  marshal JSON            │
│  file:batch-     │ │  file:batch-     │ │  file:batch-reports      │
│    reports       │ │    reports       │ │ doCatch(Exception):      │
│ doCatch:         │ │ doCatch:         │ │  compensate()            │
│  compensate()    │ │  compensate()    │ │  marshal JSON            │
│  file:error      │ │  file:error      │ │  file:error              │
└──────────────────┘ └──────────────────┘ └──────────────────────────┘
```

---

## 2. 핵심 데이터 변환 단계별 Body 형태

| 단계 | Body 타입 | 내용 |
|------|-----------|------|
| Route 1 진입 | `OrderRequest` | `{ orderId, orderType, customerId, quantity }` |
| AGG1 출력 | `Map<String,Object>` (CustomerBatch) | `{ customerId, customerTier, orders:[...] }` |
| split 후 각 항목 | `Map<String,Object>` (order Map) | `{ orderId, orderType, customerId, quantity }` |
| AGG2 출력 | `Map<String,Object>` (TypeSummary) | `{ customerId, customerTier, orderType, count, totalQuantity, orderIds:[...] }` |
| AGG3 출력 | `Map<String,Object>` (BatchReport) | `{ batchId, customerId, customerTier, typeSummaries:[...], totalQuantity, totalOrders, createdAt }` |
| fulfillment 출력 | `Map<String,Object>` (결과) | BatchReport + `{ fulfillmentType, status, processedAt, message }` |

---

## 3. customerTier 파생 규칙

`CustomerOrderAggregator.deriveCustomerTier()` 내부에서 결정됩니다.

| customerId 패턴 | customerTier |
|-----------------|-------------|
| `VIP-xxx` (VIP로 시작) | `VIP` |
| `GOLD-xxx` (GOLD로 시작) | `GOLD` |
| 그 외 | `STANDARD` |

---

## 4. fulfillment 분기 우선순위

```
totalQuantity > 100   →  BULK     (VIP여도 물량 우선)
customerTier == VIP   →  VIP
otherwise             →  STANDARD
```

---

## 5. 출력 파일 위치

| 경로 | 파일명 패턴 | 내용 |
|------|------------|------|
| `batch-reports/` | `bulk-{batchId}-{customerId}-{ts}.json` | BULK 처리 결과 |
| `batch-reports/` | `vip-{batchId}-{customerId}-{ts}.json` | VIP 처리 결과 |
| `batch-reports/` | `standard-{batchId}-{customerId}-{ts}.json` | STANDARD 처리 결과 |
| `error/` | `compensation-{batchId}-{customerId}-{ts}.json` | 처리 실패 보상 레코드 |

---

## 6. 테스트 절차

### 6-1. 사전 준비

```bash
# Quarkus dev 모드 실행
./mvnw quarkus:dev
```

서버 기동 확인 로그:
```
Listening on: http://0.0.0.0:8081
```

출력 디렉토리가 없어도 Camel이 자동 생성하므로 별도 작업 불필요합니다.

---

### 6-2. 시나리오 A — STANDARD 경로

**조건**: 일반 고객 (`CUST-001`), totalQuantity ≤ 100

```bash
curl -s -X POST http://localhost:8081/api/orders/batch \
  -H "Content-Type: application/json" \
  -d '{"orderId":"B001","orderType":"EXPRESS","customerId":"CUST-001","quantity":5}'

curl -s -X POST http://localhost:8081/api/orders/batch \
  -H "Content-Type: application/json" \
  -d '{"orderId":"B002","orderType":"STANDARD","customerId":"CUST-001","quantity":10}'
```

**예상 응답** (각 요청 즉시):
```json
{"status":"accepted","orderId":"B001","customerId":"CUST-001","message":"Order received and queued for batch processing"}
```

**타임라인**:
- `0s` — 두 주문 모두 `seda:orderBatch` 진입
- `~10s` — AGG1 timeout 완료 → CustomerBatch `{CUST-001, STANDARD, orders:[B001,B002]}`
- `~15s` — AGG2 timeout(5s) 완료 → TypeSummary × 2개 (EXPRESS, STANDARD)
- `~18s` — AGG3 timeout(3s) 완료 → BatchReport → STANDARD 분기

**결과 확인**:
```bash
ls batch-reports/
cat batch-reports/standard-*-CUST-001-*.json
```

**예상 결과 JSON**:
```json
{
  "batchId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "customerId": "CUST-001",
  "customerTier": "STANDARD",
  "typeSummaries": [
    {"orderType": "EXPRESS", "count": 1, "totalQuantity": 5, ...},
    {"orderType": "STANDARD", "count": 1, "totalQuantity": 10, ...}
  ],
  "totalQuantity": 15,
  "totalOrders": 2,
  "fulfillmentType": "STANDARD",
  "status": "FULFILLED"
}
```

---

### 6-3. 시나리오 B — BULK 경로

**조건**: totalQuantity > 100 (VIP여도 BULK 우선)

```bash
curl -s -X POST http://localhost:8081/api/orders/batch \
  -H "Content-Type: application/json" \
  -d '{"orderId":"B010","orderType":"EXPRESS","customerId":"CUST-002","quantity":60}'

curl -s -X POST http://localhost:8081/api/orders/batch \
  -H "Content-Type: application/json" \
  -d '{"orderId":"B011","orderType":"STANDARD","customerId":"CUST-002","quantity":70}'
```

**타임라인**: 시나리오 A와 동일 (AGG1 10s → AGG2 5s → AGG3 3s)

**결과 확인**:
```bash
ls batch-reports/
cat batch-reports/bulk-*-CUST-002-*.json
```

**예상 결과**: `totalQuantity: 130`, `fulfillmentType: "BULK"`

---

### 6-4. 시나리오 C — VIP 경로

**조건**: customerId가 `VIP`로 시작, totalQuantity ≤ 100

```bash
curl -s -X POST http://localhost:8081/api/orders/batch \
  -H "Content-Type: application/json" \
  -d '{"orderId":"B020","orderType":"EXPRESS","customerId":"VIP-007","quantity":20}'

curl -s -X POST http://localhost:8081/api/orders/batch \
  -H "Content-Type: application/json" \
  -d '{"orderId":"B021","orderType":"STANDARD","customerId":"VIP-007","quantity":30}'
```

**결과 확인**:
```bash
ls batch-reports/
cat batch-reports/vip-*-VIP-007-*.json
```

**예상 결과**: `customerTier: "VIP"`, `fulfillmentType: "VIP"`, `totalQuantity: 50`

---

### 6-5. 시나리오 D — AGG1 size 완료 (10건 즉시 집계)

**조건**: 같은 customerId로 10건 연속 전송 → timeout(10s) 대기 없이 즉시 처리

```bash
for i in $(seq 1 10); do
  curl -s -X POST http://localhost:8081/api/orders/batch \
    -H "Content-Type: application/json" \
    -d "{\"orderId\":\"S00${i}\",\"orderType\":\"STANDARD\",\"customerId\":\"CUST-BULK\",\"quantity\":3}" &
done
wait
```

**예상**: 10건 도달 시점에 즉시 AGG1 완료 (10s 대기 없음)

---

### 6-6. 시나리오 E — 유효성 검사 실패

**조건**: 필수 필드 누락

```bash
# orderId 누락
curl -s -X POST http://localhost:8081/api/orders/batch \
  -H "Content-Type: application/json" \
  -d '{"orderType":"EXPRESS","customerId":"CUST-001","quantity":5}'

# 잘못된 orderType
curl -s -X POST http://localhost:8081/api/orders/batch \
  -H "Content-Type: application/json" \
  -d '{"orderId":"B099","orderType":"INVALID","customerId":"CUST-001","quantity":5}'
```

**예상 응답** (HTTP 400):
```json
{"error":"Batch validation failed","message":"..."}
```

---

### 6-7. 복합 시나리오 — 두 고객 동시 처리

서로 다른 customerId를 동시에 전송하면 AGG1이 각각 별도로 집계합니다.

```bash
# CUST-001 (STANDARD)
curl -s -X POST http://localhost:8081/api/orders/batch \
  -H "Content-Type: application/json" \
  -d '{"orderId":"M001","orderType":"EXPRESS","customerId":"CUST-001","quantity":10}' &

# VIP-007 (VIP)
curl -s -X POST http://localhost:8081/api/orders/batch \
  -H "Content-Type: application/json" \
  -d '{"orderId":"M002","orderType":"EXPRESS","customerId":"VIP-007","quantity":20}' &

wait
```

**예상**: 10초 후 `batch-reports/`에 두 파일 각각 생성

---

## 7. 로그로 진행 상황 추적

Quarkus 터미널에서 각 단계별 로그 키워드로 확인합니다:

```
# Route 1: 수신 확인
"Batch order B001 (customer=CUST-001) accepted"

# Route 2: AGG1 완료
"AGG1 complete: CustomerBatch for customerId=CUST-001, orders=2"

# Route 3: split 확인
"AGG2 split: orderId=B001, orderType=EXPRESS, qty=5"

# Route 3: AGG2 완료
"AGG2 complete: TypeSummary CUST-001-EXPRESS, count=1, totalQty=5"

# Route 4: AGG3 완료 + 분기
"AGG3 complete: BatchReport batchId=..., customerId=CUST-001, totalOrders=2, totalQty=15"
"Routing to STANDARD fulfillment: ..."

# Route 7: 파일 저장
"STANDARD fulfillment saved: standard-...-CUST-001-....json"
```

---

## 8. 타이밍 요약

```
 0s   → curl 전송 (202 즉시 반환)
10s   → AGG1 timeout: CustomerBatch 생성 (or 10건 도달 시 즉시)
15s   → AGG2 timeout: TypeSummary 생성 (orderType별)
18s   → AGG3 timeout: BatchReport 생성 → fulfillment 분기 → 파일 저장
```

총 소요 시간: **약 18초** (timeout 기반 집계 시)

---

## 9. 트러블슈팅

| 증상 | 원인 | 확인 방법 |
|------|------|-----------|
| `batch-reports/` 파일이 생성되지 않음 | AGG 타임아웃 미완료 | 18초 이상 대기 후 재확인 |
| HTTP 400 응답 | 유효성 검사 실패 | 응답 body의 `message` 필드 확인 |
| `error/` 에 compensation 파일 생성 | fulfillment 처리 중 예외 | 파일 내용의 `reason` 필드 확인 |
| 두 고객의 결과가 하나의 파일에 섞임 | customerId가 동일 | 요청의 customerId 값 재확인 |
| AGG1이 10s 후에도 완료 안 됨 | seda 큐 미수신 | 로그에서 `"AGG1: received order"` 확인 |
