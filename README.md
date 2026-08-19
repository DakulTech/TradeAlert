# TradeAlert

TradeAlert monitors currency exchange rates and notifies users when a configured target is reached. It is built as a Java 21 and Spring Boot microservices platform with Kafka for event delivery, PostgreSQL for durable state, Redis for sessions and offline notifications, and WebSocket/STOMP for real-time delivery.

## What It Guarantees

- **One alert trigger:** an active alert is marked as notified before its alert event is published.
- **Offline delivery:** notifications for disconnected users are stored in Redis and replayed after reconnect.
- **Asynchronous processing:** rate ingestion, alert evaluation, and notification delivery are decoupled through Kafka.
- **Independent scaling:** each service can be scaled according to its workload.

## Architecture

```text
Client
  |
  v
API Gateway :8081
  |-- User Service         :8080  authentication and sessions
  |-- Verification Service :8085  email verification
  |-- Rate Service         :8082  rate ingestion and lookup
  |-- Alert Service        :8084  threshold evaluation
  `-- Notification Service :8083  WebSocket delivery and offline replay

PostgreSQL  durable users, alerts, and rates
Redis       sessions, rate limiting, verification tokens, offline queue
Kafka       user-registered -> verification
            rates            -> alert evaluation
            alerts           -> notification delivery
```

## Services

| Service | Port | Responsibility |
| --- | ---: | --- |
| API Gateway | `8081` | Entry point, routing, JWT validation, rate limiting |
| User Service | `8080` | Registration, login, logout, user state |
| Rate Service | `8082` | Ingests and retrieves exchange rates |
| Alert Service | `8084` | Stores alerts and evaluates thresholds |
| Notification Service | `8083` | Sends WebSocket messages or queues them in Redis |
| Verification Service | `8085` | Sends and confirms email verification tokens |

## Prerequisites

- Docker Desktop with Docker Compose
- Java 21 LTS for local Maven builds
- Maven 3.13 or later, or the Maven Wrapper if added to the project

Verify Java and Maven:

```powershell
java -version
mvn -v
```

## Configuration

Create a `.env` file at the repository root. Do not commit real credentials.

```dotenv
DB_PASSWORD=change-me
JWT_SECRET=change-me-to-a-long-random-secret
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-address@gmail.com
SMTP_PASSWORD=your-gmail-app-password
COMPOSE_PARALLEL_LIMIT=1
```

The services use these Docker Compose hostnames internally:

```text
postgres-primary:5432
kafka:9092
redis:6379
```

## Build and Run

Build all six service images:

```powershell
docker compose build --no-cache
```

If Docker Desktop reports `bad_record_mac`, `failed to stat parent`, or an overlayfs snapshot error, restart Docker Desktop, verify available disk space, and retry with Bake disabled:

```powershell
$env:COMPOSE_BAKE = "false"
docker compose --parallel 1 build --no-cache
```

Start the platform:

```powershell
docker compose up -d
docker compose ps
```

Stop the platform:

```powershell
docker compose down
```

## Health Checks

```powershell
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health
```

Expected response from a healthy Spring Boot service:

```json
{
  "status": "UP"
}
```

Metrics are exposed at `/actuator/prometheus` where enabled. Prometheus is available at `http://localhost:9090`.

## API Conventions

Use the API Gateway at `http://localhost:8081` for client requests. Protected routes require:

```http
Authorization: Bearer <jwt>
```

The examples below use PowerShell. Replace `$token`, `$userId`, and `$alertId` with values returned by the preceding request.

## Authentication and Verification

### 1. Rhema registers

The registration endpoint accepts a `User` JSON document. The password is stored as a BCrypt hash. Registration publishes a `user-registered` Kafka event for the Verification Service.

```powershell
$registration = @{
  email = "rhema@gmail.com"
  name = "Rhema"
  passwordHash = "RhemaPassword!23"
  emailNotifications = $true
  websocketNotifications = $true
} | ConvertTo-Json

$registrationResponse = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/api/auth/register" `
  -ContentType "application/json" `
  -Body $registration

$registrationResponse
```

