# TradeAlert

TradeAlert monitors currency exchange rates and notifies Rhema when a configured target is reached. The application is a modular monolith built with Java 21 and Spring Boot. All business capabilities run in one application process and share one PostgreSQL database.

## Architecture

```text
Client
  |
  v
TradeAlert application :8080
  |-- identity       registration, login, logout, JWT
  |-- verification   email verification and token confirmation
  |-- rates          provider access, persistence, scheduled polling
  |-- alerts         alert rules, matching, atomic trigger claims
  `-- notifications  WebSocket delivery, presence, offline replay

PostgreSQL :5432      users, alerts, currency pairs, rates
Redis :6379           sessions, verification tokens, presence, offline queues
Prometheus :9090      application metrics
```

Kafka, ZooKeeper, the API Gateway, and PostgreSQL standby replication are not required by the monolith.

## Why A Modular Monolith

The current scope is one user, Rhema, with approximately 100 alerts. A single application keeps the domain boundaries clear without the operational cost of several deployable services, a message broker, broker coordination, and database replication.

Modules communicate through direct service calls. They are organized as internal packages so they can be separated into services later if traffic or team ownership requires it.

## Modules

| Module | Responsibility |
| --- | --- |
| `identity` | Canonical `User` entity, registration, login, logout, JWT creation and validation |
| `verification` | Redis-backed verification tokens, SMTP email, account confirmation |
| `rates` | External provider failover, rate persistence, REST endpoints, scheduled polling |
| `alerts` | Alert persistence, threshold matching, and atomic notification claims |
| `notifications` | STOMP/WebSocket delivery, Redis presence, offline queueing, replay |
| `security` | HTTP JWT authentication and endpoint authorization |

The canonical user entity is [User.java](tradealert-app/src/main/java/com/tradealert/identity/model/User.java). It owns the `users` table and is referenced by alerts through `user_id`.

## Prerequisites

- Docker Desktop with Docker Compose
- Java 21 LTS
- Maven 3.9 or later for local builds

Verify the local tools:

```powershell
java -version
mvn -v
docker --version
docker compose version
```

## Configuration

Create a root `.env` file. Do not commit real credentials. The tracked [.env.example](.env.example) contains safe placeholders.

```dotenv
DB_PASSWORD=change-me
JWT_SECRET=change-me-to-a-long-random-secret
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-address@gmail.com
SMTP_PASSWORD=your-gmail-app-password
RATE_CURRENCY_PAIRS=USD/NGN,EUR/USD
RATE_POLLING_INTERVAL_MS=60000
```

Important settings:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_PASSWORD` | `tradealert123` in Compose | PostgreSQL password |
| `JWT_SECRET` | development placeholder | JWT signing key; replace it |
| `SMTP_HOST` | `smtp.gmail.com` | SMTP server |
| `SMTP_PORT` | `587` | SMTP port |
| `SMTP_USERNAME` | empty | SMTP account |
| `SMTP_PASSWORD` | empty | SMTP app password |
| `RATE_CURRENCY_PAIRS` | `USD/NGN,EUR/USD` in Compose | Comma-separated pairs to poll |
| `RATE_POLLING_INTERVAL_MS` | `60000` in Compose | Polling delay in milliseconds |

## Build And Run

Build and start PostgreSQL, Redis, the monolith, and Prometheus:

```powershell
docker compose up --build -d
docker compose ps
```

The application is available at `http://localhost:8080`. Prometheus is available at `http://localhost:9090`.

Stop the stack:

```powershell
docker compose down
```

Remove local database, Redis, and Prometheus data as well:

```powershell
docker compose down -v
```

Build the application locally without tests:

```powershell
mvn -q -f tradealert-app/pom.xml clean compile
```

## Health And Metrics

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/actuator/prometheus
```

Prometheus scrapes `tradealert-app:8080/actuator/prometheus` every 10 seconds inside Compose.

## API

The monolith serves all client APIs directly on port `8080`. Protected endpoints require:

```http
Authorization: Bearer <jwt>
```

### Authentication

Register Rhema:

```powershell
$registration = @{
  email = "rhema@gmail.com"
  name = "Rhema"
  passwordHash = "RhemaPassword!23"
  emailNotifications = $true
  websocketNotifications = $true
} | ConvertTo-Json

$token = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/register" `
  -ContentType "application/json" `
  -Body $registration

$headers = @{ Authorization = "Bearer $token" }
```

Registration saves the user, creates a verification token in Redis, and sends the verification email directly through the internal verification module. No Kafka event is involved.

Log in:

```powershell
$token = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/login?email=rhema%40gmail.com&password=RhemaPassword%2123&device=web&location=home"

$headers = @{ Authorization = "Bearer $token" }
```

Log out:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/logout/1" `
  -Headers $headers
```

Endpoints:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Create a user and send verification email |
| `POST` | `/api/auth/login` | Authenticate with email and password |
| `POST` | `/api/auth/logout/{userId}` | Remove the Redis session |
| `GET` | `/api/verify/confirm?userId={id}&token={token}` | Confirm email verification |

Verification tokens are stored under `verify:token:{token}` and expire after 15 minutes. Successful confirmation marks the canonical user as verified and deletes the token.

### Rates

Manually ingest a trusted rate:

```powershell
$rate = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/rates/ingest?currencyPair=USD%2FNGN&rateValue=1500.25" `
  -Headers $headers
```

Fetch and ingest from the external providers:

```powershell
$rate = Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/rates/fetch/USD%2FNGN" `
  -Headers $headers
