<!--
Steps
Explain the project and how to run it locally.
Show what has been implemented so far.
Show an order example and the database schema.
Add the Maven, Docker and Swagger instructions.
-->

# OrderFlow API

A small order management microservice built with **Java 21, Spring Boot and Maven** as part of a backend technical challenge.

It manages products and orders through a REST API: create, retrieve and list products, and create, retrieve and list orders. Order totals are calculated on the server from the current product prices, each order item keeps the price at the time of ordering, and an order and its items are saved in one transaction. All endpoints, including the Swagger documentation, require HTTP Basic authentication. Data is stored in an in-memory H2 database.

The aim is to keep the service easy to understand and test, while making deliberate choices around validation, persistence and error handling.

## Development milestones

The project was built in small steps:

- **Phase 1 - Foundation:** Maven project, Spring Boot, in-memory H2 and HTTP Basic authentication.
- **Phase 2 - Products:** Create, retrieve and list products; request validation and initial unit/integration tests.
- **Phase 3 - Orders:** Order items, product existence checks, price snapshots and calculated totals in one transaction.
- **Phase 4 - Errors and tests:** Consistent error responses and broader integration coverage.
- **Phase 5 - Documentation and logging:** Request logging with request IDs, Swagger/OpenAPI behind authentication, and usage examples.
- **Phase 6 - Docker:** Container image and a simple local run option with Docker Compose.
- **Phase 7A - Pagination:** Optional `page` and `size` parameters on the product and order lists, applied in the database.

## Technology

- Java 21 and Spring Boot 4.1.1
- Maven Wrapper
- Spring MVC, Spring Data JPA and Jakarta Bean Validation
- H2 in-memory database
- Spring Security (HTTP Basic)
- Springdoc OpenAPI 3.1.1 (Swagger UI)
- JUnit 5, Mockito and Spring MVC integration tests

## Architecture

```mermaid
flowchart TD
    Client[API client or Swagger UI]
    subgraph Container["Optional: Docker container (host 127.0.0.1:8089 to container 8080)"]
        Log[Request logging filter - request ID] --> Security[Spring Security - Basic Auth]
        Security --> PC[Product Controller]
        Security --> OC[Order Controller]
        Security --> Docs[Swagger UI and OpenAPI JSON]
        PC --> PS[Product Service]
        OC --> OS[Order Service]
        PS --> PR[Product Repository]
        OS --> PR
        OS --> OR[Order Repository]
        PR --> H2[(H2 in-memory database)]
        OR --> H2
        PC -.-> EH[Global exception handler]
        OC -.-> EH
    end
    Client --> Log
```

The same application runs directly through Maven, as the packaged JAR, or inside the Docker container shown above.

The application uses a simple layered design: controllers handle HTTP, services apply business rules, and repositories handle database access. The order service also reads products (through the product repository) to check they exist and to copy their prices. The logging filter runs first so that even rejected (401) requests are logged, and one exception handler turns errors into consistent JSON responses.

### Database schema

```mermaid
erDiagram
    PRODUCTS ||--o{ ORDER_ITEMS : "referenced by"
    ORDERS ||--|{ ORDER_ITEMS : contains
    PRODUCTS {
        bigint id PK
        varchar name
        decimal price "DECIMAL(19,2)"
    }
    ORDERS {
        bigint id PK
        decimal total_price "DECIMAL(19,2)"
        timestamp created_at "UTC"
    }
    ORDER_ITEMS {
        bigint id PK
        bigint order_id FK
        bigint product_id FK
        int quantity
        decimal unit_price "price at order time"
    }
```

`order_items` links an order to a product and also stores the quantity and the product's price at the moment of ordering. Line totals are not stored; they are calculated as `unit_price * quantity` when an order is returned.

## Run locally

**Requirements:** JDK 21. Maven does not need to be installed globally because the project includes the Maven Wrapper.

In PowerShell, make sure `JAVA_HOME` points to JDK 21, then run:

```powershell
.\mvnw.cmd clean verify

# Set your own local development credentials (do not commit them).
$env:ORDERFLOW_USER = "local-user"
$env:ORDERFLOW_PASSWORD = "replace-with-a-local-password"

.\mvnw.cmd spring-boot:run
```

On Linux or macOS, with `JAVA_HOME` pointing to JDK 21:

```bash
./mvnw clean verify

# Set your own local development credentials (do not commit them).
export ORDERFLOW_USER="local-user"
export ORDERFLOW_PASSWORD="replace-with-a-local-password"

./mvnw spring-boot:run
```

The API uses the default local address `http://localhost:8080` unless configured otherwise. The application requires both credential variables at startup. All API routes require authentication.

To use another port, run the packaged jar with `java -jar target/orderflow-api-0.0.1-SNAPSHOT.jar --server.port=8089` (after `clean verify`, with the credential variables set; use a backslash in the path on Windows).

