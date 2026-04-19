import React, { useState } from 'react';
import { marketService } from '../../services/marketService';
import { mockMarketService } from '../../services/mockMarketService';
import type { Stock } from '../../types/market';
import './OrderForm.css';

const USE_MOCK = true;

interface OrderFormProps {
  stock: Stock;
  onOrderCreated?: () => void;
}

const OrderForm: React.FC<OrderFormProps> = ({ stock, onOrderCreated }) => {
  const [orderType, setOrderType] = useState<'buy' | 'sell'>('buy');
  const [amount, setAmount] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const amountNum = parseFloat(amount);
    if (!amountNum || amountNum <= 0) {
      setError('Введите корректное количество');
      return;
    }

    try {
      setLoading(true);
      const service = USE_MOCK ? mockMarketService : marketService;
      await service.createOrder({
        stockId: stock.id,
        type: orderType,
        orderType: 'market',
        amount: amountNum,
      });

      setAmount('');
      onOrderCreated?.();
    } catch (err: any) {
      setError(err.message || 'Ошибка создания заявки');
    } finally {
      setLoading(false);
    }
  };

  const estimatedTotal = amount
    ? (parseFloat(amount) * stock.currentPrice).toFixed(2)
    : '0.00';

  return (
    <div className="order-form">
      <h2 className="order-form-title">Создать заявку: {stock.symbol}</h2>
      
      {error && <div className="order-form-error">{error}</div>}

      <form onSubmit={handleSubmit} className="order-form-content">
        <div className="order-type-toggle">
          <button
            type="button"
            className={`type-button ${orderType === 'buy' ? 'buy' : ''}`}
            onClick={() => setOrderType('buy')}
          >
            Купить
          </button>
          <button
            type="button"
            className={`type-button ${orderType === 'sell' ? 'sell' : ''}`}
            onClick={() => setOrderType('sell')}
          >
            Продать
          </button>
        </div>

        <div className="form-group">
          <label>Количество</label>
          <input
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="Введите количество"
            min="0"
            step="0.01"
            className="form-input"
          />
        </div>

        <div className="order-summary">
          <div className="summary-row">
            <span>Текущая цена:</span>
            <span>${stock.currentPrice.toFixed(2)}</span>
          </div>
          <div className="summary-row">
            <span>Ориентировочная сумма:</span>
            <span>${estimatedTotal}</span>
          </div>
        </div>

        <button
          type="submit"
          className={`submit-button ${orderType}`}
          disabled={loading}
        >
          {loading ? 'Создание...' : orderType === 'buy' ? 'Купить' : 'Продать'}
        </button>
      </form>
    </div>
  );
};

export default OrderForm;
