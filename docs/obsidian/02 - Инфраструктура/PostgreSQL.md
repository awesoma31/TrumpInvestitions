# PostgreSQL

#infrastructure #database #postgresql

| Параметр | Значение |
|---|---|
| Версия | PostgreSQL 16 |
| Docker image | `postgres:16` |
| Порт | 5432 |

## Базы данных

| База | Пользователь | Кто использует |
|---|---|---|
| `auth_gateway` | `auth` | [[auth-gateway]] |
| `portfolio` | `trading` | [[portfolio-service]] |
| `trading` | `trading` | [[trading-service]] |

## Схема: auth_gateway

```sql
-- Пользователи системы
users (
  id            UUID PRIMARY KEY,
  email         VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at    TIMESTAMPTZ,
  updated_at    TIMESTAMPTZ
)

-- Refresh-токены
refresh_tokens (
  id         UUID PRIMARY KEY,
  user_id    UUID NOT NULL REFERENCES users(id),
  token_hash VARCHAR(255) UNIQUE NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ,
  revoked    BOOLEAN DEFAULT FALSE
)
```

## Схема: portfolio

```sql
-- Кассовый баланс
portfolios (
  user_id      UUID PRIMARY KEY,
  cash_balance DECIMAL(20,8) NOT NULL DEFAULT 0,
  updated_at   TIMESTAMPTZ
)

-- Позиции по инструментам
positions (
  id           UUID PRIMARY KEY,
  user_id      UUID NOT NULL,
  symbol       VARCHAR(20) NOT NULL,
  quantity     DECIMAL(20,8) NOT NULL DEFAULT 0,
  avg_price    DECIMAL(20,8) NOT NULL DEFAULT 0,
  realized_pnl DECIMAL(20,8) NOT NULL DEFAULT 0,
  updated_at   TIMESTAMPTZ,
  UNIQUE (user_id, symbol)
)
```

## Схема: trading

```sql
-- Ордера
orders (
  id             UUID PRIMARY KEY,
  user_id        UUID NOT NULL,
  symbol         VARCHAR(20) NOT NULL,
  side           VARCHAR(4) NOT NULL,
  type           VARCHAR(10) NOT NULL,
  quantity       DECIMAL(20,8) NOT NULL,
  status         VARCHAR(10) NOT NULL,
  avg_fill_price DECIMAL(20,8),
  created_at     TIMESTAMPTZ,
  updated_at     TIMESTAMPTZ
)

-- Сделки
trades (
  id           UUID PRIMARY KEY,
  order_id     UUID REFERENCES orders(id),
  user_id      UUID NOT NULL,
  symbol       VARCHAR(20) NOT NULL,
  side         VARCHAR(4) NOT NULL,
  quantity     DECIMAL(20,8) NOT NULL,
  price        DECIMAL(20,8) NOT NULL,
  gross_amount DECIMAL(20,8) NOT NULL,
  fee_amount   DECIMAL(20,8) NOT NULL DEFAULT 0,
  executed_at  TIMESTAMPTZ
)
```

## ER-диаграмма (trading + portfolio)

```mermaid
erDiagram
    USERS {
        uuid id PK
        string email
        string password_hash
    }
    PORTFOLIOS {
        uuid user_id PK
        decimal cash_balance
    }
    POSITIONS {
        uuid id PK
        uuid user_id FK
        string symbol
        decimal quantity
        decimal avg_price
        decimal realized_pnl
    }
    ORDERS {
        uuid id PK
        uuid user_id
        string symbol
        string side
        string status
        decimal quantity
        decimal avg_fill_price
    }
    TRADES {
        uuid id PK
        uuid order_id FK
        uuid user_id
        string symbol
        decimal price
        decimal quantity
    }
    ORDERS ||--o{ TRADES : "исполняется"
    USERS ||--o| PORTFOLIOS : "имеет"
    USERS ||--o{ POSITIONS : "держит"
    USERS ||--o{ ORDERS : "создаёт"
```

## Строки подключения

```bash
# auth_gateway
postgresql://auth:auth_pass@postgres:5432/auth_gateway

# portfolio / trading
postgresql://trading:trading_pass@postgres:5432/portfolio
postgresql://trading:trading_pass@postgres:5432/trading
```

## Связанные страницы

- [[auth-gateway]] — использует auth_gateway DB
- [[portfolio-service]] — использует portfolio DB
- [[trading-service]] — использует trading DB
