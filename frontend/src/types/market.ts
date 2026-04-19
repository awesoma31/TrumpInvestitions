export interface Stock {
  id: string;
  symbol: string;
  name: string;
  currentPrice: number;
  highestBid: number;
  lowestAsk: number;
  change24h: number;
  volume24h: number;
}

export interface PriceHistory {
  timestamp: number;
  price: number;
  volume: number;
}

export interface OrderBook {
  bids: Array<{ price: number; amount: number }>;
  asks: Array<{ price: number; amount: number }>;
}

export interface Order {
  id: string;
  stockId: string;
  stockSymbol: string;
  type: 'buy' | 'sell';
  orderType: 'market' | 'limit';
  price?: number;
  amount: number;
  status: 'pending' | 'filled' | 'cancelled' | 'partial';
  createdAt: string;
  updatedAt: string;
}

export interface Trade {
  id: string;
  stockId: string;
  stockSymbol: string;
  type: 'buy' | 'sell';
  price: number;
  amount: number;
  total: number;
  timestamp: string;
}

export interface Portfolio {
  cash: number;
  totalValue: number;
  pnl: number;
  pnlPercentage: number;
  holdings: PortfolioHolding[];
}

export interface PortfolioHolding {
  stockId: string;
  stockSymbol: string;
  stockName: string;
  amount: number;
  averagePrice: number;
  currentPrice: number;
  totalValue: number;
  pnl: number;
  pnlPercentage: number;
}
