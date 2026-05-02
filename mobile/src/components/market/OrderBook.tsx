import React, { useEffect, useState } from 'react';
import { View, Text, ScrollView, StyleSheet, ActivityIndicator } from 'react-native';
import { marketService } from '../../services/marketService';
import type { OrderBook } from '../../types/market';
import type { Stock } from '../../types/market';
import { colors, spacing } from '../../theme';

const USE_MOCK = false;

interface OrderBookProps {
  stock: Stock;
}

const OrderBook: React.FC<OrderBookProps> = ({ stock }) => {
  const [orderBook, setOrderBook] = useState<OrderBook | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    loadOrderBook();
    const interval = setInterval(loadOrderBook, 3000);
    return () => clearInterval(interval);
  }, [stock.id]);

  const loadOrderBook = async () => {
    try {
      const service = !USE_MOCK ? marketService : null;
      if (service) {
        const data = await service.getOrderBook(stock.id);
        setOrderBook(data);
        setError('');
      }
    } catch (err) {
      setError('Не удалось загрузить стакан');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  if (error) {
    return <Text style={styles.error}>{error}</Text>;
  }

  if (!orderBook) {
    return null;
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Стакан заявок: {stock.symbol}</Text>
      
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Bids (Покупка)</Text>
        <ScrollView style={styles.list}>
          {orderBook.bids.slice(0, 10).map((bid, index) => (
            <View key={`bid-${index}`} style={styles.row}>
              <Text style={styles.price}>{bid.price.toFixed(2)}</Text>
              <Text style={styles.amount}>{bid.amount}</Text>
              <Text style={styles.total}>{(bid.price * bid.amount).toFixed(2)}</Text>
            </View>
          ))}
        </ScrollView>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Asks (Продажа)</Text>
        <ScrollView style={styles.list}>
          {orderBook.asks.slice(0, 10).map((ask, index) => (
            <View key={`ask-${index}`} style={styles.row}>
              <Text style={styles.price}>{ask.price.toFixed(2)}</Text>
              <Text style={styles.amount}>{ask.amount}</Text>
              <Text style={styles.total}>{(ask.price * ask.amount).toFixed(2)}</Text>
            </View>
          ))}
        </ScrollView>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: colors.background,
    padding: spacing.md,
  },
  center: {
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: spacing.xl,
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: colors.text,
    marginBottom: spacing.lg,
  },
  section: {
    backgroundColor: colors.card,
    borderRadius: 8,
    padding: spacing.md,
    marginBottom: spacing.md,
    borderWidth: 1,
    borderColor: colors.border,
    minHeight: 150,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: colors.text,
    marginBottom: spacing.md,
  },
  list: {
    maxHeight: 200,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: spacing.xs,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  price: {
    fontSize: 14,
    fontWeight: '600',
    color: colors.text,
    flex: 1,
  },
  amount: {
    fontSize: 14,
    color: colors.text,
    flex: 1,
    textAlign: 'center',
  },
  total: {
    fontSize: 14,
    fontWeight: '600',
    color: colors.text,
    flex: 1,
    textAlign: 'right',
  },
  error: {
    color: colors.danger,
    textAlign: 'center',
    fontSize: 16,
  },
});

export default OrderBook;
