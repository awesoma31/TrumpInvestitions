CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY','SELL')),
    order_type VARCHAR(10) NOT NULL CHECK (order_type = 'MARKET'),
    quantity INT NOT NULL CHECK (quantity > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('NEW','FILLED','REJECTED','CANCELLED')),
    filled_quantity INT,
    avg_fill_price VARCHAR(50),
    rejection_reason VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    filled_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,

    trade_id UUID,
    trade_price VARCHAR(50),
    trade_gross_amount VARCHAR(50),
    trade_fee_amount VARCHAR(50),
    trade_executed_at TIMESTAMPTZ
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_user_status ON orders(user_id, status);
CREATE INDEX idx_trades_user_id ON orders(user_id) WHERE trade_id IS NOT NULL;