Example output:

```text
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiaWF0IjoxNz...signature...
```

The returned value is a JWT. The new user is initially unverified.

### 2. Verification email pattern

The registration event is consumed asynchronously:

```text
User Service
  -> Kafka: user-registered
  -> Verification Service
  -> Redis: verify:token:<token>, TTL 15 minutes
  -> SMTP email to rhema@gmail.com
```

The email contains a link in this form:

```text
https://your-app.com/verify?userId=<userId>&token=<token>
```

The API endpoint that confirms the token is:

```http
GET /api/verify/confirm?userId=<userId>&token=<token>
```

Successful response:

```text
Verification successful for user 1
```

Expired or invalid token response:

```text
Invalid or expired verification token
```

The token is deleted from Redis after successful confirmation, so it cannot be reused.

### 3. Rhema logs in

Login uses `email` and `password` query parameters. Optional `device` and `location` values can be supplied for session context.

```powershell
$token = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/api/auth/login?email=rhema%40gmail.com&password=RhemaPassword%2123&device=web&location=home"

$headers = @{ Authorization = "Bearer $token" }
$token
```

Example output:

```text
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiaWF0IjoxNz...signature...
```

The User Service stores the active session in Redis under `session:<userId>`.

### 4. Rhema logs out

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/api/auth/logout/$userId" `
  -Headers $headers
```

Expected response:

```text
HTTP 204 No Content
```

Logout removes the Redis session and failed-login counter. The client should also discard its JWT. The current gateway validates JWT signatures and expiration; immediate token revocation requires a token blacklist or equivalent gateway check.

## Rate and Alert Endpoints

### Ingest a rate

```http
POST /api/rates/ingest?currencyPair=USD%2FEUR&rateValue=0.9500
Authorization: Bearer <jwt>
```

PowerShell:

```powershell
$rate = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/api/rates/ingest?currencyPair=USD%2FEUR&rateValue=0.9500" `
  -Headers $headers

$rate | ConvertTo-Json
```

Example output:

```json
{
  "id": 42,
  "currencyPair": "USD/EUR",
  "rate": 0.95,
  "timestamp": "2026-08-19T12:00:00Z"
}
```

The rate is persisted and published to Kafka topic `rates`.

### Read rates

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/rates` | Return all stored rates |
| `GET` | `/api/rates/fetch/{currencyPair}` | Fetch and ingest the latest provider rate |
| `GET` | `/api/rates/health` | Check the rate provider |

### Graceful rate-provider failover

`GET /api/rates/fetch/{currencyPair}` uses two external providers:

```text
1. Call the primary provider.
2. Wait no longer than 5 seconds for a response.
3. If the primary succeeds, use its rate.
4. If the primary fails or times out, call the secondary provider.
5. If the secondary succeeds, persist and publish its rate.
6. If both providers fail, return HTTP 503 and do not publish a new rate.
```

The service does not silently invent or reuse a rate when both providers are unavailable. This protects alert consistency: an alert must only be evaluated against a rate that was actually received from a provider or explicitly ingested by a trusted caller.

Example fallback response from the health endpoint:

```json
{
  "primaryApi": false,
  "secondaryApi": true
}
```

The rate service reports this state as `DEGRADED` through its Actuator health indicator. If both providers are down, the health state is `DOWN` and fetch requests return:

```http
HTTP 503 Service Unavailable
```

Previously persisted rates remain available through `GET /api/rates`. That endpoint is a historical read and does not pretend that an old value is a current provider value.

### Create Rhema's alert

An alert contains a user, currency pair, target rate, and direction. Direction is `ABOVE` or `BELOW`.

