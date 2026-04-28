# ProtectMyService Commerce Order System

스터디 부하 테스트 및 성능 개선 실습용 커머스 주문 시스템.

## 기술 스택

| 항목 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Database | PostgreSQL 15 |
| Messaging | RabbitMQ 3 |
| Build | Gradle 8.10 (Kotlin DSL) |
| Monitoring | Spring Actuator + Micrometer Prometheus |

## 프로젝트 구조

```
src/main/java/com/pms/order/
├── OrderApplication.java                  # 애플리케이션 진입점
│
├── domain/                                # 도메인별 Layered Architecture
│   ├── member/
│   │   ├── entity/Member.java
│   │   └── repository/MemberRepository.java
│   │
│   ├── category/
│   │   ├── entity/Category.java           # 셀프 참조 트리 구조
│   │   ├── repository/CategoryRepository.java
│   │   ├── service/CategoryService.java
│   │   └── controller/CategoryController.java
│   │
│   ├── product/
│   │   ├── entity/Product.java            # 재고 차감/복원 도메인 로직 포함
│   │   ├── entity/ProductStatus.java      # ON_SALE, SOLD_OUT, HIDDEN
│   │   ├── repository/ProductRepository.java  # 비관적 락(findByIdWithLock)
│   │   ├── service/ProductService.java
│   │   └── controller/ProductController.java
│   │
│   ├── cart/
│   │   ├── entity/Cart.java               # 회원당 1개 (1:1)
│   │   ├── entity/CartItem.java           # cart_id+product_id UNIQUE
│   │   ├── dto/AddCartItemRequest.java
│   │   ├── dto/UpdateCartItemRequest.java
│   │   ├── repository/CartRepository.java
│   │   ├── repository/CartItemRepository.java
│   │   ├── service/CartService.java       # 중복 상품 수량 합산 로직
│   │   └── controller/CartController.java
│   │
│   ├── order/
│   │   ├── entity/Order.java
│   │   ├── entity/OrderItem.java          # 주문 시점 가격 스냅샷
│   │   ├── entity/OrderStatus.java        # 상태 전이 검증 로직 내장
│   │   ├── dto/CreateOrderRequest.java
│   │   ├── dto/OrderResponse.java
│   │   ├── dto/OrderListResponse.java
│   │   ├── repository/OrderRepository.java
│   │   ├── repository/OrderItemRepository.java
│   │   ├── service/OrderService.java      # 핵심 주문 생성/취소 비즈니스 로직
│   │   └── controller/OrderController.java
│   │
│   └── payment/
│       ├── entity/Payment.java            # READY, APPROVED, CANCELLED, FAILED
│       ├── entity/PaymentStatus.java
│       ├── dto/PaymentRequest.java
│       ├── dto/PaymentCancelRequest.java
│       ├── repository/PaymentRepository.java
│       ├── service/PaymentService.java    # 결제 요청/취소, 외부 시스템 연동
│       ├── controller/PaymentController.java
│       └── client/ExternalPaymentClient.java  # Mock (300-500ms 지연, 5% 실패)
│
├── event/                                 # 도메인 이벤트
│   ├── OrderCreatedEvent.java
│   ├── OrderPaidEvent.java
│   ├── OrderCancelledEvent.java
│   ├── OrderEventPublisher.java           # 인터페이스
│   └── OrderEventListener.java            # @TransactionalEventListener(AFTER_COMMIT)
│
├── infra/
│   └── rabbitmq/
│       └── RabbitMQEventPublisher.java    # RabbitMQ 기반 이벤트 발행 구현체
│
└── global/
    ├── common/BaseTimeEntity.java         # createdAt, updatedAt 자동 관리
    ├── config/
    │   ├── JpaConfig.java                 # @EnableJpaAuditing
    │   └── RabbitMQConfig.java            # Exchange, Queue, Binding 설정
    └── exception/
        ├── ErrorCode.java                 # 에러 코드 + HTTP 상태 매핑
        ├── BusinessException.java
        └── GlobalExceptionHandler.java    # @RestControllerAdvice
```

## 테스트 구조

```
src/test/java/com/pms/order/
├── support/                               # 테스트 인프라
│   ├── IntegrationTestBase.java           # 통합 테스트 베이스 클래스
│   ├── TestDataHelper.java                # JdbcTemplate 기반 테스트 데이터 생성
│   ├── TestFixture.java                   # 단위 테스트용 엔티티 팩토리
│   └── StubExternalPaymentClient.java     # 결정적 동작 보장하는 결제 Stub
│
├── unit/                                  # 단위 테스트 (Spring Context 불필요)
│   ├── domain/
│   │   ├── order/entity/OrderStatusTest.java   # 상태 전이 검증 (23 tests)
│   │   └── product/entity/ProductTest.java     # 재고/판매 로직 (6 tests)
│   └── service/
│       ├── OrderServiceTest.java               # 주문 생성/취소 (8 tests)
│       └── PaymentServiceTest.java             # 결제 요청/취소 (6 tests)
│
└── integration/                           # 통합 테스트 (Docker Compose + PostgreSQL)
    ├── CategoryApiTest.java               # 카테고리 트리 조회 (2 tests)
    ├── ProductApiTest.java                # 상품 목록/상세 (4 tests)
    ├── CartApiTest.java                   # 장바구니 CRUD (6 tests)
    ├── OrderApiTest.java                  # 주문 생성/조회/취소 (11 tests)
    ├── PaymentApiTest.java                # 결제 요청/취소 (6 tests)
    └── OrderConcurrencyTest.java          # 동시성 재고 차감 (2 tests)
```

