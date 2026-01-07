# 송금 서비스 (Coding Test)

계좌 간 **입금/출금/이체** 및 **거래내역 조회** 기능을 제공하는 간단한 송금 서비스입니다.  
과제 요구사항에 맞춰 **Spring Boot + JPA(Hibernate)** 로 구현하며, **Docker Compose** 로 로컬 실행 환경을 제공합니다.

## 요구사항 요약
- 계좌 등록 / 삭제
- 입금
- 출금 (**일 한도: 1,000,000원**)
- 이체 (**수수료: 이체 금액의 1%**, **일 한도: 3,000,000원**)
- 거래내역 조회 (최신순)

---

## Tech Stack
- Java 17+
- Spring Boot, Spring Web
- Spring Data JPA (Hibernate)
- MySQL 8 (Docker)
- Flyway (DB migration)
- JUnit5
- Testcontainers (통합 테스트)

---

## Quick Start (Docker Compose)

### Prerequisites
- Docker / Docker Compose 설치

### Run
```bash
docker compose up -d --build
```

### Verify
- API Base URL: `http://localhost:8080`
- Health Check: `http://localhost:8080/actuator/health`

### Logs
```bash
docker compose logs -f app
docker compose logs -f mysql
```

### Stop
```bash
docker compose down
```

### Reset (DB 데이터까지 삭제)
```bash
docker compose down -v
```

---

## 환경 변수 / 설정
Docker Compose에서 앱 컨테이너에 아래 환경 변수를 주입합니다(예시).

- `SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/app?...`
- `SPRING_DATASOURCE_USERNAME=app`
- `SPRING_DATASOURCE_PASSWORD=app1234`
- `SPRING_PROFILES_ACTIVE=local`

> 로컬에서 직접 실행 시에도 동일한 값으로 설정하면 됩니다.

---

## API Spec

### 1) 계좌 등록
`POST /api/accounts`

**Request**
```json
{
  "accountNumber": "110-123-456789",
  "ownerName": "kim"
}
```

**Response (201)**
```json
{
  "accountId": 1,
  "accountNumber": "110-123-456789",
  "ownerName": "kim",
  "balance": 0
}
```

### 2) 계좌 삭제
`DELETE /api/accounts/{accountId}`

**Response (204)**

---

### 3) 입금
`POST /api/accounts/{accountId}/deposit`

**Request**
```json
{
  "amount": 50000
}
```

**Response (200)**
```json
{
  "accountId": 1,
  "balance": 50000
}
```

---

### 4) 출금 (일 한도 1,000,000원)
`POST /api/accounts/{accountId}/withdraw`

**Request**
```json
{
  "amount": 10000
}
```

**Response (200)**
```json
{
  "accountId": 1,
  "balance": 40000
}
```

---

### 5) 이체 (수수료 1%, 일 한도 3,000,000원)
`POST /api/transfers`

**Request**
```json
{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 100000
}
```

**정책**
- 수수료 = `amount / 100` (원 단위, 정수 나눗셈으로 1% 계산)
- 출금 계좌에서 `amount + fee` 만큼 차감
- 입금 계좌에는 `amount` 만큼 가산
- 이체 일 한도(3,000,000원)는 **amount 기준**으로 체크

**Response (200)**
```json
{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 100000,
  "fee": 1000,
  "fromBalance":  -,
  "toBalance":    -
}
```

---

### 6) 거래내역 조회 (최신순)
`GET /api/accounts/{accountId}/transactions?size=20&cursor=...`

**Response (200)**
```json
{
  "items": [
    {
      "transactionId": 10,
      "type": "TRANSFER_OUT",
      "amount": 100000,
      "fee": 1000,
      "fromAccountId": 1,
      "toAccountId": 2,
      "createdAt": "2026-01-05T20:10:11+09:00"
    }
  ],
  "nextCursor": "..."
}
```

> 최소 요구사항은 “최신순 정렬”이며, 페이징/커서는 확장 포인트로 제공합니다(구현 선택).

---

## 에러 응답 포맷
모든 에러는 아래 형태로 반환합니다.

```json
{
  "code": "LIMIT_EXCEEDED",
  "message": "daily transfer limit exceeded",
  "timestamp": "2026-01-05T20:10:11+09:00"
}
```

예시:
- `ACCOUNT_NOT_FOUND` (404)
- `INVALID_AMOUNT` (400)
- `INSUFFICIENT_BALANCE` (409)
- `LIMIT_EXCEEDED` (422)

---

## 동시성/정합성 고려
- 계좌 잔액 변경(입금/출금/이체)은 **트랜잭션**으로 처리합니다.
- 이체 시 `fromAccount` → `toAccount` 순서로 **행 잠금(PESSIMISTIC_WRITE / SELECT FOR UPDATE)** 을 걸어 레이스 컨디션을 방지합니다.
- 거래내역은 별도 테이블에 적재하여 감사/추적 가능하도록 설계합니다.

---

## 테스트
### Run
```bash
./gradlew test
```

- 통합 테스트는 Testcontainers(MySQL)를 사용합니다.
- 테스트 실행에도 Docker가 필요합니다.

---

## Docker Compose 구성
- `mysql`: MySQL 8, 볼륨으로 데이터 영구화
- `app`: Spring Boot 애플리케이션, MySQL 헬스체크 이후 기동