```powershell
$alertBody = @{
  user = @{ id = $userId }
  currencyPair = @{ symbol = "USD/EUR" }
  targetRate = 0.9500
  direction = "ABOVE"
} | ConvertTo-Json -Depth 4

$alert = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/api/alerts" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $alertBody

$alert | ConvertTo-Json -Depth 5
$alertId = $alert.id
```

Example output:

```json
{
  "id": 1001,
  "targetRate": 0.95,
  "direction": "ABOVE",
  "notified": false,
  "createdAt": "2026-08-19T12:01:00Z",
  "triggeredAt": null
}
```

Alert management endpoints:

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/alerts` | List alerts |
| `POST` | `/api/alerts` | Create an alert |
| `PUT` | `/api/alerts/{id}` | Update an alert |
| `DELETE` | `/api/alerts/{id}` | Delete an alert |

## Rhema's Complete Alert Journey

Assume Rhema created an `ABOVE` alert for `USD/EUR` at `0.9500`.

### Before the target is reached

```text
Alert Service database:
  targetRate = 0.9500
  direction  = ABOVE
  notified   = false
```

Rates below the target do not trigger the alert.

### The rate reaches the target

Rhema or a provider sends a new rate:

```text
POST /api/rates/ingest?currencyPair=USD%2FEUR&rateValue=0.9500
```

The event path is:

```text
1. Rate Service saves the rate in PostgreSQL.
2. Rate Service publishes RateEvent to Kafka topic: rates.
3. Alert Service consumes the rate event.
4. Alert Service finds active matching alerts.
5. The matching alert is marked notified=true.
6. Alert Service publishes AlertTriggeredEvent to Kafka topic: alerts.
7. Notification Service consumes the event.
```

The event contains the alert ID, user ID, currency pair, target rate, triggered rate, and trigger timestamp.

### Rhema is online

When Rhema has an active WebSocket connection, Notification Service sends the event immediately to:

```text
/topic/alerts/<userId>
```

The client connects to the notification service WebSocket/STOMP endpoint:

```text
ws://localhost:8083/ws/alerts
```

The client subscribes to:

```text
/topic/alerts/1
```

Example message:

```json
{
  "alertId": 1001,
  "userId": 1,
  "currencyPair": "USD/EUR",
  "targetRate": 0.95,
  "triggeredRate": 0.95,
  "triggeredAt": "2026-08-19T12:05:00Z"
}
```

### Rhema is offline

If Rhema is disconnected, Notification Service stores the event in Redis:

```text
pending:1 -> [<serialized AlertTriggeredEvent>]
```

No notification is discarded just because the WebSocket is unavailable. When Rhema reconnects, the pending events are replayed in order to `/topic/alerts/1`, then the Redis list is cleared.

The replay endpoint is also available for clients that need to request it explicitly:

```http
POST /api/notifications/replay/{userId}
```

Example response:

```text
Pending notifications replayed for user 1
```

### Notification is received once

The once-only path is enforced at alert state level:

```text
active alert
  -> matching rate
  -> notified=true
  -> one AlertTriggeredEvent
  -> one immediate delivery or one offline queue entry
```

The `notified` flag prevents the same active alert from being selected again after it has triggered. Kafka consumer groups provide a stable consumer identity, while Redis preserves offline events until replay. Clients should subscribe once per user session and acknowledge their own UI state without duplicating messages.

## Notification Utility Endpoints

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/notifications/replay/{userId}` | Replay queued notifications |
| `GET` | `/api/notifications/ping/{userId}` | Send a WebSocket ping event |

Ping messages are sent to:

```text
/topic/ping/<userId>
```

## Observability

Each service exposes Spring Boot Actuator endpoints where configured:

```text
/actuator/health
/actuator/metrics
/actuator/prometheus
```

Useful counters include:

```text
alerts_created_total
alerts_triggered_total
notifications_delivered_total
notifications_queued_total
notifications_replayed_total
notifications_failed_total
logins_failed_total
```

## Repository Layout

