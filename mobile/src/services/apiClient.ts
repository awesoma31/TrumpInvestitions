import AsyncStorage from '@react-native-async-storage/async-storage';
import type { AuthResponse } from '../types/api';

const API_BASE_URL = 'http://192.168.1.83:8080';

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
    options: RequestInit = {},
    baseUrl: string = API_BASE_URL
  ): Promise<T> {
    const token = await this.getAccessToken();
    const url = endpoint.startsWith('http://') || endpoint.startsWith('https://')
      ? endpoint
      : `${baseUrl}${endpoint}`;

    const headers: Record<string, string> = {};
    if (options.method !== 'GET') {
      headers['Content-Type'] = 'application/json';
    }

    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    // X-User-Id проставляет auth-gateway для внутренних сервисов
    // Мобильное приложение отправляет только Authorization через gateway.
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 30000); // Увеличили до 30 секунд

    console.log('Making request to:', url);
    console.log('Request options:', { ...options, headers });
    console.log('Request body:', options.body);

    const response = await fetch(url, {
      ...options,
      headers,
      signal: controller.signal,
    });

    clearTimeout(timeoutId);
    console.log('Response status:', response.status);
    console.log('Response headers:', response.headers);

    const responseText = await response.text();
    if (!response.ok) {
      if (response.status === 401) {
        await this.clearTokens();
      }

      let errorMessage = `HTTP ${response.status}`;
      if (responseText) {
        try {
          const parsed = JSON.parse(responseText);
          errorMessage = parsed?.message || parsed?.error || errorMessage;
        } catch {
          errorMessage = responseText;
        }
      }

      throw new Error(errorMessage || 'Network error');
    }

    if (response.status === 204 || responseText.length === 0) {
      return {} as T;
    }

    try {
      return JSON.parse(responseText) as T;
    } catch (error) {
      console.warn('Failed to parse JSON response:', error, responseText);
      throw new Error('Invalid response from server');
    }
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
      if (refreshToken) {
        await this.post('/api/v1/auth/logout', { refreshToken });
      } else {
        await this.post('/api/v1/auth/logout', {});
      }
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

  async getInstrumentBySymbol(symbol: string) {
    const queryParams = new URLSearchParams();
    queryParams.append('q', symbol);
    queryParams.append('limit', '1');
    return this.get(`/api/v1/market/instruments?${queryParams}`);
  }

  async getQuotes(symbols: string) {
    return this.get(`/api/v1/market/quotes?symbols=${symbols}`);
  }

  async getQuoteBySymbol(symbol: string) {
    return this.get(`/api/v1/market/quotes?symbols=${symbol}`);
  }

  async getOrderBook(symbol: string, depth?: number) {
    const queryParams = new URLSearchParams();
    queryParams.append('symbol', symbol);
    if (depth) queryParams.append('depth', depth.toString());
    
    const endpoint = `/api/v1/market/order-book?${queryParams}`;
    return this.get(endpoint);
  }

  async getMarketHistory(symbol: string, from: string, to: string, interval: string, limit?: number) {
    const queryParams = new URLSearchParams();
    queryParams.append('symbol', symbol);
    queryParams.append('from', from);
    queryParams.append('to', to);
    queryParams.append('interval', interval);
    if (limit) queryParams.append('limit', limit.toString());
    
    return this.get(`/api/v1/market/history/candles?${queryParams}`);
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

  async getTradeById(tradeId: string) {
    return this.get(`/api/v1/trades/${tradeId}`);
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

  async getPnl() {
    return this.get('/api/v1/portfolio/pnl');
  }

  async depositBalance(amount: string) {
    return this.post('/api/v1/portfolio/balance/deposit', { amount });
  }

  async withdrawBalance(amount: string) {
    return this.post('/api/v1/portfolio/balance/withdraw', { amount });
  }

  async getCashBalance() {
    return this.get('/api/v1/portfolio/balance/cash');
  }

  // WebSocket token
  async getWebSocketToken() {
    return this.post('/api/v1/realtime/ws-token');
  }

  // System endpoints
  async health() {
    return this.get('/api/v1/system/health');
  }

  async ready() {
    return this.get('/api/v1/system/ready');
  }
}

export const apiClient = new ApiClient();
