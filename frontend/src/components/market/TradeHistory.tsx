import React, { useEffect, useState } from 'react';
import { marketService } from '../../services/marketService';
import { mockMarketService } from '../../services/mockMarketService';
import type { Trade, Order } from '../../types/market';
import './TradeHistory.css';

const USE_MOCK = true;

const TradeHistory: React.FC = () => {
  const [trades, setTrades] = useState<Trade[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [activeTab, setActiveTab] = useState<'trades' | 'orders'>('trades');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, 10000);
    return () => clearInterval(interval);
  }, [activeTab]);

  const loadData = async () => {
    try {
      setLoading(true);
      const service = USE_MOCK ? mockMarketService : marketService;
      if (activeTab === 'trades') {
        const tradesData = await service.getUserTrades();
        setTrades(tradesData);
      } else {
        const ordersData = await service.getUserOrders();
        setOrders(ordersData);
      }
      setError('');
    } catch (err) {
      setError('Не удалось загрузить историю');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelOrder = async (orderId: string) => {
    try {
      const service = USE_MOCK ? mockMarketService : marketService;
      await service.cancelOrder(orderId);
      loadData();
    } catch (err) {
      setError('Не удалось отменить заявку');
    }
  };

  const formatValue = (value: number) => {
    return `$${value.toFixed(2)}`;
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('ru-RU');
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'filled':
        return '#4ade80';
      case 'cancelled':
        return '#f87171';
      case 'pending':
        return '#fbbf24';
      case 'partial':
        return '#60a5fa';
      default:
        return '#999';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'filled':
        return 'Исполнена';
      case 'cancelled':
        return 'Отменена';
      case 'pending':
        return 'Ожидает';
      case 'partial':
        return 'Частично';
      default:
        return status;
    }
  };

  if (loading) {
    return <div className="trade-history-loading">Загрузка...</div>;
  }

  return (
    <div className="trade-history">
      <h2 className="trade-history-title">История</h2>

      <div className="tabs">
        <button
          className={`tab-button ${activeTab === 'trades' ? 'active' : ''}`}
          onClick={() => setActiveTab('trades')}
        >
          Сделки
        </button>
        <button
          className={`tab-button ${activeTab === 'orders' ? 'active' : ''}`}
          onClick={() => setActiveTab('orders')}
        >
          Заявки
        </button>
      </div>

      {error && <div className="trade-history-error">{error}</div>}

      {activeTab === 'trades' ? (
        <div className="history-list">
          {trades.length === 0 ? (
            <div className="history-empty">История сделок пуста</div>
          ) : (
            <>
              <div className="history-header">
                <div>Акция</div>
                <div>Тип</div>
                <div>Цена</div>
                <div>Количество</div>
                <div>Сумма</div>
                <div>Дата</div>
              </div>
              {trades.map((trade) => (
                <div key={trade.id} className="history-item">
                  <div className="item-stock">
                    <div className="stock-symbol">{trade.stockSymbol}</div>
                  </div>
                  <div className={`item-type ${trade.type}`}>
                    {trade.type === 'buy' ? 'Покупка' : 'Продажа'}
                  </div>
                  <div className="item-price">{formatValue(trade.price)}</div>
                  <div className="item-amount">{trade.amount}</div>
                  <div className="item-total">{formatValue(trade.total)}</div>
                  <div className="item-date">{formatDate(trade.timestamp)}</div>
                </div>
              ))}
            </>
          )}
        </div>
      ) : (
        <div className="history-list">
          {orders.length === 0 ? (
            <div className="history-empty">История заявок пуста</div>
          ) : (
            <>
              <div className="history-header">
                <div>Акция</div>
                <div>Тип</div>
                <div>Вид заявки</div>
                <div>Цена</div>
                <div>Количество</div>
                <div>Статус</div>
                <div>Действия</div>
              </div>
              {orders.map((order) => (
                <div key={order.id} className="history-item">
                  <div className="item-stock">
                    <div className="stock-symbol">{order.stockSymbol}</div>
                  </div>
                  <div className={`item-type ${order.type}`}>
                    {order.type === 'buy' ? 'Покупка' : 'Продажа'}
                  </div>
                  <div className="item-order-type">
                    {order.orderType === 'market' ? 'Рыночная' : 'Лимитная'}
                  </div>
                  <div className="item-price">
                    {order.price ? formatValue(order.price) : '-'}
                  </div>
                  <div className="item-amount">{order.amount}</div>
                  <div
                    className="item-status"
                    style={{ color: getStatusColor(order.status) }}
                  >
                    {getStatusText(order.status)}
                  </div>
                  <div className="item-actions">
                    {order.status === 'pending' && (
                      <button
                        onClick={() => handleCancelOrder(order.id)}
                        className="cancel-button"
                      >
                        Отменить
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </>
          )}
        </div>
      )}
    </div>
  );
};

export default TradeHistory;
