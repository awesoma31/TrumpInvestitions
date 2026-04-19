import React, { useEffect, useState } from 'react';
import { marketService } from '../../services/marketService';
import { mockMarketService } from '../../services/mockMarketService';
import type { Portfolio } from '../../types/market';
import './Portfolio.css';

const USE_MOCK = true;

const Portfolio: React.FC = () => {
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    loadPortfolio();
    const interval = setInterval(loadPortfolio, 10000); // Обновление каждые 10 секунд
    return () => clearInterval(interval);
  }, []);

  const loadPortfolio = async () => {
    try {
      const service = USE_MOCK ? mockMarketService : marketService;
      const data = await service.getPortfolio();
      setPortfolio(data);
      setError('');
    } catch (err) {
      setError('Не удалось загрузить портфель');
    } finally {
      setLoading(false);
    }
  };

  const formatValue = (value: number) => {
    return `$${value.toFixed(2)}`;
  };

  const formatPnL = (pnl: number, pnlPercentage: number) => {
    const sign = pnl >= 0 ? '+' : '';
    return `${sign}${pnl.toFixed(2)} (${sign}${pnlPercentage.toFixed(2)}%)`;
  };

  if (loading) {
    return <div className="portfolio-loading">Загрузка портфеля...</div>;
  }

  if (error) {
    return <div className="portfolio-error">{error}</div>;
  }

  if (!portfolio) {
    return null;
  }

  return (
    <div className="portfolio">
      <h2 className="portfolio-title">Инвестиционный портфель</h2>

      <div className="portfolio-summary">
        <div className="summary-card">
          <div className="summary-label">Доступные средства</div>
          <div className="summary-value">{formatValue(portfolio.cash)}</div>
        </div>

        <div className="summary-card">
          <div className="summary-label">Общая стоимость</div>
          <div className="summary-value">{formatValue(portfolio.totalValue)}</div>
        </div>

        <div className="summary-card">
          <div className="summary-label">P&L</div>
          <div className={`summary-value ${portfolio.pnl >= 0 ? 'positive' : 'negative'}`}>
            {formatPnL(portfolio.pnl, portfolio.pnlPercentage)}
          </div>
        </div>
      </div>

      <h3 className="portfolio-section-title">Активы в портфеле</h3>

      {portfolio.holdings.length === 0 ? (
        <div className="portfolio-empty">
          Портфель пуст. Начните торговать, чтобы добавить активы.
        </div>
      ) : (
        <div className="portfolio-holdings">
          <div className="holdings-header">
            <div>Акция</div>
            <div>Количество</div>
            <div>Сред. цена</div>
            <div>Тек. цена</div>
            <div>Стоимость</div>
            <div>P&L</div>
          </div>

          {portfolio.holdings.map((holding) => (
            <div key={holding.stockId} className="holding-item">
              <div className="holding-stock">
                <div className="stock-symbol">{holding.stockSymbol}</div>
                <div className="stock-name">{holding.stockName}</div>
              </div>
              <div className="holding-amount">{holding.amount}</div>
              <div className="holding-avg-price">{formatValue(holding.averagePrice)}</div>
              <div className="holding-current-price">{formatValue(holding.currentPrice)}</div>
              <div className="holding-total-value">{formatValue(holding.totalValue)}</div>
              <div className={`holding-pnl ${holding.pnl >= 0 ? 'positive' : 'negative'}`}>
                {formatPnL(holding.pnl, holding.pnlPercentage)}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default Portfolio;