```

The primary provider is tried first. If it fails or times out after five seconds, the secondary provider is tried. If both fail, the endpoint returns `503` and does not create a new rate.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/rates/ingest` | Persist a supplied rate and evaluate alerts |
| `GET` | `/api/rates/fetch/{currencyPair}` | Fetch, persist, and evaluate a provider rate |
| `GET` | `/api/rates` | List stored rates |
| `GET` | `/api/rates/health` | Report provider health |

### Scheduled Rate Polling

The application enables Spring scheduling through `@EnableScheduling`. [RatePollingJob.java](tradealert-app/src/main/java/com/tradealert/rates/scheduler/RatePollingJob.java) polls every `RATE_POLLING_INTERVAL_MS` for each pair in `RATE_CURRENCY_PAIRS`.

With the default Compose configuration:

```text
USD/NGN -> fetch -> persist -> evaluate alerts
EUR/USD -> fetch -> persist -> evaluate alerts
repeat every 60 seconds
```

The polling job does nothing when the currency-pair list is empty. This makes local startup safe when external rate providers are not configured.

### Alerts

Create an alert for Rhema:

```powershell
$alertBody = @{
  user = @{ id = 1 }
  currencyPair = @{ symbol = "USD/NGN" }
  targetRate = 1500.00
  direction = "ABOVE"
} | ConvertTo-Json -Depth 4

$alert = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/alerts" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $alertBody
```

`direction` must be `ABOVE` or `BELOW`. An alert is eligible only while `notified` is `false`.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/alerts` | List alerts |
| `POST` | `/api/alerts` | Create an alert |
| `PUT` | `/api/alerts/{id}` | Update an alert |
| `DELETE` | `/api/alerts/{id}` | Delete an alert |

## Alert And Notification Flow

A rate is evaluated synchronously inside the monolith:

```text
scheduled poll or manual ingest
  -> fetch provider rate when needed
  -> save rate in PostgreSQL
  -> find matching, unnotified alerts
  -> atomically claim each alert
  -> create AlertNotification
  -> send over WebSocket when Rhema is online
  -> otherwise append JSON to Redis pending:{userId}
```

The atomic claim is:

```sql
UPDATE alerts
SET notified = true, triggered_at = :triggeredAt
WHERE id = :id AND notified = false;
```

Only a claim that updates one row can produce a notification. This prevents repeated polling or concurrent requests from sending the same alert more than once.

## WebSocket Notifications

Connect to:

```text
ws://localhost:8080/ws/alerts
```

The WebSocket handshake requires:

```http
Authorization: Bearer <jwt>
```

The JWT handshake interceptor validates the token and stores the user ID in the WebSocket session. The connection listener then records Redis presence under `presence:{userId}` and replays pending notifications.

Subscribe to:

```text
/topic/alerts/1
```

Alert messages have this shape:

```json
{
  "alertId": 1001,
  "userId": 1,
  "currencyPair": "USD/NGN",
  "targetRate": 1500.0,
  "triggeredRate": 1500.25,
  "triggeredAt": "2026-08-27T12:05:00Z"
}
```

When Rhema is offline, notifications are stored in the Redis list `pending:1`. On reconnect, they are replayed in list order and the queue is cleared.

Utility endpoints:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/notifications/replay/{userId}` | Replay pending notifications |
| `GET` | `/api/notifications/ping/{userId}` | Send a WebSocket ping |

## Redis Keys

| Key | Purpose | Lifetime |
| --- | --- | --- |
| `session:{userId}` | Active login session | Until logout or replacement |
| `login:failures:{userId}` | Failed-login tracking | Application controlled |
| `verify:token:{token}` | Email verification token | 15 minutes |
| `presence:{userId}` | Active WebSocket session IDs | Five minutes, refreshed on connect |
| `pending:{userId}` | Offline notification queue | Until replayed |

## Observability

Actuator exposes:

```text
/actuator/health
/actuator/metrics
/actuator/prometheus
```

Important metrics include:

```text
rate_provider_failures_total
rate_provider_fallbacks_total
rate_provider_latency
rates_ingested_total
verifications_sent_total
verifications_confirmed_total
verifications_failed_total
notifications_delivered_total
notifications_queued_total
notifications_replayed_total
notifications_failed_total
websocket_connections_total
websocket_disconnections_total
```

## Repository Layout

```text
TradeAlert/
|-- tradealert-app/
|   |-- src/main/java/com/tradealert/
|   |   |-- identity/
|   |   |-- verification/
|   |   |-- rates/
|   |   |-- alerts/
|   |   |-- notifications/
|   |   |-- security/
|   |   `-- TradeAlertApplication.java
|   |-- src/main/resources/application.yml
|   |-- Dockerfile
|   `-- pom.xml
|-- common/                  Kubernetes and API model artifacts retained for reference
|-- postgres/                Legacy replication scripts, no longer used by Compose
|-- docker-compose.yml
|-- prometheus.yml
`-- .env.example
```

The old service directories are retained temporarily as migration references. They are not part of the current Compose stack or monolith build.

## Security And Production Notes

- Replace the development `JWT_SECRET` and database password.
- Use an SMTP app password, not a personal email password.
- Keep `.env` and secret files out of source control.
- Use HTTPS and `wss://` outside local development.
- Restrict WebSocket origins instead of allowing all origins in production.
- Set `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` after applying a managed schema migration.
- Add integration tests for registration, verification, scheduled polling, alert claiming, offline queuing, and WebSocket reconnect before retiring the legacy directories.

## Current Scope

The Compose setup is intended for local development and integration testing for Rhema and a small alert set. The modular boundaries leave room to scale later: the rate poller, alert evaluation, or notification delivery can be extracted into separate deployables if the workload grows enough to justify that operational complexity.