**Note:** H2 is in memory. Data is lost when the application stops; this is intentional for the exercise.

Quick check once the app is running (use the credentials you set above):

```powershell
curl.exe -u "$env:ORDERFLOW_USER`:$env:ORDERFLOW_PASSWORD" http://localhost:8080/api/v1/products
```

```bash
curl -u "$ORDERFLOW_USER:$ORDERFLOW_PASSWORD" http://localhost:8080/api/v1/products
```

## API documentation (Swagger)

With the application running:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Both require the same HTTP Basic credentials as the API; they are not public. Opening Swagger UI in a browser makes the browser ask for the username and password. Inside Swagger UI the **Authorize** button (scheme `basicAuth`) lets you enter them so that "Try it out" requests are authenticated. The documentation is generated from the controllers and DTOs by Springdoc, with a few short annotations for summaries and status codes.

## Logging

A small filter writes one line per request, after it completes:

```text
INFO [3f1c...] ... RequestLoggingFilter : request_id=3f1c... method=POST path=/api/v1/orders status=201 duration_ms=38
```

It also logs `Order created order_id=15 items=3` once an order has been committed; rejected orders do not produce this line.

**Request IDs:** if the client sends an `X-Request-ID` header made of letters, digits, `.`, `_` or `-` (up to 64 characters) it is kept; otherwise a UUID is generated. The effective ID is returned in the `X-Request-ID` response header and appears on every log line written during that request, so a client can quote it when reporting a problem.

Logs contain only the path, never the query string, `Authorization` header, credentials or request/response bodies.

## Run with Docker (optional)

Docker is an alternative way to run the same application; Maven remains the main build. The `Dockerfile` builds and tests the project with Maven on Java 21 and copies only the packaged JAR into a Java 21 JRE image that runs as a non-root user. No credentials are stored in the image.

**Prerequisites:** a running Docker engine with Docker Compose v2 (for example Rancher Desktop with the Moby/dockerd engine, or Docker Desktop).

Set demonstration credentials (both are required; Compose stops with an error if either is missing), then build and start. PowerShell:

```powershell
$env:ORDERFLOW_USER = "local-user"
$env:ORDERFLOW_PASSWORD = "replace-with-a-local-password"

docker compose up --build -d
docker compose ps
```

Linux or macOS:

```bash
export ORDERFLOW_USER="local-user"
export ORDERFLOW_PASSWORD="replace-with-a-local-password"

docker compose up --build -d
docker compose ps
```

The first build runs the whole test suite inside Docker, so it takes a few minutes, and the application needs up to a minute to start after the container is up. The API is then at `http://localhost:8089` (published on localhost only; set `ORDERFLOW_HOST_PORT` to use another host port):

```powershell
curl.exe -u "$env:ORDERFLOW_USER`:$env:ORDERFLOW_PASSWORD" http://localhost:8089/api/v1/products
```

```bash
curl -u "$ORDERFLOW_USER:$ORDERFLOW_PASSWORD" http://localhost:8089/api/v1/products
```

Swagger UI is at `http://localhost:8089/swagger-ui/index.html` and the OpenAPI JSON at `http://localhost:8089/v3/api-docs`, both with the same credentials.

```bash
docker compose logs --tail=50 orderflow-api   # application logs (written to standard output)
docker compose down                            # stop and remove this project's container
```

Passing credentials as environment variables is fine for a local demonstration, but it is not a complete secrets-management approach.

**H2 limitations in Docker:** the database is in memory inside the container. All data is lost when the container stops, is restarted by `down`/`up`, or is recreated, and a new empty database is created. Each container would have its own independent database, so the demo must run as a single instance; scaling out would need an external persistent database. Compose deliberately has no volume for this.

## API status

| Method | Endpoint |
| --- | --- |
| `POST` | `/api/v1/products` |
| `GET` | `/api/v1/products/{id}` |
| `GET` | `/api/v1/products` |
| `POST` | `/api/v1/orders` |
| `GET` | `/api/v1/orders/{id}` |
| `GET` | `/api/v1/orders` |

Example product request:

```json
{
  "name": "Keyboard",
  "price": 450.00
}
```

Product names must not be blank, and prices must be positive with no more than two decimal places. The service assigns product IDs.

### Creating an order

```json
POST /api/v1/orders
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 2, "quantity": 1 }
  ]
}
```

Example response (`201 Created`, with a `Location` header pointing at the new order):

```json
{
  "id": 1,
  "items": [
    { "productId": 1, "productName": "Keyboard", "quantity": 2, "unitPrice": 450.00, "lineTotal": 900.00 },
    { "productId": 2, "productName": "Mouse", "quantity": 1, "unitPrice": 250.00, "lineTotal": 250.00 }
  ],
  "totalPrice": 1150.00,
  "createdAt": "2026-10-22T12:00:00Z"
}
```

