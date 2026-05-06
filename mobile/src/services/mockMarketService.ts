import type { Stock, PriceHistory, OrderBook, Order, Trade, Portfolio } from '../types/market';

// Функция загрузки данных из JSON файлов
const loadCandleData = async (symbol: string): Promise<PriceHistory[]> => {
  try {
    const response = await fetch(`../assets/market_data/market_data_${symbol.toLowerCase()}_candles.json`);
    const data = await response.json();
    
    return data.map((candle: any) => ({
      timestamp: new Date(candle.timestamp).getTime(),
      price: parseFloat(candle.close),
      volume: candle.volume,
    }));
  } catch (error) {
    console.error(`Error loading candle data for ${symbol}:`, error);
    return generatePriceHistory(100, '1d');
  }
};

const mockStocks: Stock[] = [
  {
    id: '1',
    symbol: 'AAPL',
    name: 'Apple Inc.',
    currentPrice: 178.50,
    highestBid: 178.45,
    lowestAsk: 178.55,
    change24h: 2.5,
    volume24h: 50000000,
  },
  {
    id: '2',
    symbol: 'GOOGL',
    name: 'Alphabet Inc.',
    currentPrice: 141.20,
    highestBid: 141.15,
    lowestAsk: 141.25,
    change24h: -1.2,
    volume24h: 30000000,
  },
  {
    id: '3',
    symbol: 'MSFT',
    name: 'Microsoft Corp.',
    currentPrice: 378.90,
    highestBid: 378.85,
    lowestAsk: 378.95,
    change24h: 1.8,
    volume24h: 25000000,
  },
  {
    id: '4',
    symbol: 'AMZN',
    name: 'Amazon.com Inc.',
    currentPrice: 178.25,
    highestBid: 178.20,
    lowestAsk: 178.30,
    change24h: 0.5,
    volume24h: 40000000,
  },
  {
    id: '5',
    symbol: 'TSLA',
    name: 'Tesla Inc.',
    currentPrice: 248.50,
    highestBid: 248.45,
    lowestAsk: 248.55,
    change24h: -3.2,
    volume24h: 80000000,
  },
];

const generatePriceHistory = (basePrice: number, period: string = '1d'): PriceHistory[] => {
  const history: PriceHistory[] = [];
  const now = Date.now();
  
  let points: number;
  let interval: number;
  
  switch (period) {
    case '1w':
      points = 7;
      interval = 24 * 3600000; // 1 день
      break;
    case '1m':
      points = 30;
      interval = 24 * 3600000; // 1 день
      break;
    case '1y':
      points = 12;
      interval = 30 * 24 * 3600000; // 1 месяц
      break;
    default: // 1d
      points = 24;
      interval = 3600000; // 1 час
  }
  
  for (let i = points; i >= 0; i--) {
    const price = basePrice + (Math.random() - 0.5) * 10;
    history.push({
      timestamp: now - i * interval,
      price,
      volume: Math.floor(Math.random() * 1000000) + 100000,
    });
  }
  return history;
};

const mockPortfolio: Portfolio = {
  cash: 10000.00,
  totalValue: 25000.00,
  pnl: 5000.00,
  pnlPercentage: 25.0,
  holdings: [
    {
      stockId: '1',
      stockSymbol: 'AAPL',
      stockName: 'Apple Inc.',
      amount: 10,
      averagePrice: 170.00,
      currentPrice: 178.50,
      totalValue: 1785.00,
      pnl: 85.00,
      pnlPercentage: 5.0,
    },
    {
      stockId: '3',
      stockSymbol: 'MSFT',
      stockName: 'Microsoft Corp.',
      amount: 5,
      averagePrice: 350.00,
      currentPrice: 378.90,
      totalValue: 1894.50,
      pnl: 144.50,
      pnlPercentage: 8.26,
    },
  ],
};

class MockMarketService {
  async getStocks(): Promise<Stock[]> {
    return new Promise((resolve) => setTimeout(() => resolve(mockStocks), 300));
  }

