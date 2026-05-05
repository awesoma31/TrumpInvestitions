package repository

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
	"time"

	"github.com/jmoiron/sqlx"
	"github.com/vnikolaenko/trading-service/internal/domain"
)

type PostgresOrderRepo struct {
	db *sqlx.DB
}

func NewPostgresOrderRepo(db *sqlx.DB) *PostgresOrderRepo {
	return &PostgresOrderRepo{db: db}
}

func (r *PostgresOrderRepo) CreateOrder(ctx context.Context, order *domain.OrderRecord) error {
	query := `INSERT INTO orders (
		id, user_id, symbol, side, order_type, quantity, status,
		created_at, updated_at
	) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`
	order.CreatedAt = time.Now()
	order.UpdatedAt = order.CreatedAt
	_, err := r.db.ExecContext(ctx, query,
		order.ID, order.UserID, order.Symbol, order.Side, order.OrderType,
		order.Quantity, order.Status, order.CreatedAt, order.UpdatedAt,
	)
	return err
}

func (r *PostgresOrderRepo) GetOrderByID(ctx context.Context, orderID, userID string) (*domain.OrderRecord, error) {
	order := &domain.OrderRecord{}
	err := r.db.GetContext(ctx, order, `SELECT * FROM orders WHERE id=$1 AND user_id=$2`, orderID, userID)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return order, err
}

func (r *PostgresOrderRepo) ListOrders(ctx context.Context, userID int64, filters OrderFilter) ([]domain.OrderRecord, int, error) {
	where := []string{"user_id = $1"}
	args := []interface{}{userID}
	i := 2
	if filters.Status != nil {
		where = append(where, fmt.Sprintf("status = $%d", i))
		args = append(args, *filters.Status)
		i++
	}
	if filters.Symbol != nil {
		where = append(where, fmt.Sprintf("symbol = $%d", i))
		args = append(args, *filters.Symbol)
		i++
	}
	if filters.Side != nil {
		where = append(where, fmt.Sprintf("side = $%d", i))
		args = append(args, *filters.Side)
		i++
	}

	countQ := fmt.Sprintf("SELECT COUNT(*) FROM orders WHERE %s", strings.Join(where, " AND "))
	var total int
	if err := r.db.GetContext(ctx, &total, countQ, args...); err != nil {
		return nil, 0, err
	}

	limit := filters.Limit
	if limit <= 0 {
		limit = 50
	}
	offset := filters.Offset
	if offset < 0 {
		offset = 0
	}

	query := fmt.Sprintf("SELECT * FROM orders WHERE %s ORDER BY created_at DESC LIMIT $%d OFFSET $%d",
		strings.Join(where, " AND "), i, i+1)
	args = append(args, limit, offset)
	var orders []domain.OrderRecord
	if err := r.db.SelectContext(ctx, &orders, query, args...); err != nil {
		return nil, 0, err
	}
	return orders, total, nil
}

func (r *PostgresOrderRepo) UpdateOrderStatus(ctx context.Context, orderID, userID string, status domain.OrderStatus, reason *string) error {
	query := `UPDATE orders SET status=$1, rejection_reason=$2, updated_at=$3 WHERE id=$4 AND user_id=$5`
	_, err := r.db.ExecContext(ctx, query, status, reason, time.Now(), orderID, userID)
	return err
}

func (r *PostgresOrderRepo) FillOrder(ctx context.Context, orderID string, tradeID string, price, grossAmount string, executedAt time.Time) error {
	query := `UPDATE orders SET
		status = 'FILLED',
		trade_id = $1,
		trade_price = $2,
		trade_gross_amount = $3,
		trade_executed_at = $4,
		filled_quantity = quantity,
		avg_fill_price = $2,
		filled_at = $4,
		updated_at = $5
	WHERE id = $6`
	_, err := r.db.ExecContext(ctx, query, tradeID, price, grossAmount, executedAt, time.Now(), orderID)
	return err
}

func (r *PostgresOrderRepo) CancelOrder(ctx context.Context, orderID, userID string) error {
	now := time.Now()
	query := `UPDATE orders SET status='CANCELLED', cancelled_at=$1, updated_at=$2 WHERE id=$3 AND user_id=$4`
	_, err := r.db.ExecContext(ctx, query, now, now, orderID, userID)
	return err
}

func (r *PostgresOrderRepo) GetTradeByID(ctx context.Context, tradeID, userID string) (*domain.OrderRecord, error) {
	order := &domain.OrderRecord{}
	err := r.db.GetContext(ctx, order, `SELECT * FROM orders WHERE trade_id=$1 AND user_id=$2`, tradeID, userID)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return order, err
}

func (r *PostgresOrderRepo) ListTrades(ctx context.Context, userID int64, filters TradeFilter) ([]domain.OrderRecord, int, error) {
	where := []string{"user_id = $1", "trade_id IS NOT NULL"}
	args := []interface{}{userID}
	i := 2
	if filters.Symbol != nil {
		where = append(where, fmt.Sprintf("symbol = $%d", i))
		args = append(args, *filters.Symbol)
		i++
	}
	if filters.Side != nil {
		where = append(where, fmt.Sprintf("side = $%d", i))
		args = append(args, *filters.Side)
		i++
	}
	countQ := fmt.Sprintf("SELECT COUNT(*) FROM orders WHERE %s", strings.Join(where, " AND "))
	var total int
	if err := r.db.GetContext(ctx, &total, countQ, args...); err != nil {
		return nil, 0, err
	}

	limit := filters.Limit
	if limit <= 0 {
		limit = 50
	}
	offset := filters.Offset
	if offset < 0 {
		offset = 0
	}

	query := fmt.Sprintf("SELECT * FROM orders WHERE %s ORDER BY trade_executed_at DESC LIMIT $%d OFFSET $%d",
		strings.Join(where, " AND "), i, i+1)
	args = append(args, limit, offset)
	var trades []domain.OrderRecord
	if err := r.db.SelectContext(ctx, &trades, query, args...); err != nil {
		return nil, 0, err
	}
	return trades, total, nil
}
