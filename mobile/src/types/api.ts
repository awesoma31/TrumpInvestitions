// Auth Types
export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  login: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface LogoutRequest {
  refreshToken?: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  refreshExpiresIn: number;
  user: UserProfile;
}

export interface UserProfile {
  id: number;
  username: string;
  email: string;
  balance: string;
  createdAt: string;
  updatedAt: string;
}

// Market Types
export interface Instrument {
  symbol: string;
  name: string;
  currency: string;
  lotSize: number;
  active: boolean;
}

export interface InstrumentListResponse {
  items: Instrument[];
  total: number;
  limit: number;
  offset: number;
}

export interface Quote {
  symbol: string;
  bid: string;
  ask: string;
  last: string;
  timestamp: string;
}

export interface QuoteListResponse {
  items: Quote[];
}

export interface OrderBookLevel {
  price: string;
  quantity: number;
}

export interface OrderBookResponse {
  symbol: string;
  bids: OrderBookLevel[];
  asks: OrderBookLevel[];
  bestBid: string | null;
  bestAsk: string | null;
  spread: string | null;
  timestamp: string;
}

export interface Candle {
  timestamp: string;
  open: string;
  high: string;
  low: string;
  close: string;
  volume: number;
}

export interface CandleListResponse {
  symbol: string;
  interval: string;
  items: Candle[];
}

// Order Types
export type OrderSide = 'BUY' | 'SELL';
export type OrderType = 'MARKET';
export type OrderStatus = 'NEW' | 'ACCEPTED' | 'FILLED' | 'CANCELLED' | 'REJECTED';

export interface CreateOrderRequest {
  symbol: string;
  side: OrderSide;
  type: OrderType;
  quantity: number;
}

export interface OrderResponse {
  id: string;
  userId: number;
  symbol: string;
  side: OrderSide;
  type: OrderType;
  quantity: number;
  filledQuantity: number;
  avgFillPrice: string | null;
  status: OrderStatus;
  rejectionReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface OrderListResponse {
  items: OrderResponse[];
  total: number;
  limit: number;
  offset: number;
}

export interface TradeResponse {
  id: string;
  orderId: string;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: string;
  executedAt: string;
}

export interface TradeListResponse {
  items: TradeResponse[];
  total: number;
  limit: number;
  offset: number;
}

// Portfolio Types
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

export interface PositionListResponse {
  items: PositionResponse[];
}

export interface PortfolioResponse {
  userId: number;
  cashBalance: string;
  totalMarketValue: string;
  totalEquity: string;
  unrealizedPnl: string;
  realizedPnl: string;
  positions: PositionResponse[];
  updatedAt: string;
}

export interface PnlResponse {
  realizedPnl: string;
  unrealizedPnl: string;
  totalPnl: string;
  currency: string;
}

// WebSocket Types
export interface WebSocketTokenResponse {
  token: string;
  expiresIn: number;
}

// Error Types
export interface ErrorResponse {
  code: string;
  message: string;
  details?: ErrorDetail[];
  traceId: string;
}

export interface ErrorDetail {
  field: string;
  issue: string;
}

// System Types
export interface HealthResponse {
  status: string;
  service: string;
  timestamp: string;
}

export interface ReadinessResponse {
  status: string;
  service: string;
  dependencies?: DependencyStatus[];
  timestamp: string;
}

export interface DependencyStatus {
  name: string;
  status: string;
}