**테스트 합계: 98개** (단위 43 + 통합 55)

## API 엔드포인트

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/v1/categories` | 카테고리 트리 조회 |
| GET | `/api/v1/products?categoryId={}&page={}&size={}` | 상품 목록 (페이지네이션) |
| GET | `/api/v1/products/{productId}` | 상품 상세 |
| GET | `/api/v1/cart` | 장바구니 조회 |
| POST | `/api/v1/cart/items` | 장바구니 상품 추가 |
| PATCH | `/api/v1/cart/items/{cartItemId}` | 장바구니 수량 변경 |
| DELETE | `/api/v1/cart/items/{cartItemId}` | 장바구니 상품 삭제 |
| POST | `/api/v1/orders` | 주문 생성 (장바구니 기반) |
| GET | `/api/v1/orders/{orderId}` | 주문 상세 |
| GET | `/api/v1/orders?page={}&size={}` | 주문 목록 |
| POST | `/api/v1/orders/{orderId}/cancel` | 주문 취소 |
| POST | `/api/v1/payments` | 결제 요청 |
| POST | `/api/v1/payments/{paymentKey}/cancel` | 결제 취소 (환불) |

모든 회원 인증 엔드포인트는 `X-Member-Id` 헤더가 필요합니다.

## 주문 상태 흐름

```
PENDING ──→ PAID ──→ PREPARING ──→ SHIPPING ──→ DELIVERED ──→ RETURNED
  │           │
  └→ CANCELLED └→ CANCELLED
                │
                └→ REFUND_REQUESTED ──→ REFUNDED
```

## 실행 방법

### 사전 요구사항

- Java 21
- Docker / Docker Compose

### 1. 인프라 실행

```bash
docker compose up -d
```

PostgreSQL(5432)과 RabbitMQ(5672, 관리 UI 15672)가 시작됩니다.

### 2. 스키마 및 테스트 데이터 적용

```bash
docker exec -i pms-order-postgres-1 psql -U pms -d pms_order < schema.sql
docker exec -i pms-order-postgres-1 psql -U pms -d pms_order < data.sql
```

회원 100명, 카테고리 10개, 상품 50개, 장바구니 100개가 생성됩니다.

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 4. 헬스 체크

```bash
curl http://localhost:8080/actuator/health
```

## 테스트 실행 방법

### 1. 테스트 인프라 실행

통합 테스트는 별도의 Docker Compose를 사용합니다 (본 서비스와 포트 충돌 없음).

```bash
docker compose -f docker-compose-test.yml up -d
```

| 서비스 | 호스트 포트 | 용도 |
|---|---|---|
| postgres-test | 15432 | 테스트 DB (tmpfs, 재시작 시 초기화) |
| rabbitmq-test | 15672 (AMQP), 25672 (관리 UI) | 테스트 메시징 |

### 2. 전체 테스트

```bash
./gradlew test
```

### 3. 단위 테스트만

```bash
./gradlew test --tests "com.pms.order.unit.*"
```

### 4. 통합 테스트만

```bash
./gradlew test --tests "com.pms.order.integration.*"
```

### 5. 테스트 인프라 종료

```bash
docker compose -f docker-compose-test.yml down
```

## 테스트 설계 원칙

### 단위 테스트

- **FIRST 원칙** 준수 (Fast, Isolated, Repeatable, Self-Validating, Thorough)
- Given-When-Then 구조로 가독성 확보
- Mockito `@InjectMocks` + `@Mock`으로 의존성 격리
- `ArgumentCaptor`를 사용한 이벤트 발행 타입 및 데이터 검증
- `TestFixture`로 테스트 데이터 생성 중복 제거

### 통합 테스트

- **실제 PostgreSQL** (Docker Compose)로 프로덕션 환경 재현
- `IntegrationTestBase`로 Spring Context 재사용 (`@DirtiesContext` 미사용)
- `@Transactional` 롤백으로 테스트 간 격리
- `StubExternalPaymentClient`(`@Primary`)로 외부 결제 결정적 동작 보장
- 동시성 테스트는 `@Transactional` 제외 + `@AfterEach` 수동 정리
- `flushAndClear()`로 JPA 캐시와 DB 상태 동기화 후 검증

## API 호출 예시

```bash
# 카테고리 조회
curl http://localhost:8080/api/v1/categories

