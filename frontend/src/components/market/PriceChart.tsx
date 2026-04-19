import React, { useEffect, useState } from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { marketService } from '../../services/marketService';
import { mockMarketService } from '../../services/mockMarketService';
import type { PriceHistory } from '../../types/market';
import './PriceChart.css';

const USE_MOCK = true;

interface PriceChartProps {
  stockId: string;
  stockSymbol: string;
}

const PriceChart: React.FC<PriceChartProps> = ({ stockId, stockSymbol }) => {
  const [data, setData] = useState<PriceHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [period, setPeriod] = useState('1d');

  console.log('PriceChart rendered with stockId:', stockId, 'stockSymbol:', stockSymbol);

  useEffect(() => {
    loadData();
  }, [stockId, period]);

  const loadData = async () => {
    try {
      setLoading(true);
      const service = USE_MOCK ? mockMarketService : marketService;
      const history = await service.getPriceHistory(stockId, period);
      console.log('Price history loaded:', history);
      setData(history);
      setError('');
    } catch (err) {
      console.error('Error loading price history:', err);
      setError('Не удалось загрузить график');
    } finally {
      setLoading(false);
    }
  };

  const formatTimestamp = (timestamp: number) => {
    const date = new Date(timestamp);
    if (period === '1d') {
      return date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
    }
    return date.toLocaleDateString('ru-RU', { month: 'short', day: 'numeric' });
  };

  const formatPrice = (value: number) => {
    return `$${value.toFixed(2)}`;
  };

  if (loading) {
    return <div className="price-chart-loading">Загрузка графика...</div>;
  }

  if (error) {
    return <div className="price-chart-error">{error}</div>;
  }

  if (!data || data.length === 0) {
    return <div className="price-chart-loading">Нет данных для отображения</div>;
  }

  const chartData = data.map((item) => ({
    time: formatTimestamp(item.timestamp),
    price: item.price,
    volume: item.volume,
  }));

  console.log('Chart data:', chartData);

  return (
    <div className="price-chart">
      <div className="price-chart-header">
        <h2 className="price-chart-title">График цены: {stockSymbol}</h2>
        <div className="price-chart-controls">
          <button
            className={`period-button ${period === '1d' ? 'active' : ''}`}
            onClick={() => setPeriod('1d')}
          >
            1Д
          </button>
          <button
            className={`period-button ${period === '1w' ? 'active' : ''}`}
            onClick={() => setPeriod('1w')}
          >
            1Н
          </button>
          <button
            className={`period-button ${period === '1m' ? 'active' : ''}`}
            onClick={() => setPeriod('1m')}
          >
            1М
          </button>
        </div>
      </div>
      <div className="price-chart-container">
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#333" />
            <XAxis
              dataKey="time"
              stroke="#999"
              fontSize={12}
            />
            <YAxis
              stroke="#999"
              fontSize={12}
              tickFormatter={formatPrice}
            />
            <Tooltip
              contentStyle={{
                backgroundColor: '#1a1a1a',
                border: '1px solid #333',
                borderRadius: '8px',
              }}
              itemStyle={{ color: '#fff' }}
              labelStyle={{ color: '#999' }}
              formatter={(value: number) => formatPrice(value)}
            />
            <Line
              type="monotone"
              dataKey="price"
              stroke="#667eea"
              strokeWidth={2}
              dot={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};

export default PriceChart;
