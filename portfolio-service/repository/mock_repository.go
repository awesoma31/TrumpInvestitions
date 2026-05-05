package repository

import (
	"context"
	"sync"
	"time"

	"github.com/awesoma31/portfolio-service/models"
	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

// MockRepository is an in-memory implementation used for unit tests
type MockRepository struct {
	mu         sync.RWMutex
	portfolios map[int64]*models.Portfolio
	positions  map[int64]map[string]*models.Position
	orders     []models.Order
	trades     []models.Trade
	pingErr    error
}

func NewMockRepository() *MockRepository {
	return &MockRepository{
		portfolios: make(map[int64]*models.Portfolio),
		positions:  make(map[int64]map[string]*models.Position),
	}
}

func (m *MockRepository) SetPingErr(err error) {
	m.pingErr = err
}

func (m *MockRepository) Ping(_ context.Context) error {
	return m.pingErr
}

func (m *MockRepository) GetPortfolio(_ context.Context, userID int64) (*models.Portfolio, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	p, ok := m.portfolios[userID]
	if !ok {
		return nil, nil
	}
	cp := *p
	return &cp, nil
}

func (m *MockRepository) CreatePortfolio(_ context.Context, userID int64) (*models.Portfolio, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	p := &models.Portfolio{UserID: userID, CashBalance: decimal.Zero, UpdatedAt: time.Now()}
	m.portfolios[userID] = p
	cp := *p
	return &cp, nil
}

func (m *MockRepository) GetOrCreatePortfolio(ctx context.Context, userID int64) (*models.Portfolio, error) {
	p, err := m.GetPortfolio(ctx, userID)
	if err != nil {
		return nil, err
	}
	if p == nil {
		return m.CreatePortfolio(ctx, userID)
	}
	return p, nil
}

func (m *MockRepository) UpdateCashBalance(_ context.Context, userID int64, newBalance decimal.Decimal) (*models.Portfolio, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	now := time.Now()
	m.portfolios[userID] = &models.Portfolio{UserID: userID, CashBalance: newBalance, UpdatedAt: now}
	cp := *m.portfolios[userID]
	return &cp, nil
}

func (m *MockRepository) GetPositions(_ context.Context, userID int64, symbol *string) ([]models.Position, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	posMap, ok := m.positions[userID]
	if !ok {
		return nil, nil
	}
	var result []models.Position
	for _, p := range posMap {
		if symbol != nil && p.Symbol != *symbol {
			continue
		}
		result = append(result, *p)
	}
	return result, nil
}

func (m *MockRepository) GetPosition(_ context.Context, userID int64, symbol string) (*models.Position, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	posMap, ok := m.positions[userID]
	if !ok {
		return nil, nil
	}
	p, ok := posMap[symbol]
	if !ok {
		return nil, nil
	}
	cp := *p
	return &cp, nil
}

func (m *MockRepository) UpsertPosition(_ context.Context, userID int64, symbol string, quantity int, avgPrice, realizedPnl decimal.Decimal) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.positions[userID] == nil {
		m.positions[userID] = make(map[string]*models.Position)
	}
	m.positions[userID][symbol] = &models.Position{
		UserID:      userID,
		Symbol:      symbol,
		Quantity:    quantity,
		AvgPrice:    avgPrice,
		RealizedPnl: realizedPnl,
		UpdatedAt:   time.Now(),
	}
	return nil
}

func (m *MockRepository) ListOrders(_ context.Context, userID int64, status *string, symbol *string, limit, offset int) ([]models.Order, int, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var filtered []models.Order
	for _, o := range m.orders {
		if o.UserID != userID {
			continue
		}
		if status != nil && string(o.Status) != *status {
			continue
		}
		if symbol != nil && o.Symbol != *symbol {
			continue
		}
		filtered = append(filtered, o)
	}
	total := len(filtered)
	if offset >= len(filtered) {
		return nil, total, nil
	}
	end := offset + limit
	if end > len(filtered) {
		end = len(filtered)
	}
	return filtered[offset:end], total, nil
}

func (m *MockRepository) InsertOrder(_ context.Context, order *models.Order) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.orders = append(m.orders, *order)
	return nil
}

func (m *MockRepository) UpdateOrderStatus(_ context.Context, orderID uuid.UUID, status models.OrderStatus, avgFillPrice *string, rejectionReason *string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	for i := range m.orders {
		if m.orders[i].ID == orderID {
			m.orders[i].Status = status
			m.orders[i].AvgFillPrice = avgFillPrice
			m.orders[i].RejectionReason = rejectionReason
			m.orders[i].UpdatedAt = time.Now()
			return nil
		}
	}
	return nil
}

func (m *MockRepository) ListTrades(_ context.Context, userID int64, symbol *string, side *string, limit, offset int) ([]models.Trade, int, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var filtered []models.Trade
	for _, t := range m.trades {
		if t.UserID != userID {
			continue
		}
		if symbol != nil && t.Symbol != *symbol {
			continue
		}
		if side != nil && string(t.Side) != *side {
			continue
		}
		filtered = append(filtered, t)
	}
	total := len(filtered)
	if offset >= len(filtered) {
		return nil, total, nil
	}
	end := offset + limit
	if end > len(filtered) {
		end = len(filtered)
	}
	return filtered[offset:end], total, nil
}

func (m *MockRepository) InsertTrade(_ context.Context, trade *models.Trade) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.trades = append(m.trades, *trade)
	return nil
}