```text
TradeAlert/
|-- api-gateway/
|-- user-service/
|-- verification-service/
|-- rate-service/
|-- alert-service/
|-- notification-service/
|-- common/
|   |-- config/       Kubernetes configuration
|   |-- models/       OpenAPI model definitions
|   `-- observability/
|-- postgres/
|-- docker-compose.yml
`-- prometheus.yml
```

## Development Commands

Build one service locally without tests:

```powershell
cd alert-service
mvn clean package -Dmaven.test.skip=true
```

Run a packaged service:

```powershell
java -jar target/alert-service-0.0.1-SNAPSHOT.jar
```

Follow service logs:

```powershell
docker compose logs -f api-gateway
docker compose logs -f alert-service notification-service
```

## Security Notes

- Replace every default password and JWT secret before deployment.
- Use an SMTP app password rather than a personal email password.
- Keep `.env` and Kubernetes secret files out of source control.
- Use HTTPS and `wss://` outside local development.
- Add JWT revocation or a token blacklist if logout must invalidate an already-issued token immediately.

## Availability, Consistency, and Trade-offs

TradeAlert favors durable alert state and reliable notification handling while allowing asynchronous delivery between services. This is intentional: a currency alert can tolerate a short processing delay more safely than it can tolerate an incorrect trigger or a silently lost notification.

### Availability choices

| Approach | Availability benefit | Cost or limitation |
| --- | --- | --- |
| Primary and secondary rate APIs | A provider outage can be absorbed by failover. | The providers may disagree; the service uses the primary when both return materially different values. |
| Five-second provider timeout | A slow external API does not block a request indefinitely. | A slow provider may be abandoned while it could eventually have responded. |
| Kafka topics | Rate and alert events remain decoupled when a consumer is restarting. | Notification delivery is asynchronous and may be delayed by consumer lag. |
| Redis offline queue | Disconnected users can receive pending alerts after reconnecting. | Redis is an operational dependency; a Redis outage prevents queueing until it recovers. |
| Docker restart policies and health checks | Failed containers can restart automatically. | Restarting a process does not replace database, broker, or provider high availability. |

### Consistency choices

| Approach | Consistency benefit | Cost or limitation |
| --- | --- | --- |
| PostgreSQL for users, alerts, and rates | Alert state and persisted rates survive process restarts and support transactional updates. | Cross-service changes are not one distributed transaction. |
| `notified` alert state | A matching active alert is marked before its Kafka event is emitted, preventing repeated selection of the same alert. | A failure after marking and before event publication needs operational reconciliation or an outbox pattern for stronger guarantees. |
| Kafka consumer groups | Each service has a stable consumption identity and can resume from committed offsets. | The system is eventually consistent: an ingested rate is not visible to notification clients instantly. |
| Redis queue replay | Pending notifications are replayed in list order and cleared after replay. | Redis queue delivery is not a substitute for a durable audit log or a transactional outbox. |
| Provider failover without stale fallback | Alerts are never evaluated against an invented or silently stale current rate. | Both providers being down produces `503`, so current-rate availability is sacrificed to protect correctness. |

### Why this balance

The platform separates two kinds of data:

1. **Durable decisions:** users, alert rules, alert state, and ingested rates belong in PostgreSQL.
2. **Transport and delivery state:** Kafka carries events, while Redis holds sessions and short-lived offline notifications.

This gives Rhema a predictable result: if a provider fails, the service tries another source; if both fail, it reports the outage instead of creating a false trigger; if the alert really triggers, the event is processed independently; and if Rhema is offline, the notification waits for her reconnect.

For stronger production guarantees, the next hardening steps are a transactional outbox between PostgreSQL and Kafka, idempotent notification event IDs, Redis high availability, replicated Kafka, and managed PostgreSQL failover.

## Current Scope

The Docker Compose setup is intended for local development and integration testing. Production deployments should add managed PostgreSQL high availability, Kafka replication, Redis high availability, TLS termination, centralized logs, secret management, and load testing before targeting 100,000 active alerts.
