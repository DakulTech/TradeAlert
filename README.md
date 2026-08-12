TradeAlert
TradeAlert is a microservices‑based platform that monitors currency exchange rates and notifies users when their target thresholds are reached. It ensures no duplicate notifications, handles offline users gracefully, and scales to 100,000+ active alerts.

🚀 Overview
Rhema wants to exchange money but doesn’t want to keep checking rates. TradeAlert watches rates for her and sends a notification when her target is met.
Key guarantees:

Consistency: Alerts fire once and only once.

Availability: Notifications are delivered even if the user is offline.

Scalability: Designed to handle 100k+ active alerts.

🏗️ Architecture
Microservices
Rate Service → Fetches exchange rates, publishes to Kafka, stores in TimescaleDB.

Alert Service → Evaluates alerts against new rates, marks alerts as triggered.

Notification Service → Sends notifications via WebSockets, caches offline alerts in Redis.

User Service → Manages authentication, preferences, sessions.

API Gateway → Entry point for clients, routes requests to services.

Databases
Postgres + TimescaleDB → Durable storage of alerts and rates, optimized for time‑series queries.

Redis → Ephemeral cache for offline notifications and fast deduplication.

Messaging
Kafka (Pub/Sub) → Backbone for asynchronous event processing.

rates topic → new exchange rates.

alerts topic → triggered alerts.

notifications topic → delivery events.

Redis Pub/Sub → Fast fan‑out to WebSocket servers.

🔄 Sequence Diagram
mermaid
sequenceDiagram
    participant RP as Rate Provider
    participant RS as Rate Service
    participant K as Kafka
    participant AS as Alert Service
    participant DB as TimescaleDB
    participant NS as Notification Service
    participant R as Redis
    participant WS as WebSocket Server
    participant U as User Client

    RP ->> RS: Fetch new rate
    RS ->> K: Publish rate event
    K ->> AS: Consume rate event
    AS ->> DB: Check alerts, mark notified
    AS ->> K: Publish alertTriggered event
    K ->> NS: Consume alertTriggered
    NS ->> WS: Push notification (if online)
    NS ->> R: Store pending notification (if offline)
    U ->> WS: Reconnect
    WS ->> R: Fetch pending notifications
    WS ->> U: Deliver notification
⚖️ Availability vs Consistency
Consistency priority:

Alerts marked notified=true in Postgres ensure no duplicates.

Availability priority:

Kafka ensures rate events aren’t lost.

Redis ensures offline users still receive notifications.

📈 Scaling to 100k+ Alerts
Kafka partitions → parallel processing by currency pair.

TimescaleDB hypertables → efficient ingestion of millions of rate updates.

Redis cluster → high availability for offline cache.

Horizontal scaling → Alert Service and Notification Service scale independently.

📉 What’s Left Out Initially
Advanced analytics dashboards.

Multi‑currency aggregation.

Complex retry logic (start with exponential backoff).

✅ Key Behaviors
Users receive one notification per alert.

Offline users get notifications when they reconnect.

Rate provider failures don’t block other alerts.

System grows gracefully with user base.

🛠️ Tech Stack
Java (Spring Boot) → microservices implementation.

Postgres + TimescaleDB → durable + time‑series storage.

Redis → offline cache + pub/sub.

Kafka → async event backbone.

WebSockets → real‑time notifications.

Docker/Kubernetes → deployment and scaling.

📂 Repository Structure
Code
TradeAlert/
├── api-gateway/
│   ├── src/
│   └── Dockerfile
├── rate-service/
│   ├── src/
│   └── Dockerfile
├── alert-service/
│   ├── src/
│   └── Dockerfile
├── notification-service/
│   ├── src/
│   └── Dockerfile
├── user-service/
│   ├── src/
│   └── Dockerfile
├── common/
│   ├── models/        # Shared DTOs, event schemas
│   ├── utils/         # Common utilities
│   └── config/        # Kafka, Redis, DB configs
├── infra/
│   ├── docker-compose.yml
│   ├── k8s/           # Kubernetes manifests
│   └── scripts/       # Deployment scripts
└── README.md