# 상품 목록
curl "http://localhost:8080/api/v1/products?categoryId=6&page=0&size=10"

# 장바구니에 상품 추가
curl -X POST http://localhost:8080/api/v1/cart/items \
  -H "X-Member-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 2}'

# 장바구니 조회
curl http://localhost:8080/api/v1/cart -H "X-Member-Id: 1"

# 주문 생성
curl -X POST http://localhost:8080/api/v1/orders \
  -H "X-Member-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{"cartItemIds": [1]}'

# 결제 요청
curl -X POST http://localhost:8080/api/v1/payments \
  -H "X-Member-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{"orderId": 1}'

# 주문 취소
curl -X POST http://localhost:8080/api/v1/orders/1/cancel \
  -H "X-Member-Id: 1"
```

---

## Deployment (EC2 + nginx Blue/Green)

이 레포는 **EC2 1대 안에서의 무중단 Blue/Green 자산**까지를 책임진다. 2대 EC2에 대한 롤링 오케스트레이션(ALB deregister/register, 인스턴스 순회)은 별도 레포 [`bteam-jenkins`](../bteam-jenkins)가 담당한다.

### 배포 토폴로지

```
ALB ──► EC2 (nginx :80) ──► blue:8087  (active)
                          └► green:8088 (standby, 다음 배포 시 새 이미지)
```

배포 1회 = `nginx upstream`을 blue ↔ green 으로 스왑 + reload. 컨테이너 자체는 살아있어 트래픽 끊김 없음.

### 사전 조건

- EC2: Docker, docker compose plugin, awscli v2 설치. ECR pull 권한 IAM Role 부여.
- ALB Target Group health check path: **`/actuator/health/readiness`** (deregistration_delay 30~60초 권장).
- Jenkins ↔ EC2 SSH 도달성: 같은 VPC / SSM Session Manager / bastion 중 하나 필수.
- EC2의 `~/app/.env` 파일은 **운영자가 1회 수동 작성** (오케스트레이터가 덮어쓰지 않음).

### 환경변수 (`.env`)

`.env.example` 참고. 키 누락 시 Spring 부팅 실패(fail-fast).

| 키 | 설명 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `staging` 또는 `prod` |
| `DB_URL` | `jdbc:postgresql://<host>:5432/pms_order` |
| `DB_USERNAME` / `DB_PASSWORD` | RDS 자격증명 |
| `RABBIT_HOST` / `RABBIT_PORT` | 기본 5672 |
| `RABBIT_USERNAME` / `RABBIT_PASSWORD` | RabbitMQ 자격증명 |

### 오케스트레이터 인터페이스 (`bteam-jenkins`가 호출)

| 항목 | 계약 |
|---|---|
| 동기화 대상 | `docker-compose.deploy.yml` → `~/app/docker-compose.yml`, `nginx/` → `~/app/nginx/`, `scripts/` → `~/app/scripts/` |
| 사전 환경변수 | `ECR_REPO`, `IMAGE_TAG`, `SPRING_PROFILES_ACTIVE` (deploy.sh 호출 직전 export) |
| 사전 환경파일 | `~/app/.env` (운영자 사전 작성) |
| 호출 순서 | `deploy.sh /actuator/health/readiness` → ALB healthy 검증 → `stop-old-color.sh <old>` |
| `deploy.sh` 종료 코드 | 0 = 새 색상 활성화 성공 (stdout 마지막 줄 = old color) / 1 = 실패 (이전 색상 자동 복구됨, ALB 영향 없음) |
| ALB healthy 실패 시 롤백 | 오케스트레이터가 `nginx/templates/upstream-<old>.conf` → `nginx/conf.d/upstream.conf` 덮어쓰고 nginx reload. `stop-old-color.sh` 호출하지 않음 |

### 운영 주의사항

- **2대 EC2 롤링 = 배포 중 처리 용량 50% 감소**. 부하 높은 시간대 배포 금지.
- **DB 변경은 backward-compatible** (expand → deploy → contract). Flyway 도입은 별도 트랙.
- 배포 직후 `docker logs pms-blue / pms-green` 으로 active/standby 상태 확인 가능. `~/app/state/active_color` 파일도 확인.
- nginx config 변경 시 반드시 `docker compose exec nginx nginx -t` 로 검증. deploy.sh가 자동으로 수행하지만 수동 변경 후엔 잊지 말 것.

### 로컬 검증 절차

1. 로컬에서 임시 ECR 태그로 이미지 빌드/태깅 후 `docker compose -f docker-compose.deploy.yml up -d`
2. `curl localhost/actuator/health/readiness` → 200
3. `IMAGE_TAG=v2 ECR_REPO=<...> ./scripts/deploy.sh` 두 번 실행 → blue↔green 순환 확인
4. 일부러 readiness 실패하는 이미지로 배포 → deploy.sh fail, 이전 색상 그대로 살아있음 확인
5. `nginx/templates/upstream-blue.conf`에 syntax 에러 주입 → `nginx -t` 실패 시 자동 롤백 확인

