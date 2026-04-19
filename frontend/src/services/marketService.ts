import { apiClient } from './api';
import type { Stock, PriceHistory, OrderBook, Order, Trade, Portfolio } from '../types/market';

class MarketService {
  async getStocks(): Promise<Stock[]> {
    return apiClient.get<Stock[]>('/stocks');
  }

  async getStockById(id: string): Promise<Stock> {
    return apiClient.get<Stock>(`/stocks/${id}`);
  }

  async getPriceHistory(stockId: string, period?: string): Promise<PriceHistory[]> {
    const periodParam = period ? `?period=${period}` : '';
    return apiClient.get<PriceHistory[]>(`/stocks/${stockId}/history${periodParam}`);
  }

  async getOrderBook(stockId: string): Promise<OrderBook> {
    return apiClient.get<OrderBook>(`/stocks/${stockId}/orderbook`);
  }

  async createOrder(data: {
    stockId: string;
    type: 'buy' | 'sell';
    orderType: 'market' | 'limit';
    amount: number;
    price?: number;
  }): Promise<Order> {
    return apiClient.post<Order>('/orders', data);
  }

  async cancelOrder(orderId: string): Promise<void> {
    return apiClient.delete<void>(`/orders/${orderId}`);
  }

  async getUserOrders(): Promise<Order[]> {
    return apiClient.get<Order[]>('/orders');
  }

  async getUserTrades(): Promise<Trade[]> {
    return apiClient.get<Trade[]>('/trades');
  }

  async getPortfolio(): Promise<Portfolio> {
    return apiClient.get<Portfolio>('/portfolio');
  }
}

export const marketService = new MarketService();
