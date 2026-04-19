import React, { useEffect, useState } from 'react';
import { marketService } from '../../services/marketService';
import { mockMarketService } from '../../services/mockMarketService';
import type { Stock } from '../../types/market';
import './StockList.css';

const USE_MOCK = true;

interface StockListProps {
  onStockSelect?: (stock: Stock) => void;
}

const StockList: React.FC<StockListProps> = ({ onStockSelect }) => {
  const [stocks, setStocks] = useState<Stock[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    loadStocks();
    const interval = setInterval(loadStocks, 5000); // Обновление каждые 5 секунд
    return () => clearInterval(interval);
  }, []);

  const loadStocks = async () => {
    try {
      const service = USE_MOCK ? mockMarketService : marketService;
      const data = await service.getStocks();
      setStocks(data);
      setError('');
    } catch (err) {
      setError('Не удалось загрузить акции');
    } finally {
      setLoading(false);
    }
  };

  const formatPrice = (price: number) => {
    return price.toFixed(2);
  };

  const formatChange = (change: number) => {
    const sign = change >= 0 ? '+' : '';
    return `${sign}${change.toFixed(2)}%`;
  };

  if (loading) {
    return <div className="stock-list-loading">Загрузка...</div>;
  }

  if (error) {
    return <div className="stock-list-error">{error}</div>;
  }

  return (
    <div className="stock-list">
      <h2 className="stock-list-title">Список акций</h2>
      <div className="stock-list-header">
        <div>Символ</div>
        <div>Название</div>
        <div>Цена</div>
        <div>Bid</div>
        <div>Ask</div>
        <div>Изменение</div>
      </div>
      <div className="stock-list-items">
        {stocks.map((stock) => (
          <div
            key={stock.id}
            className="stock-list-item"
            onClick={() => onStockSelect?.(stock)}
          >
            <div className="stock-symbol">{stock.symbol}</div>
            <div className="stock-name">{stock.name}</div>
            <div className="stock-price">${formatPrice(stock.currentPrice)}</div>
            <div className="stock-bid">${formatPrice(stock.highestBid)}</div>
            <div className="stock-ask">${formatPrice(stock.lowestAsk)}</div>
            <div className={`stock-change ${stock.change24h >= 0 ? 'positive' : 'negative'}`}>
              {formatChange(stock.change24h)}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default StockList;
