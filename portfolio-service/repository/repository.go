package repository

import (
	"context"
	"database/sql"
	"strconv"
	"time"

	"github.com/awesoma31/portfolio-service/models"
	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

type Repository interface {
	GetPortfolio(ctx context.Context, userID int64) (*models.Portfolio, error)
	CreatePortfolio(ctx context.Context, userID int64) (*models.Portfolio, error)
	GetOrCreatePortfolio(ctx context.Context, userID int64) (*models.Portfolio, error)
	UpdateCashBalance(ctx context.Context, userID int64, newBalance decimal.Decimal) (*models.Portfolio, error)

	GetPositions(ctx context.Context, userID int64, symbol *string) ([]models.Position, error)
	GetPosition(ctx context.Context, userID int64, symbol string) (*models.Position, error)
	UpsertPosition(ctx context.Context, userID int64, symbol string, quantity int, avgPrice, realizedPnl decimal.Decimal) error

	ListOrders(ctx context.Context, userID int64, status *string, symbol *string, limit, offset int) ([]models.Order, int, error)
	InsertOrder(ctx context.Context, order *models.Order) error
	UpdateOrderStatus(ctx context.Context, orderID uuid.UUID, status models.OrderStatus, avgFillPrice *string, rejectionReason *string) error

	ListTrades(ctx context.Context, userID int64, symbol *string, side *string, limit, offset int) ([]models.Trade, int, error)
	InsertTrade(ctx context.Context, trade *models.Trade) error

	Ping(ctx context.Context) error
}

type PostgresRepository struct {
	db *sql.DB
}

func NewPostgresRepository(db *sql.DB) *PostgresRepository {
	return &PostgresRepository{db: db}
}

func (r *PostgresRepository) Ping(ctx context.Context) error {
	return r.db.PingContext(ctx)
}

func (r *PostgresRepository) GetPortfolio(ctx context.Context, userID int64) (*models.Portfolio, error) {
	p := &models.Portfolio{}
	err := r.db.QueryRowContext(ctx,
		"SELECT user_id, cash_balance, updated_at FROM portfolios WHERE user_id = $1", userID,
	).Scan(&p.UserID, &p.CashBalance, &p.UpdatedAt)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return p, err
}

func (r *PostgresRepository) CreatePortfolio(ctx context.Context, userID int64) (*models.Portfolio, error) {
	p := &models.Portfolio{UserID: userID, CashBalance: decimal.Zero, UpdatedAt: time.Now()}
	_, err := r.db.ExecContext(ctx,
		"INSERT INTO portfolios (user_id, cash_balance, updated_at) VALUES ($1, $2, $3)",
		p.UserID, p.CashBalance, p.UpdatedAt,
	)
	return p, err
}

func (r *PostgresRepository) GetOrCreatePortfolio(ctx context.Context, userID int64) (*models.Portfolio, error) {
	p, err := r.GetPortfolio(ctx, userID)
	if err != nil {
		return nil, err
	}
	if p == nil {
		return r.CreatePortfolio(ctx, userID)
	}
	return p, nil
}

func (r *PostgresRepository) UpdateCashBalance(ctx context.Context, userID int64, newBalance decimal.Decimal) (*models.Portfolio, error) {
	now := time.Now()
	_, err := r.db.ExecContext(ctx,
		"UPDATE portfolios SET cash_balance = $1, updated_at = $2 WHERE user_id = $3",
		newBalance, now, userID,
	)
	if err != nil {
		return nil, err
	}
	return &models.Portfolio{UserID: userID, CashBalance: newBalance, UpdatedAt: now}, nil
}

func (r *PostgresRepository) GetPositions(ctx context.Context, userID int64, symbol *string) ([]models.Position, error) {
	query := "SELECT id, user_id, symbol, quantity, avg_price, realized_pnl, updated_at FROM positions WHERE user_id = $1"
	args := []interface{}{userID}
	if symbol != nil {
		query += " AND symbol = $2"
		args = append(args, *symbol)
	}
	query += " ORDER BY symbol"

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var positions []models.Position
	for rows.Next() {
		var p models.Position
		if err := rows.Scan(&p.ID, &p.UserID, &p.Symbol, &p.Quantity, &p.AvgPrice, &p.RealizedPnl, &p.UpdatedAt); err != nil {
			return nil, err
		}
		positions = append(positions, p)
	}
	return positions, rows.Err()
}

func (r *PostgresRepository) GetPosition(ctx context.Context, userID int64, symbol string) (*models.Position, error) {
	p := &models.Position{}
	err := r.db.QueryRowContext(ctx,
		"SELECT id, user_id, symbol, quantity, avg_price, realized_pnl, updated_at FROM positions WHERE user_id = $1 AND symbol = $2",
		userID, symbol,
	).Scan(&p.ID, &p.UserID, &p.Symbol, &p.Quantity, &p.AvgPrice, &p.RealizedPnl, &p.UpdatedAt)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return p, err
}

func (r *PostgresRepository) UpsertPosition(ctx context.Context, userID int64, symbol string, quantity int, avgPrice, realizedPnl decimal.Decimal) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO positions (user_id, symbol, quantity, avg_price, realized_pnl, updated_at)
		VALUES ($1, $2, $3, $4, $5, NOW())
		ON CONFLICT (user_id, symbol)
		DO UPDATE SET quantity = $3, avg_price = $4, realized_pnl = $5, updated_at = NOW()
	`, userID, symbol, quantity, avgPrice, realizedPnl)
	return err
}

func (r *PostgresRepository) ListOrders(ctx context.Context, userID int64, status *string, symbol *string, limit, offset int) ([]models.Order, int, error) {
	countQuery := "SELECT COUNT(*) FROM orders WHERE user_id = $1"
	query := "SELECT id, user_id, symbol, side, quantity, status, avg_fill_price, rejection_reason, created_at, updated_at FROM orders WHERE user_id = $1"
	args := []interface{}{userID}
	argIdx := 2

	if status != nil {
		filter := " AND status = $" + itoa(argIdx)
		countQuery += filter
		query += filter
		args = append(args, *status)
		argIdx++
	}
	if symbol != nil {
		filter := " AND symbol = $" + itoa(argIdx)
		countQuery += filter
		query += filter
		args = append(args, *symbol)
		argIdx++
	}

	var total int
	if err := r.db.QueryRowContext(ctx, countQuery, args...).Scan(&total); err != nil {
		return nil, 0, err
	}

	query += " ORDER BY created_at DESC LIMIT $" + itoa(argIdx) + " OFFSET $" + itoa(argIdx+1)
	args = append(args, limit, offset)

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var orders []models.Order
	for rows.Next() {
		var o models.Order
		if err := rows.Scan(&o.ID, &o.UserID, &o.Symbol, &o.Side, &o.Quantity, &o.Status, &o.AvgFillPrice, &o.RejectionReason, &o.CreatedAt, &o.UpdatedAt); err != nil {
			return nil, 0, err
		}
		orders = append(orders, o)
	}
	return orders, total, rows.Err()
}

func (r *PostgresRepository) InsertOrder(ctx context.Context, order *models.Order) error {
	_, err := r.db.ExecContext(ctx,
		"INSERT INTO orders (id, user_id, symbol, side, quantity, status, avg_fill_price, rejection_reason, created_at, updated_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)",
		order.ID, order.UserID, order.Symbol, order.Side, order.Quantity, order.Status, order.AvgFillPrice, order.RejectionReason, order.CreatedAt, order.UpdatedAt,
	)
	return err
}

func (r *PostgresRepository) UpdateOrderStatus(ctx context.Context, orderID uuid.UUID, status models.OrderStatus, avgFillPrice *string, rejectionReason *string) error {
	_, err := r.db.ExecContext(ctx,
		"UPDATE orders SET status = $1, avg_fill_price = $2, rejection_reason = $3, updated_at = NOW() WHERE id = $4",
		status, avgFillPrice, rejectionReason, orderID,
	)
	return err
}

func (r *PostgresRepository) ListTrades(ctx context.Context, userID int64, symbol *string, side *string, limit, offset int) ([]models.Trade, int, error) {
	countQuery := "SELECT COUNT(*) FROM trades WHERE user_id = $1"
	query := "SELECT id, order_id, user_id, symbol, side, quantity, price, gross_amount, fee_amount, executed_at FROM trades WHERE user_id = $1"
	args := []interface{}{userID}
	argIdx := 2

	if symbol != nil {
		filter := " AND symbol = $" + itoa(argIdx)
		countQuery += filter
		query += filter
		args = append(args, *symbol)
		argIdx++
	}
	if side != nil {
		filter := " AND side = $" + itoa(argIdx)
		countQuery += filter
		query += filter
		args = append(args, *side)
		argIdx++
	}

	var total int
	if err := r.db.QueryRowContext(ctx, countQuery, args...).Scan(&total); err != nil {
		return nil, 0, err
	}

	query += " ORDER BY executed_at DESC LIMIT $" + itoa(argIdx) + " OFFSET $" + itoa(argIdx+1)
	args = append(args, limit, offset)

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var trades []models.Trade
	for rows.Next() {
		var t models.Trade
		if err := rows.Scan(&t.ID, &t.OrderID, &t.UserID, &t.Symbol, &t.Side, &t.Quantity, &t.Price, &t.GrossAmount, &t.FeeAmount, &t.ExecutedAt); err != nil {
			return nil, 0, err
		}
		trades = append(trades, t)
	}
	return trades, total, rows.Err()
}

func (r *PostgresRepository) InsertTrade(ctx context.Context, trade *models.Trade) error {
	_, err := r.db.ExecContext(ctx,
		"INSERT INTO trades (id, order_id, user_id, symbol, side, quantity, price, gross_amount, fee_amount, executed_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)",
		trade.ID, trade.OrderID, trade.UserID, trade.Symbol, trade.Side, trade.Quantity, trade.Price, trade.GrossAmount, trade.FeeAmount, trade.ExecutedAt,
	)
	return err
}

func itoa(i int) string {
	return strconv.Itoa(i)
}
