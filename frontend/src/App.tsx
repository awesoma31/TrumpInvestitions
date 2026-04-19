import { useState } from 'react';
import { useAuth } from './context/AuthContext';
import { Auth } from './components/auth';
import Dashboard from './pages/Dashboard';
import StockList from './components/market/StockList';
import PriceChart from './components/market/PriceChart';
import type { Stock } from './types/market';

// Режим демонстрации - установите в true для просмотра без бэкенда и авторизации
const DEMO_MODE = true;

function App() {
  const { login, register, isAuthenticated, isLoading } = useAuth();
  const [selectedStock, setSelectedStock] = useState<Stock | null>(null);

  if (isLoading) {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        minHeight: '100vh',
        background: '#000000',
        color: '#ffffff'
      }}>
        Загрузка...
      </div>
    );
  }

  // Режим демонстрации - показываем Dashboard без авторизации
  if (DEMO_MODE) {
    return (
      <div style={{ background: '#000000', minHeight: '100vh' }}>
        <div style={{ 
          position: 'fixed', 
          top: '10px', 
          right: '10px', 
          background: '#667eea', 
          color: '#fff', 
          padding: '8px 16px', 
          borderRadius: '6px', 
          fontSize: '12px',
          zIndex: 1000
        }}>
          РЕЖИМ ДЕМОНСТРАЦИИ
        </div>
        <Dashboard />
      </div>
    );
  }

  if (isAuthenticated) {
    return <Dashboard />;
  }

  return (
    <div style={{ background: '#000000', minHeight: '100vh' }}>
      <Auth 
        onLogin={login}
        onRegister={register}
      />
      <div style={{ padding: '24px' }}>
        <h2 style={{ color: '#ffffff', marginBottom: '20px', textAlign: 'center' }}>
          Рынок акций (только просмотр)
        </h2>
        <StockList onStockSelect={setSelectedStock} />
        {selectedStock && (
          <PriceChart stockId={selectedStock.id} stockSymbol={selectedStock.symbol} />
        )}
      </div>
    </div>
  );
}

export default App
