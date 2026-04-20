import { apiClient } from './apiClient';
import { BalanceOperation, BalanceResponse, PortfolioResponse, PositionResponse, PortfolioPnlResponse } from '../types/portfolio';

class PortfolioService {
  async getPortfolio(): Promise<PortfolioResponse> {
    return apiClient.getPortfolio() as Promise<PortfolioResponse>;
  }

  async getPositions(symbol?: string): Promise<{ items: PositionResponse[] }> {
    return apiClient.getPositions({ symbol }) as Promise<{ items: PositionResponse[] }>;
  }

  async getPortfolioPnl(): Promise<PortfolioPnlResponse> {
    return apiClient.getPnl() as Promise<PortfolioPnlResponse>;
  }

  async depositBalance(amount: string): Promise<BalanceResponse> {
    return apiClient.depositBalance(amount) as Promise<BalanceResponse>;
  }

  async withdrawBalance(amount: string): Promise<BalanceResponse> {
    return apiClient.withdrawBalance(amount) as Promise<BalanceResponse>;
  }

  async getCashBalance(): Promise<BalanceResponse> {
    // Используем специальный эндпоинт для получения денежного баланса
    const cashBalance = await apiClient.getCashBalance() as any;
    return {
      userId: cashBalance.userId,
      balance: cashBalance.balance,
      currency: cashBalance.currency || 'USD',
      updatedAt: new Date().toISOString()
    };
  }
}

export const portfolioService = new PortfolioService();
