# Component Diagram

```mermaid
graph TB
    subgraph "Client Layer"
        A[Android App<br/>Kotlin + Compose]
        B[Cross-platform App<br/>React Native]
    end

    subgraph "API Gateway"
        C[API Gateway<br/>Ktor]
        C1[Authentication]
        C2[Authorization]
        C3[REST Endpoints]
        C4[WebSocket Server]
        C5[Rate Limiting]
        C6[Request Validation]
    end

    subgraph "Microservices Layer"
        D[Trading Service<br/>Kotlin]
        E[Market Data Service<br/>Go]
        F[Analytics Service<br/>Kotlin]
    end

    subgraph "Data Layer"
        G[(PostgreSQL<br/>OLTP)]
        H[(ClickHouse<br/>OLAP)]
        I[(Redis<br/>Cache + Pub/Sub)]
    end

    subgraph "External"
        J[Linux Driver<br/>C]
    end

    %% Connections
    A --> C
    B --> C
    C --> D
    C --> E
    C --> F
    D --> G
    D --> I
    E --> H
    E --> I
    F --> H
    J --> E

    %% Internal Gateway components
    C --> C1
    C --> C2
    C --> C3
    C --> C4
    C --> C5
    C --> C6

    %% Styling
    classDef client fill:#e1f5fe
    classDef gateway fill:#f3e5f5
    classDef service fill:#e8f5e9
    classDef data fill:#fff3e0
    classDef external fill:#fce4ec

    class A,B client
    class C,C1,C2,C3,C4,C5,C6 gateway
    class D,E,F service
    class G,H,I data
    class J external
```

## Component Responsibilities

### Client Layer
- **Android App**: Native Android application using Kotlin and Compose
- **Cross-platform App**: React Native application for multiple platforms

### API Gateway
- **Authentication**: User identity verification
- **Authorization**: Access control and permissions
- **REST Endpoints**: Standard HTTP API endpoints
- **WebSocket Server**: Real-time communication for quotes
- **Rate Limiting**: Request throttling and protection
- **Request Validation**: Input validation and sanitization

### Microservices
- **Trading Service**: Order creation, balance checking, trade execution
- **Market Data Service**: Quote processing, normalization, distribution
- **Analytics Service**: Data aggregation, charting, statistics

### Data Stores
- **PostgreSQL**: Transactional data (users, accounts, orders, portfolios)
- **ClickHouse**: Analytical data (quotes history, trades, aggregations)
- **Redis**: Caching and real-time messaging (quotes, sessions, pub/sub)

### External Systems
- **Linux Driver**: Quote generation source written in C