export interface BalanceOperation {
  amount: string;
}

export interface BalanceResponse {
  userId: number;
  balance: string;
  currency: string;
  updatedAt: string;
}

export interface PortfolioResponse {
  userId: number;
  cashBalance: string;
  totalMarketValue: string;
  totalEquity: string;
  realizedPnl: string;
  unrealizedPnl: string;
  totalPnl: string;
  positions: PositionResponse[];
  updatedAt: string;
}

export interface PositionResponse {
  symbol: string;
  quantity: number;
  avgPrice: string;
  currentPrice: string;
  marketValue: string;
  realizedPnl: string;
  unrealizedPnl: string;
  totalPnl: string;
  currency: string;
  updatedAt: string;
}

export interface PortfolioPnlResponse {
  realizedPnl: string;
  unrealizedPnl: string;
  totalPnl: string;
  currency: string;
  updatedAt: string;
}
