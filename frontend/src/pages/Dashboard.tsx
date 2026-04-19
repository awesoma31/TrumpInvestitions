import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import StockList from '../components/market/StockList';
import PriceChart from '../components/market/PriceChart';
import OrderForm from '../components/market/OrderForm';
import Portfolio from '../components/market/Portfolio';
import TradeHistory from '../components/market/TradeHistory';
import type { Stock } from '../types/market';
import './Dashboard.css';

const Dashboard: React.FC = () => {
  const { logout, user } = useAuth();
  const [selectedStock, setSelectedStock] = useState<Stock | null>(null);
  const [activeView, setActiveView] = useState<'market' | 'portfolio' | 'history'>('market');

  const handleStockSelect = (stock: Stock) => {
    setSelectedStock(stock);
  };

  const handleOrderCreated = () => {
    // Обновить данные после создания заявки
    setSelectedStock(null);
  };

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h1 className="dashboard-title">Trump Investitions</h1>
        <div className="dashboard-user">
          <span className="user-name">{user?.name}</span>
          <button onClick={logout} className="logout-button">
            Выйти
          </button>
        </div>
      </header>

      <nav className="dashboard-nav">
        <button
          className={`nav-button ${activeView === 'market' ? 'active' : ''}`}
          onClick={() => setActiveView('market')}
        >
          Рынок
        </button>
        <button
          className={`nav-button ${activeView === 'portfolio' ? 'active' : ''}`}
          onClick={() => setActiveView('portfolio')}
        >
          Портфель
        </button>
        <button
          className={`nav-button ${activeView === 'history' ? 'active' : ''}`}
          onClick={() => setActiveView('history')}
        >
          История
        </button>
      </nav>

      <main className="dashboard-content">
        {activeView === 'market' && (
          <>
            <StockList onStockSelect={handleStockSelect} />
            {selectedStock && (
              <>
                <PriceChart stockId={selectedStock.id} stockSymbol={selectedStock.symbol} />
                <OrderForm stock={selectedStock} onOrderCreated={handleOrderCreated} />
              </>
            )}
          </>
        )}

        {activeView === 'portfolio' && <Portfolio />}

        {activeView === 'history' && <TradeHistory />}
      </main>
    </div>
  );
};

export default Dashboard;