Clients send only product IDs and quantities. Prices and totals always come from the server. Quantities must be whole numbers from 1 to 1000, an order needs at least one item, and the same product cannot appear twice in one order (`400`). If any product does not exist the whole order is rejected with `404`, and the response lists every missing ID in `missingProductIds`.

## Pagination

The two list endpoints, `GET /api/v1/products` and `GET /api/v1/orders`, accept optional `page` and `size` query parameters.

- Without them you get the full array, exactly as before.
- With them you must send **both**. Pages are numbered from 0, `size` must be between 1 and 100, and results are sorted by ID ascending.
- The body is still a plain array of the same product or order objects. The page details are in response headers:

| Header | Meaning |
| --- | --- |
| `X-Total-Count` | Total number of records |
| `X-Total-Pages` | Total number of pages for the requested size |
| `X-Page` | The requested page number (from 0) |
| `X-Page-Size` | The requested page size |

- A page beyond the last one returns `200` with an empty array (the headers still show the totals).
- A missing parameter, a negative `page`, a `size` outside 1-100, a non-numeric value, or a `page` so large that its row offset would not fit in an `int` returns `400`.

```bash
curl -u "$ORDERFLOW_USER:$ORDERFLOW_PASSWORD" -i "http://localhost:8080/api/v1/products?page=0&size=2"
curl -u "$ORDERFLOW_USER:$ORDERFLOW_PASSWORD" -i "http://localhost:8080/api/v1/orders?page=1&size=10"
```

```powershell
curl.exe -u "$env:ORDERFLOW_USER`:$env:ORDERFLOW_PASSWORD" -i "http://localhost:8080/api/v1/products?page=0&size=2"
```

**Why in the database:** `LIMIT`/`OFFSET` is applied by the database, so only the requested rows are read and sent to the application, instead of loading every row and slicing the list in Java. For orders this takes two queries: first the ids of the requested page, then only those orders together with their items and products. Fetching the items collection in the paged query itself would make Hibernate page in memory, and the two-query approach also avoids one query per order. A test checks the loaded entities and the number of SQL statements.

## Error handling

All endpoints require HTTP Basic authentication. Missing or wrong credentials return `401`; this is answered by Spring Security before the request reaches a controller, so it does not use the JSON format below.

Every other error is returned as an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details response (`application/problem+json`), handled in one class, `GlobalExceptionHandler`. Responses never contain stack traces, exception class names or database details.

| Situation | Status |
| --- | --- |
| Invalid fields, malformed JSON (including a fractional quantity such as `1.5`), duplicate product IDs in an order, invalid pagination parameters | `400` |
| Unknown product or order, or products missing from an order | `404` |

Validation error, with field errors sorted by field name:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/v1/products",
  "fieldErrors": [
    { "field": "name", "message": "must not be blank" }
  ]
}
```

Order that references products that do not exist (nothing is saved):

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "One or more products were not found",
  "instance": "/api/v1/orders",
  "missingProductIds": [10, 20]
}
```

Unreadable JSON returns the fixed message `Malformed or invalid JSON request`. Unexpected server errors return a plain `500` without exception details (checked by a test); the cause is only written to the server log.

## Design notes

- **One service:** This domain does not need distributed services for the challenge.
- **Spring MVC:** The work is blocking JPA CRUD, so the simpler servlet stack fits better than WebFlux.
- **H2 in memory:** The exercise asks for it, and it makes the project easy to run and review.
- **DTOs separate from entities:** API contracts should not depend on database entities.
- **`BigDecimal` for money:** Avoid floating-point errors in prices and totals.
- **Validation at the boundary:** Reject invalid input before persisting it.
- **Price snapshots:** Each order item stores the product price at the time of ordering, so later price changes do not alter past orders.
- **Transactions for orders:** Order creation is one transaction. All checks run before saving, and the order and its items are saved together, so a failed order leaves no partial records.
- **Simple security:** HTTP Basic meets the exercise requirements without a separate identity system. It sends credentials with every request, so it should only be used over HTTPS outside local development.
- **Logging without bodies or credentials:** Request bodies and headers can contain personal data or secrets, so logs carry only the request ID, method, path, status and timing.

## Known limitations

- Data is lost when the application restarts, and the service must run as a single instance because H2 is in memory.
- Pagination is optional: without `page` and `size` the lists return every record.
- Products and orders cannot be updated or deleted.
- The demo uses one configured user; there are no roles or user management.

## Tests

Run all tests through Maven:

```powershell
.\mvnw.cmd clean verify
```

Current tests (141) cover product and order service behaviour, authenticated API requests, validation, exact decimal totals, price snapshots, missing products, H2 persistence, rollback of a failed order, the structure of every error response, an unexpected 500 error, request logging and request IDs, the secured Swagger/OpenAPI endpoints, and pagination (parameter rules, headers, page contents and the database-level paging of orders).
