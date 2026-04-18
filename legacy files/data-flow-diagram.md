# Data Flow Diagram

```mermaid
graph LR
    subgraph "Quote Flow"
        direction TB
        Q1[Linux Driver<br/>C] -->|Raw Quotes| Q2[Market Data Service<br/>Go]
        Q2 -->|Normalized Quotes| Q3[Redis Pub/Sub<br/>Real-time]
        Q2 -->|Historical Data| Q4[ClickHouse<br/>Storage]
        Q3 -->|WebSocket| Q5[API Gateway<br/>Ktor]
        Q5 -->|Real-time Quotes| Q6[Clients<br/>Apps]
        Q4 -->|Analytics| Q7[Analytics Service<br/>Kotlin]
        Q7 -->|Charts & Stats| Q5
    end

    subgraph "Trade Flow"
        direction TB
        T1[Clients<br/>Apps] -->|Trade Requests| T2[API Gateway<br/>Ktor]
        T2 -->|Validated Requests| T3[Trading Service<br/>Kotlin]
        T3 -->|Transaction| T4[PostgreSQL<br/>OLTP]
        T4 -->|Confirmation| T3
        T3 -->|Trade Events| T5[Redis Pub/Sub<br/>Events]
        T5 -->|Real-time Updates| T2
        T2 -->|Trade Confirmations| T1
        T5 -->|Analytics Data| T6[ClickHouse<br/>Event Store]
        T6 -->|Historical Analysis| T7[Analytics Service<br/>Kotlin]
    end

    subgraph "Data Storage Patterns"
        direction LR
        S1[(PostgreSQL<br/>Transactional)]
        S2[(ClickHouse<br/>Analytical)]
        S3[(Redis<br/>Cache & Messaging)]
    end

    %% Styling
    classDef quote fill:#e3f2fd
    classDef trade fill:#f3e5f5
    classDef storage fill:#fff3e0
    classDef service fill:#e8f5e9

    class Q1,Q2,Q3,Q4,Q5,Q6,Q7 quote
    class T1,T2,T3,T4,T5,T6,T7 trade
    class S1,S2,S3 storage
```

## Data Flow Details

### Quote Processing Flow
1. **Generation**: Linux Driver generates raw quote data
2. **Normalization**: Market Data Service processes and normalizes quotes
3. **Distribution**: 
   - Real-time quotes via Redis Pub/Sub to WebSocket clients
   - Historical data stored in ClickHouse for analytics
4. **Analytics**: Analytics Service processes historical data for charts and statistics
5. **Delivery**: API Gateway serves both real-time and analytical data to clients

### Trade Execution Flow
1. **Request**: Clients submit trade requests through API Gateway
2. **Validation**: Trading Service validates requests and checks balances
3. **Execution**: PostgreSQL transaction records the trade
4. **Notification**: Trade events published via Redis Pub/Sub
5. **Confirmation**: Real-time updates sent to clients
6. **Analytics**: Trade data stored in ClickHouse for historical analysis

### Data Storage Strategy
- **PostgreSQL**: ACID-compliant transactional data
- **ClickHouse**: Columnar storage for time-series analytics
- **Redis**: In-memory caching and pub/sub messaging

## Key Data Flows

### Real-time Quote Streaming
```
Linux Driver → Market Data Service → Redis Pub/Sub → API Gateway WebSocket → Clients
```

### Trade Execution
```
Clients → API Gateway → Trading Service → PostgreSQL → Redis Pub/Sub → Clients
```

### Analytics Pipeline
```
Historical Data → ClickHouse → Analytics Service → API Gateway → Clients