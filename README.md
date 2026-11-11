# OrderHub — Order Management REST API

[![CI](https://github.com/Ramanna1218/orderhub/actions/workflows/ci.yml/badge.svg)](https://github.com/Ramanna1218/orderhub/actions)

A production-style REST API for managing **products, customers and orders**, built with
**Java 21, Spring Boot 3, Spring Security (JWT), Spring Data JPA, PostgreSQL, Flyway, Docker**, deployed on **Render** with **Neon PostgreSQL**.

> 🚀 **Live API docs:** https://orderhub-rpv4.onrender.com/swagger-ui.html (free tier: first load may take 2–3 min to wake up)

---

## Features

- **Layered architecture**: Controller → Service → Repository, with DTOs (Java records) at the API boundary
- **JWT authentication** with **role-based access** (`ADMIN`, `USER`), stateless sessions, BCrypt password hashing
- **Order workflow** with stock reservation, price snapshots, and an enforced status lifecycle
  (`PENDING → CONFIRMED → SHIPPED → DELIVERED`, or `CANCELLED`, which restores stock)
- **Concurrency-safe stock**: product rows are locked (`SELECT … FOR UPDATE`) in a fixed order while an order is placed, so two buyers can't oversell the same item
- **Pagination & sorting** on every list endpoint (`?page=0&size=20&sort=price,desc`), with search on products
- **Bean Validation** on all requests, plus **centralized exception handling** returning one consistent JSON error shape
- **Flyway** database migrations (schema is versioned, never auto-generated)
- **Tests**: JUnit 5 + Mockito unit tests and full-stack integration tests against real PostgreSQL via **Testcontainers**
- **CI**: GitHub Actions builds, runs all tests, and builds the Docker image on every push and PR
- **Swagger / OpenAPI** docs with a built-in "Authorize" button for JWTs

## Architecture

```
            ┌─────────────────────────── Spring Boot app ───────────────────────────┐
 Client ──► │ JwtAuthenticationFilter → Controllers → Services → Repositories (JPA) │ ──► PostgreSQL
 (JSON)     │        ▲ SecurityConfig (roles)      │ @Transactional      Flyway ───┘
            │        └ GlobalExceptionHandler ◄────┘                                │
            └────────────────────────────────────────────────────────────────────────┘
```

```
src/main/java/com/orderhub
├── auth/        register & login, token responses
├── security/    JWT issue/verify, auth filter, JSON 401/403 handler
├── config/      Spring Security rules, OpenAPI
├── user/        user entity, UserDetailsService, admin seeding
├── product/     catalog CRUD + search
├── customer/    customer CRUD
├── order/       order placement, status lifecycle, stock handling
└── common/      ApiError, PageResponse, exceptions + global handler
```

## Access rules

| Endpoint | Public | USER | ADMIN |
|---|:-:|:-:|:-:|
| `POST /api/auth/register`, `/login` | ✅ | ✅ | ✅ |
| `GET /api/products/**` | ✅ | ✅ | ✅ |
| `POST/PUT/DELETE /api/products/**` | | | ✅ |
| `/api/customers/**` | | | ✅ |
| `POST /api/orders`, `GET /api/orders/**` | | ✅ | ✅ |
| `PATCH /api/orders/{id}/status` | | | ✅ |

New sign-ups are always `USER`. The first `ADMIN` is created at startup from `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

## Run locally

**Option A: everything in Docker (recommended)**

```bash
cp .env.example .env        # then edit the values
docker compose up --build
```

**Option B: run the app from your IDE**

```bash
docker compose up -d db     # PostgreSQL only
mvn spring-boot:run
```

Then open **http://localhost:8080/swagger-ui.html**.

## Try it with curl

```bash
# 1. Log in as admin
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@orderhub.local","password":"<ADMIN_PASSWORD>"}' | jq -r .accessToken)

# 2. Create a product and a customer
curl -s -X POST localhost:8080/api/products -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"sku":"KB-001","name":"Mechanical Keyboard","price":89.99,"stockQuantity":25}'

curl -s -X POST localhost:8080/api/customers -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com"}'

# 3. Place an order (stock drops from 25 to 23)
curl -s -X POST localhost:8080/api/orders -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"customerId":1,"items":[{"productId":1,"quantity":2}]}'

# 4. Move it through the lifecycle
curl -s -X PATCH localhost:8080/api/orders/1/status -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"status":"CONFIRMED"}'

# 5. Paginated, filtered listing
curl -s "localhost:8080/api/orders?status=CONFIRMED&page=0&size=10" -H "Authorization: Bearer $TOKEN"
```

Error responses always look like this:

```json
{
  "timestamp": "2026-11-10T21:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Insufficient stock for product KB-001: requested 50, available 23",
  "path": "/api/orders"
}
```

## Tests

```bash
mvn test       # unit + integration (integration tests need Docker; they skip without it)
```

- `OrderServiceTest`: totals, stock decrement, duplicate-line merging, insufficient stock, cancel restores stock, invalid transitions
- `ProductServiceTest`, `JwtServiceTest`, `OrderStatusTest`: business rules, token signing/expiry/tampering, lifecycle rules
- `OrderHubIntegrationTest`: real HTTP + JWT + PostgreSQL flow covering 201/400/401/403/409 paths

## Deployment

Live on **Render** (Docker web service) with **Neon** serverless PostgreSQL.
To self-host on AWS EC2 instead, see **[docs/DEPLOY_AWS.md](docs/DEPLOY_AWS.md)**.

## Tech stack

Java 21 · Spring Boot 3.3 · Spring Security 6 · jjwt · Spring Data JPA / Hibernate · PostgreSQL 16 · Flyway ·
springdoc-openapi · JUnit 5 · Mockito · Testcontainers · Docker / Docker Compose · GitHub Actions · Render · Neon
