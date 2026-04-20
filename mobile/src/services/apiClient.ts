import AsyncStorage from '@react-native-async-storage/async-storage';
import type { AuthResponse } from '../types/api';

const API_BASE_URL = 'https://localhost:8081';

class ApiClient {
  private async getAccessToken(): Promise<string | null> {
    return await AsyncStorage.getItem('access_token');
  }

  private async setAccessToken(token: string): Promise<void> {
    await AsyncStorage.setItem('access_token', token);
  }

  private async getRefreshToken(): Promise<string | null> {
    return await AsyncStorage.getItem('refresh_token');
  }

  private async setRefreshToken(token: string): Promise<void> {
    await AsyncStorage.setItem('refresh_token', token);
  }

  private async clearTokens(): Promise<void> {
    await AsyncStorage.removeItem('access_token');
    await AsyncStorage.removeItem('refresh_token');
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const token = await this.getAccessToken();
    const url = `${API_BASE_URL}${endpoint}`;

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };

    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(url, {
      ...options,
      headers,
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Network error' }));
      throw new Error((error as any).message || `HTTP ${response.status}`);
    }

    return response.json() as Promise<T>;
  }

  async get<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: 'GET' });
  }

  async post<T>(endpoint: string, data?: any): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'POST',
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  async put<T>(endpoint: string, data?: any): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'PUT',
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  async delete<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: 'DELETE' });
  }

  // Auth methods
  async register(username: string, email: string, password: string) {
    const response = await this.post<AuthResponse>('/api/v1/auth/register', {
      username,
      email,
      password,
    });
    
    await this.setAccessToken(response.accessToken);
    await this.setRefreshToken(response.refreshToken);
    return response;
  }

  async login(login: string, password: string) {
    const response = await this.post<AuthResponse>('/api/v1/auth/login', {
      login,
      password,
    });
    
    await this.setAccessToken(response.accessToken);
    await this.setRefreshToken(response.refreshToken);
    return response;
  }

  async logout(): Promise<void> {
    try {
      const refreshToken = await this.getRefreshToken();
      await this.post('/api/v1/auth/logout', { refreshToken });
    } finally {
      await this.clearTokens();
    }
  }

  async refreshTokens(): Promise<void> {
    const refreshToken = await this.getRefreshToken();
    if (!refreshToken) {
      throw new Error('No refresh token');
    }

    const response = await this.post<AuthResponse>('/api/v1/auth/refresh', {
      refreshToken,
    });
    
    await this.setAccessToken(response.accessToken);
    await this.setRefreshToken(response.refreshToken);
  }

  async getMe() {
    return this.get('/api/v1/auth/me');
  }

  // Market methods
  async getInstruments(params?: { q?: string; limit?: number; offset?: number }) {
    const queryParams = new URLSearchParams();
    if (params?.q) queryParams.append('q', params.q);
    if (params?.limit) queryParams.append('limit', params.limit.toString());
    if (params?.offset) queryParams.append('offset', params.offset.toString());
    
    const endpoint = `/api/v1/market/instruments${queryParams.toString() ? `?${queryParams}` : ''}`;
    return this.get(endpoint);
  }

  async getQuotes(symbols: string) {
    return this.get(`/api/v1/market/quotes?symbols=${symbols}`);
  }

  async getOrderBook(symbol: string, depth?: number) {
    const queryParams = new URLSearchParams();
    queryParams.append('symbol', symbol);
    if (depth) queryParams.append('depth', depth.toString());
    
    return this.get(`/api/v1/market/order-book?${queryParams}`);
  }

  async getMarketHistory(symbol: string, from: string, to: string, interval: string) {
    const queryParams = new URLSearchParams();
    queryParams.append('symbol', symbol);
    queryParams.append('from', from);
    queryParams.append('to', to);
    queryParams.append('interval', interval);
    
    return this.get(`/api/v1/market/history?${queryParams}`);
  }

  // Order methods
  async createOrder(data: { symbol: string; side: string; type: string; quantity: number }) {
    return this.post('/api/v1/orders', data);
  }

  async getOrders(params?: { status?: string; symbol?: string; limit?: number; offset?: number }) {
    const queryParams = new URLSearchParams();
    if (params?.status) queryParams.append('status', params.status);
    if (params?.symbol) queryParams.append('symbol', params.symbol);
    if (params?.limit) queryParams.append('limit', params.limit.toString());
    if (params?.offset) queryParams.append('offset', params.offset.toString());
    
    const endpoint = `/api/v1/orders${queryParams.toString() ? `?${queryParams}` : ''}`;
    return this.get(endpoint);
  }

  async getOrderById(orderId: string) {
    return this.get(`/api/v1/orders/${orderId}`);
  }

  async cancelOrder(orderId: string) {
    return this.post(`/api/v1/orders/${orderId}/cancel`);
  }

  async getTrades(params?: { symbol?: string; limit?: number; offset?: number }) {
    const queryParams = new URLSearchParams();
    if (params?.symbol) queryParams.append('symbol', params.symbol);
    if (params?.limit) queryParams.append('limit', params.limit.toString());
    if (params?.offset) queryParams.append('offset', params.offset.toString());
    
    const endpoint = `/api/v1/trades${queryParams.toString() ? `?${queryParams}` : ''}`;
    return this.get(endpoint);
  }

  // Portfolio methods
  async getPortfolio() {
    return this.get('/api/v1/portfolio');
  }

  async getPositions(params?: { symbol?: string }) {
    const queryParams = new URLSearchParams();
    if (params?.symbol) queryParams.append('symbol', params.symbol);
    
    const endpoint = `/api/v1/portfolio/positions${queryParams.toString() ? `?${queryParams}` : ''}`;
    return this.get(endpoint);
  }

  async getPnl(params?: { from?: string; to?: string }) {
    const queryParams = new URLSearchParams();
    if (params?.from) queryParams.append('from', params.from);
    if (params?.to) queryParams.append('to', params.to);
    
    const endpoint = `/api/v1/portfolio/pnl${queryParams.toString() ? `?${queryParams}` : ''}`;
    return this.get(endpoint);
  }

  // WebSocket token
  async getWebSocketToken() {
    return this.post('/api/v1/realtime/ws-token');
  }
}

export const apiClient = new ApiClient();
