import { apiClient } from './apiClient';
import type { Instrument, Quote, OrderBookLevel, OrderBookResponse, Candle, OrderResponse, TradeResponse, PortfolioResponse, PositionResponse, InstrumentListResponse, QuoteListResponse, CandleListResponse, OrderListResponse, TradeListResponse } from '../types/api';
import type { Stock, PriceHistory, OrderBook, Order, Trade, Portfolio } from '../types/market';

// Конвертация типов API в типы приложения
const convertInstrumentToStock = (instrument: Instrument): Stock => ({
  id: instrument.symbol,
  symbol: instrument.symbol,
  name: instrument.name,
  currentPrice: 0,
  highestBid: 0,
  lowestAsk: 0,
  change24h: Math.random() * 10 - 5, // Случайное значение от -5% до +5%
  volume24h: 0,
});

const convertQuoteToStock = (quote: Quote): Stock => ({
  id: quote.symbol,
  symbol: quote.symbol,
  name: quote.symbol,
  currentPrice: parseFloat(quote.last),
  highestBid: parseFloat(quote.bid),
  lowestAsk: parseFloat(quote.ask),
  change24h: Math.random() * 10 - 5, // Случайное значение от -5% до +5%
  volume24h: 0,
});

const convertCandleToPriceHistory = (candle: Candle): PriceHistory => ({
  timestamp: new Date(candle.timestamp).getTime(),
  price: parseFloat(candle.close),
  volume: candle.volume,
});

const convertOrderToOrder = (order: OrderResponse): Order => ({
  id: order.id,
  stockId: order.symbol,
  stockSymbol: order.symbol,
  type: order.side === 'BUY' ? 'buy' : 'sell',
  orderType: order.type === 'MARKET' ? 'market' : 'limit',
  price: order.avgFillPrice ? parseFloat(order.avgFillPrice) : undefined,
  amount: order.quantity,
  status: order.status === 'FILLED' ? 'filled' : order.status === 'CANCELLED' ? 'cancelled' : order.status === 'REJECTED' ? 'rejected' : 'pending',
  createdAt: order.createdAt,
  updatedAt: order.updatedAt,
});

const convertTradeToTrade = (trade: TradeResponse): Trade => ({
  id: trade.id,
  stockId: trade.symbol,
  stockSymbol: trade.symbol,
  type: trade.side === 'BUY' ? 'buy' : 'sell',
  price: parseFloat(trade.price),
  amount: trade.quantity,
  total: parseFloat(trade.price) * trade.quantity,
  timestamp: trade.executedAt,
});

const convertPositionToHolding = (position: PositionResponse) => ({
  stockId: position.symbol,
  stockSymbol: position.symbol,
  stockName: position.symbol,
  amount: position.quantity,
  averagePrice: parseFloat(position.avgPrice),
  currentPrice: parseFloat(position.currentPrice),
  totalValue: parseFloat(position.marketValue),
  pnl: parseFloat(position.totalPnl),
  pnlPercentage: position.marketValue !== '0' ? (parseFloat(position.totalPnl) / parseFloat(position.marketValue)) * 100 : 0,
});

const convertPortfolioToPortfolio = (portfolio: PortfolioResponse): Portfolio => ({
  cash: parseFloat(portfolio.cashBalance),
  totalValue: parseFloat(portfolio.totalMarketValue),
  pnl: parseFloat(portfolio.unrealizedPnl) + parseFloat(portfolio.realizedPnl),
  pnlPercentage: portfolio.totalEquity !== '0' ? ((parseFloat(portfolio.unrealizedPnl) + parseFloat(portfolio.realizedPnl)) / parseFloat(portfolio.totalEquity)) * 100 : 0,
  holdings: portfolio.positions.map(convertPositionToHolding),
});

class MarketService {
  async getStocks(): Promise<Stock[]> {
    try {
      console.log('Loading stocks from API...');
      const instruments = await apiClient.getInstruments({ limit: 50 }) as InstrumentListResponse;
      console.log('Instruments loaded:', instruments.items.length);
      
      const symbols = instruments.items.map((i: Instrument) => i.symbol).join(',');
      const quotes = await apiClient.getQuotes(symbols) as QuoteListResponse;
      console.log('Quotes loaded:', quotes.items.length);
      
      const quoteMap = new Map(quotes.items.map((q: Quote) => [q.symbol, q]));
      
      const stocks = instruments.items.map((instrument: Instrument) => {
        const quote = quoteMap.get(instrument.symbol);
        return quote ? convertQuoteToStock(quote) : convertInstrumentToStock(instrument);
      });
      
      console.log('Stocks converted:', stocks.length);
      return stocks;
    } catch (error) {
      console.error('Error loading stocks from API:', error);
      throw error;
    }
  }

  async getStockById(symbol: string): Promise<Stock> {
    const quote = await apiClient.getQuotes(symbol) as QuoteListResponse;
    return convertQuoteToStock(quote.items[0]);
  }

  async getPriceHistory(symbol: string): Promise<PriceHistory[]> {
    const normalizedSymbol = symbol.trim().toUpperCase();
    try {
      console.log(`Loading price history for ${normalizedSymbol} from API...`);
      const to = new Date().toISOString();
      const from = new Date(Date.now() - 1 * 60 * 60 * 1000).toISOString(); // 1ч 4ч 1д 1м
      const candles = await apiClient.getMarketHistory(normalizedSymbol, from, to, '1m', 1000) as CandleListResponse;
      console.log(`Price history loaded for ${normalizedSymbol}:`, candles.items.length, 'candles');
      return candles.items.map(convertCandleToPriceHistory);
    } catch (error) {
      console.error(`Error loading price history for ${normalizedSymbol}:`, error);
      throw error;
    }
  }

  async getOrderBook(symbol: string): Promise<OrderBook> {
    const orderBook = await apiClient.getOrderBook(symbol) as OrderBookResponse;
    return {
      bids: orderBook.bids.map((level: OrderBookLevel) => ({
        price: parseFloat(level.price),
        amount: level.quantity,
      })),
      asks: orderBook.asks.map((level: OrderBookLevel) => ({
        price: parseFloat(level.price),
        amount: level.quantity,
      })),
    };
  }

  async createOrder(data: {
    stockId: string;
    type: 'buy' | 'sell';
    orderType: 'market' | 'limit';
    amount: number;
    price?: number;
  }): Promise<Order> {
    const order = await apiClient.createOrder({
      symbol: data.stockId,
      side: data.type.toUpperCase() as any,
      type: data.orderType.toUpperCase() as any,
      quantity: data.amount,
    }) as OrderResponse;
    return convertOrderToOrder(order);
  }

  async cancelOrder(orderId: string): Promise<void> {
    await apiClient.cancelOrder(orderId);
  }

  async getUserOrders(): Promise<Order[]> {
    const orders = await apiClient.getOrders({ limit: 50 }) as OrderListResponse;
    return orders.items.map(convertOrderToOrder);
  }

  async getUserTrades(): Promise<Trade[]> {
    const trades = await apiClient.getTrades({ limit: 50 }) as TradeListResponse;
    return trades.items.map(convertTradeToTrade);
  }

  async getPortfolio(): Promise<Portfolio> {
    const portfolio = await apiClient.getPortfolio() as PortfolioResponse;
    return convertPortfolioToPortfolio(portfolio);
  }
}

export const marketService = new MarketService();
