# 송금 서비스 (WireBarley Backend)

계좌 관리, 입금, 출금 및 국내/해외 이체 기능 제공 프로젝트입니다.
---

## 사전 요구사항

### 필수 설치 항목

- **Java 17** 이상
- **Docker Desktop** (실행 중이어야 함)
    - MySQL 컨테이너를 위해 Docker가 실행 중이어야 합니다
    - [Docker Desktop 다운로드](https://www.docker.com/products/docker-desktop)

### Docker 실행 확인

```bash
# Docker가 실행 중인지 확인
docker ps
```

---

## 실행 방법 (Docker Compose)

### 1. 실행 명령어

```bash
# 전체 서비스 빌드 및 실행 (MySQL + API 애플리케이션)
docker-compose up -d

# 실행 상태 확인
docker-compose ps
```

### 2. 접속 정보 및 API 문서

- **Swagger UI (API 명세)**: [swagger](http://localhost:6060/swagger)
- **API 서버**: `http://localhost:6060`
- **MySQL**: `localhost:3307` (ID: wirebarley / PW: wirebarley1234)

### 3. 종료

```bash
# Docker 컨테이너 종료 및 애플리케이션 이미지 제거
docker-compose down --rmi all

# 볼륨까지 삭제 (데이터 초기화)
docker-compose down -v --rmi all
```

---

## API 명세 및 테스트 가이드

[swagger](http://localhost:6060/swagger) 또는 아래 시나리오별 curl 명령어로 모든 기능을 테스트할 수 있습니다.

### [v1] 기본 계좌 및 국내 거래 (필수 요구사항)

#### 1. 계좌 관리

- **계좌 생성 (테스트용 계좌 3개 생성)** (`POST /api/v1/accounts`)
  ```bash
  curl -X POST http://localhost:6060/api/v1/accounts \
    -H "Content-Type: application/json" \
    -d '{"accountNumber": "111-222-333", "ownerName": "김성수"}'
  
  curl -X POST http://localhost:6060/api/v1/accounts \
    -H "Content-Type: application/json" \
    -d '{"accountNumber": "100-200-300", "ownerName": "김성훈"}'
  
  curl -X POST http://localhost:6060/api/v1/accounts \
    -H "Content-Type: application/json" \
    -d '{"accountNumber": "012-345-678", "ownerName": "한윤상"}'
  ```
- **응답**
  ```json
  {"id":1,"accountNumber":"111-222-333", "ownerName":"김성수", "balance":0, "currency":"KRW"}
  {"id":2,"accountNumber":"100-200-300", "ownerName":"김성훈", "balance":0, "currency":"KRW"} 
  {"id":3,"accountNumber":"012-345-678", "ownerName":"한윤상", "balance":0, "currency":"KRW"}
  ```

- **계좌 삭제** (`DELETE /api/v1/accounts/{id}`)
  ```bash
  curl -X DELETE http://localhost:6060/api/v1/accounts/3
  ```
- **응답**: `204 No Content`

#### 2. 입금 및 출금

- **입금** (`POST /api/v1/accounts/{id}/deposit`)
  ```bash
  curl -X POST http://localhost:6060/api/v1/accounts/2/deposit \
    -H "Content-Type: application/json" \
    -d '{"amount": 1000000}'
  ```
- **응답**
  ```json
  {
    "accountId": 2,
    "balance": 1000000.0,
    "currency":"KRW"
  }
  ```

- **출금 (성공)** (`POST /api/v1/accounts/{id}/withdraw`)
  ```bash
  curl -X POST http://localhost:6060/api/v1/accounts/2/withdraw \
    -H "Content-Type: application/json" \
    -d '{"amount": 500000}'
  ```
- **응답**
  ```json
  {
    "accountId": 2,
    "balance": 500000.0,
    "currency":"KRW"
  }
  ```

- **출금 (한도 초과 실패)**
    - **제약**: **일 출금 한도 1,000,000원**
  ```bash
  curl -X POST http://localhost:6060/api/v1/accounts/2/withdraw \
    -H "Content-Type: application/json" \
    -d '{"amount": 600000}'
  ```
- **응답**
  ```json
  {
    "code": "LIMIT_EXCEEDED",
    "message": "일일 출금 한도를 초과했습니다",
    "timestamp": "2026-01-12T18:46:46.129251718"
  }
  ```

#### 3. 이체

- **계좌 간 이체 (성공)** (`POST /api/v1/transfers`)
  ```bash
  curl -X POST http://localhost:6060/api/v1/transfers \
    -H "Content-Type: application/json" \
    -d '{"fromAccountId": 2, "toAccountId": 1, "amount": 100000}'
  ```
- **응답**
  ```json
  {
    "fromAccountId": 2,
    "toAccountId": 1,
    "amount": 100000,
    "fee": 1000,
    "fromBalance": 399000.0,
    "toBalance": 100000.0
  }
  ```

- **이체 결과 확인 (잔액 조회)**
  ```bash
  # 보낸 이(2번) 잔액 확인
  curl -X GET http://localhost:6060/api/v1/accounts/2/balance
  # 응답: {"accountId":2,"balance":399000.0,"currency":"KRW"}

  # 받는 이(1번) 잔액 확인
  curl -X GET http://localhost:6060/api/v1/accounts/1/balance
  # 응답: {"accountId":1,"balance":100000.0,"currency":"KRW"}
  ```

- **이체 (한도 초과 실패)**
    - **제약**: **일 이체 한도 3,000,000원**
  ```bash
  curl -X POST http://localhost:6060/api/v1/transfers \
    -H "Content-Type: application/json" \
    -d '{"fromAccountId": 2, "toAccountId": 1, "amount": 3500000}'
  ```
- **응답**
  ```json
  {
    "code": "LIMIT_EXCEEDED",
    "message": "일일 이체 한도를 초과했습니다",
    "timestamp": "2026-01-12T18:56:46.129251718"
  }
  ```

#### 4. 거래 내역 조회

- **내역 조회** (`GET /api/v1/accounts/{id}/transactions`)
  ```bash
  curl -X GET "http://localhost:6060/api/v1/accounts/2/transactions?page=0&size=10"
  ```
- **응답**
  ```json
  {
    "items": [
      {
        "id": 3,
        "type": "TRANSFER_OUT",
        "amount": 100000.0,
        "fee": 1000.0,
        "balanceSnapshot": 399000.0,
        "relatedAccountId": 1,
        "createdAt": "2026-01-12T19:35:12"
      },
      {
        "id": 2,
        "type": "WITHDRAW",
        "amount": 500000.0,
        "fee": 0.0,
        "balanceSnapshot": 500000.0,
        "relatedAccountId": null,
        "createdAt": "2026-01-12T19:34:55"
      },
      {
        "id": 1,
        "type": "DEPOSIT", 
        "amount": 1000000.0,
        "fee": 0.0,
        "balanceSnapshot": 1000000.0,
        "relatedAccountId": null,
        "createdAt": "2026-01-12T19:34:50"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 3,
    "totalPages": 1
  }
  ```
- 최신순 정렬 및 페이지네이션 적용

#### 4. 조회

- **계좌 상세 조회** (`GET /api/v1/accounts/{accountId}`)
  ```bash
  curl -X GET http://localhost:6060/api/v1/accounts/1
  ```
- **응답**
  ```json
  {
    "id": 1,
    "accountNumber": "111-200-300",
    "ownerName": "김성훈",
    "balance": 100000.0,
    "currency": "KRW"
  }
  ```

- **잔액 조회** (`GET /api/v1/accounts/{accountId}/balance`)
  ```bash
  curl -X GET http://localhost:6060/api/v1/accounts/1/balance
  ```
- **응답**
  ```json
  {
    "accountId": 1,
    "balance": 100000.0,
    "currency": "KRW"
  }
  ```

---

### [v2] 추가구현 - 해외 송금 (다중 통화 및 자동 환전)

#### 1. 글로벌 계좌 관리

- **외화 계좌 생성 (USD)** (`POST /api/v2/global-accounts`)
  ```bash
  curl -X POST http://localhost:6060/api/v2/global-accounts \
    -H "Content-Type: application/json" \
    -d '{"accountNumber": "999-888-777", "ownerName": "John", "currency": "USD"}'
  ```
- **응답**
  ```json
  {
    "id": 4,
    "accountNumber": "999-888-777",
    "ownerName": "John",
    "balance": 0.0,
    "currency": "USD"
  }
  ```

- **글로벌 계좌 입금** (`POST /api/v2/global-accounts/{id}/deposit`)
  ```bash
  curl -X POST http://localhost:6060/api/v2/global-accounts/4/deposit \
    -H "Content-Type: application/json" \
    -d '{"amount": 1000.55}'
  ```
- **응답**
  ```json
  {
    "accountId": 4,
    "balance": 1000.55,
    "currency": "USD"
  }
  ```

- **글로벌 계좌 상세 조회** (`GET /api/v2/global-accounts/{accountId}`)
  ```bash
  curl -X GET http://localhost:6060/api/v2/global-accounts/4
  ```
- **응답**
  ```json
  {
    "id": 4,
    "accountNumber": "999-888-777",
    "ownerName": "John",
    "balance": 1000.55,
    "currency": "USD"
  }
  ```

- **글로벌 계좌 출금 (성공/한도 체크)**
    - **제약**: 일 출금 한도 **1,000,000원 (환율 적용 기준)**

    - **정상 출금**
  ```bash
  curl -X POST http://localhost:6060/api/v2/global-accounts/4/withdraw \
    -H "Content-Type: application/json" \
    -d '{"amount": 100.00}'
  ```
    - **응답**
  ```json
  {
    "accountId": 4,
    "balance": 900.55,
    "currency": "USD"
  }
  ```

    - **한도 초과 시**
  ```bash
  curl -X POST http://localhost:6060/api/v2/global-accounts/4/withdraw \
    -H "Content-Type: application/json" \
    -d '{"amount": 900.00}'
  ```
    - **응답 (422 Unprocessable Entity)**
  ```json
  {
    "code": "LIMIT_EXCEEDED",
    "message": "일일 출금 한도를 초과했습니다 (한도: 100만원)",
    "timestamp": "2026-01-12T18:58:00.123"
  }
  ```

#### 2. 글로벌 송금 시나리오 (KRW -> USD)

- **자동 환전 송금** (`POST /api/v2/global-transfers`)
    - **상황**: 2번 계좌(KRW)에서 4번 계좌(USD)로 100,000원 송금
  ```bash
  curl -X POST http://localhost:6060/api/v2/global-transfers \
    -H "Content-Type: application/json" \
    -d '{"fromAccountId": 2, "toAccountId": 4, "amount": 100000}'
  ```
- **응답**
  ```json
  {
    "fromAccountId": 2,
    "toAccountId": 4,
    "originalAmount": 100000,
    "convertedAmount": 68.25,
    "exchangeRate": 0.00068,
    "fee": 1000.0
  }
  ```

- **글로벌 이체 결과 확인 (잔액 조회)**
    - 보낸 이(2번) 잔액 확인 (100,000원 + 이체 수수료 1,000원 차감 = 총 101,000원 감소)
- ```bash
  curl -X GET http://localhost:6060/api/v1/accounts/2/balance
  ```
    - 응답
  ```json
  {"accountId":2,"balance":298000.0,"currency":"KRW"}
  ```
    - 받는 이(4번) 잔액 확인 (68.25 USD 입금)
  ```bash
  curl -X GET http://localhost:6060/api/v2/global-accounts/4/balance
  ```
    - 응답
  ```json
  {"accountId":4,"balance":968.80,"currency":"USD"}
  ```
- **이체 한도 초과 시나리오**
    - **상황**: 일 3,000,000원 한도 초과 (예: 3,000,001원 송금 시도)
  ```bash
  curl -X POST http://localhost:6060/api/v2/global-transfers \
    -H "Content-Type: application/json" \
    -d '{"fromAccountId": 2, "toAccountId": 4, "amount": 3000001}'
  ```
- **응답 (422 Unprocessable Entity)**
  ```json
  {
    "code": "LIMIT_EXCEEDED",
    "message": "일일 이체 한도를 초과했습니다 (한도: 300만원)",
    "timestamp": "2026-01-12T19:00:00.123"
  }
  ```

- **상세 정책**
    - **수수료 정책**:
        - **이체 수수료**: 송금액의 **1%** (보내는 사람 부담, KRW 출금 시 차감)
        - **환전 수수료**: 환전 금액의 **0.5%** (환전 금액에서 차감되어 받는 금액이 줄어듦)
    - **소수점**: 통화 규격에 따른 절상 (`USD`는 소수점 2자리까지 표시)
    - **이체 한도**: 일 **3,000,000원 (KRW 환산 기준)** 적용

#### 3. 글로벌 거래 내역 조회

- **내역 조회** (`GET /api/v2/global-accounts/4/transactions`)
  ```bash
  curl -X GET "http://localhost:6060/api/v2/global-accounts/4/transactions?page=0&size=10"
  ```
- **응답**: v1과 동일하게 최신순 정렬 및 페이지네이션된 JSON 반환

---

### [v3] 해외 송금 (멱등성 보장)

#### 1. 멱등 송금 (최초 요청)

- **송금 실행** (`POST /api/v3/global-transfers`)
  ```bash
  curl -X POST http://localhost:6060/api/v3/global-transfers \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
    -d '{"fromAccountId": 1, "toAccountId": 4, "amount": 10000}'
  ```
- **응답**: 정상 송금 결과 반환

#### 2. 멱등 송금 (중복 요청)

- **동일 키 재전송**
  ```bash
  curl -X POST http://localhost:6060/api/v3/global-transfers \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
    -d '{"fromAccountId": 1, "toAccountId": 4, "amount": 10000}'
  ```
- **응답**: 실제 송금 로직을 타지 않고, **이전 응답 값을 즉시 반환**

#### 3. 멱등성 충돌 (데이터 불일치)

- **동일 키 + 다른 데이터 전송**
  ```bash
  curl -X POST http://localhost:6060/api/v3/global-transfers \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
    -d '{"fromAccountId": 1, "toAccountId": 4, "amount": 50000}'
  ```
- **응답 (409 Conflict)**
  ```json
  {
    "code": "DUPLICATE_IDEMPOTENCY_KEY",
    "message": "이미 처리 중이거나 요청 데이터가 일치하지 않습니다."
  }
  ```

---

## 주요 기능

### 1. 멱등성 보장 (Idempotency)

- `Idempotency-Key` 헤더를 통한 중복 요청 방지
- TTL: 10분 (캐시 설정에 따름)
- 이벤트 기반 비동기 처리 (`AFTER_COMMIT`)로 트랜잭션 성공 시에만 기록

### 2. 환율 연동

- **Primary API**: ExchangeRate-API (무료 티어 활용)
- **Fallback API**: 보조 환율 API 연동
- **캐싱**: DB 캐싱으로 외부 API 장애 시에도 서비스 안정성 확보 (10분)
- 우선순위: Primary → Fallback → DB Cache → 1.0 (기본값)

### 3. 동시성 제어

- **낙관적 락 (Optimistic Locking)**: `@Version`을 통한 동시 수정 충돌 감지
- **비관적 락 (Pessimistic Locking)**: 이체 시 계좌 조회 시점에 `SELECT FOR UPDATE`로 락 획득
- **데드락 방지**: 계좌 ID 순서(낮은 ID 순)로 락을 획득하여 순환 대기 차단
- 계좌별 순차 처리 보장

### 4. 수수료 정책

- **올림 처리**: 회사에게 유리하도록 수수료 계산 시 소수점 올림 (`RoundingMode.UP`)
- **통화별 소수점**: KRW (0자리), USD/EUR/JPY (2자리) 등 통화 규격 준수

---

## 📊 데이터베이스

### Flyway 마이그레이션

```bash
# 마이그레이션 스크립트 위치
src/main/resources/db/migration/

# 주요 스크립트
V1__init_schema.sql              # 초기 스키마 (accounts, transactions)
V2__add_exchange_rates.sql       # 환율 캐시 테이블 추가
V3__add_idempotency_records.sql  # 멱등성 레코드 테이블 추가
```

### 테이블 구조

- `accounts`: 계좌 정보 (잔액, 통화, 버전 등)
- `transactions`: 거래 내역 (유형, 금액, 수수료, 연관 계좌 등)
- `idempotency_records`: 멱등성 키 및 응답 데이터 관리
- `exchange_rate_cache`: 외부 API 환율 데이터 캐싱

---

## 🏗 프로젝트 구조

```
wirebarley/
├── application/               # 애플리케이션 서비스 (Use Case, 버전별 AppService)
├── domain/                    # 도메인 모델 (Entity, 리포지토리 인터페이스, 도메인 로직)
├── infrastructure/            # 인프라 레이어 (DB 구현체, 외부 API 클라이언트, AOP, 설정)
├── presentation/              # 표현 레이어 (Controller, DTO)
├── common/                    # 공통 유틸리티 (이벤트, 예외, 유틸)
└── config/                    # 애플리케이션 설정 (Async, Cache, Swagger 등)
```

---

## 🛠 외부 라이브러리 및 오픈소스 사용 목적

- **Spring Cloud OpenFeign**: 외부 환율 API 간의 선언적 HTTP 통신 구축
- **Flyway**: DB 스키마 형상 관리 및 버전 관리
- **Testcontainers**: 실제 MySQL 환경 기반의 신뢰도 높은 통합 테스트 수행
- **Lombok**: 반복 코드 제거를 통한 핵심 로직 집중
- **Swagger**: 대화형 API 명세 및 테스트 환경 제공

---

## 🧪 테스트 실행

```bash
./gradlew test
```

- 60개 이상의 시나리오 테스트(V1~V3) 전면 검증 완료.
