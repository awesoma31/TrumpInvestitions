CREATE TABLE IF NOT EXISTS portfolios (
    user_id    BIGINT PRIMARY KEY,
    cash_balance NUMERIC(20,2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS positions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    symbol      VARCHAR(20) NOT NULL,
    quantity    INT NOT NULL DEFAULT 0,
    avg_price   NUMERIC(20,8) NOT NULL DEFAULT 0,
    realized_pnl NUMERIC(20,2) NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, symbol)
);

CREATE INDEX idx_positions_user ON positions(user_id);

CREATE TABLE IF NOT EXISTS orders (
    id               UUID PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    symbol           VARCHAR(20) NOT NULL,
    side             VARCHAR(4) NOT NULL,
    quantity         INT NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'NEW',
    avg_fill_price   NUMERIC(20,8),
    rejection_reason VARCHAR(100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user ON orders(user_id);

CREATE TABLE IF NOT EXISTS trades (
    id           UUID PRIMARY KEY,
    order_id     UUID NOT NULL REFERENCES orders(id),
    user_id      BIGINT NOT NULL,
    symbol       VARCHAR(20) NOT NULL,
    side         VARCHAR(4) NOT NULL,
    quantity     INT NOT NULL,
    price        NUMERIC(20,8) NOT NULL,
    gross_amount NUMERIC(20,2) NOT NULL,
    fee_amount   NUMERIC(20,2),
    executed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trades_user ON trades(user_id);