  async getStockById(id: string): Promise<Stock> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const stock = mockStocks.find((s) => s.id === id);
        resolve(stock || mockStocks[0]);
      }, 200);
    });
  }

  async getPriceHistory(stockId: string, period: string = '1d'): Promise<PriceHistory[]> {
    return new Promise(async (resolve) => {
      const stock = mockStocks.find((s) => s.id === stockId);
      if (stock) {
        try {
          // Загружаем реальные данные из JSON файла
          const history = await loadCandleData(stock.symbol);
          resolve(history);
        } catch (error) {
          console.error('Failed to load candle data, using generated data:', error);
          resolve(generatePriceHistory(stock?.currentPrice || 100, period));
        }
      } else {
        resolve(generatePriceHistory(100, period));
      }
    });
  }

  async getOrderBook(stockId: string): Promise<OrderBook> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const stock = mockStocks.find((s) => s.id === stockId);
        const basePrice = stock?.currentPrice || 100;
        resolve({
          bids: [
            { price: basePrice - 0.05, amount: 100 },
            { price: basePrice - 0.10, amount: 200 },
            { price: basePrice - 0.15, amount: 150 },
          ],
          asks: [
            { price: basePrice + 0.05, amount: 100 },
            { price: basePrice + 0.10, amount: 200 },
            { price: basePrice + 0.15, amount: 150 },
          ],
        });
      }, 200);
    });
  }

  async createOrder(data: {
    stockId: string;
    type: 'buy' | 'sell';
    orderType: 'market' | 'limit';
    amount: number;
    price?: number;
  }): Promise<Order> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const stock = mockStocks.find((s) => s.id === data.stockId);
        resolve({
          id: Math.random().toString(36).substr(2, 9),
          stockId: data.stockId,
          stockSymbol: stock?.symbol || 'UNKNOWN',
          type: data.type,
          orderType: data.orderType,
          price: data.price,
          amount: data.amount,
          status: 'pending',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        });
      }, 500);
    });
  }

  async cancelOrder(orderId: string): Promise<void> {
    return new Promise((resolve) => setTimeout(() => resolve(), 300));
  }

  async getUserOrders(): Promise<Order[]> {
    return new Promise((resolve) => {
      setTimeout(() => {
        resolve([
          {
            id: '1',
            stockId: '1',
            stockSymbol: 'AAPL',
            type: 'buy',
            orderType: 'limit',
            price: 175.00,
            amount: 10,
            status: 'filled',
            createdAt: new Date(Date.now() - 86400000).toISOString(),
            updatedAt: new Date(Date.now() - 86400000).toISOString(),
          },
          {
            id: '2',
            stockId: '3',
            stockSymbol: 'MSFT',
            type: 'buy',
            orderType: 'market',
            amount: 5,
            status: 'filled',
            createdAt: new Date(Date.now() - 172800000).toISOString(),
            updatedAt: new Date(Date.now() - 172800000).toISOString(),
          },
        ]);
      }, 300);
    });
  }

  async getUserTrades(): Promise<Trade[]> {
    return new Promise((resolve) => {
      setTimeout(() => {
        resolve([
          {
            id: '1',
            stockId: '1',
            stockSymbol: 'AAPL',
            type: 'buy',
            price: 170.00,
            amount: 10,
            total: 1700.00,
            timestamp: new Date(Date.now() - 86400000).toISOString(),
          },
          {
            id: '2',
            stockId: '3',
            stockSymbol: 'MSFT',
            type: 'buy',
            price: 350.00,
            amount: 5,
            total: 1750.00,
            timestamp: new Date(Date.now() - 172800000).toISOString(),
          },
        ]);
      }, 300);
    });
  }

  async getPortfolio(): Promise<Portfolio> {
    return new Promise((resolve) => setTimeout(() => resolve(mockPortfolio), 300));
  }
}

export const mockMarketService = new MockMarketService();
