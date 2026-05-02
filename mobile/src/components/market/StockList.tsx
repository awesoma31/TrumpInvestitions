import React, { useEffect, useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, StyleSheet, ActivityIndicator } from 'react-native';
import { marketService } from '../../services/marketService';
import type { Stock } from '../../types/market';
import { colors, spacing } from '../../theme';

const USE_MOCK = false;

interface StockListProps {
  onStockSelect?: (stock: Stock) => void;
}

const StockList: React.FC<StockListProps> = ({ onStockSelect }) => {
  const [stocks, setStocks] = useState<Stock[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    loadStocks();
    const interval = setInterval(loadStocks, 5000);
    return () => clearInterval(interval);
  }, []);

  const loadStocks = async () => {
    try {
      const service = !USE_MOCK ? marketService : null;
      if (service) {
        const data = await service.getStocks();
        setStocks(data);
        setError('');
      }
    } catch (err) {
      setError('Не удалось загрузить акции');
    } finally {
      setLoading(false);
    }
  };

  const formatPrice = (price: number) => price.toFixed(2);
  const formatChange = (change: number) => `${change >= 0 ? '+' : ''}${change.toFixed(2)}%`;

  const renderStock = ({ item }: { item: Stock }) => (
    <TouchableOpacity
      style={styles.stockItem}
      onPress={() => onStockSelect?.(item)}
    >
      <View style={styles.stockInfo}>
        <Text style={styles.stockSymbol}>{item.symbol}</Text>
        <Text style={styles.stockName}>{item.name}</Text>
      </View>
      <View style={styles.stockData}>
        <Text style={styles.stockPrice}>${formatPrice(item.currentPrice)}</Text>
        <View style={styles.bidAsk}>
          <Text style={styles.bidAskText}>Bid: ${formatPrice(item.highestBid)}</Text>
          <Text style={styles.bidAskText}>Ask: ${formatPrice(item.lowestAsk)}</Text>
        </View>
        <Text style={[styles.stockChange, item.change24h >= 0 ? styles.positive : styles.negative]}>
          {formatChange(item.change24h)}
        </Text>
      </View>
    </TouchableOpacity>
  );

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

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Список акций</Text>
      <FlatList
        data={stocks}
        renderItem={renderStock}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.list}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
    padding: spacing.md,
  },
  center: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: colors.background,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: colors.text,
    marginBottom: spacing.lg,
  },
  list: {
    gap: spacing.sm,
  },
  stockItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: colors.card,
    padding: spacing.md,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.border,
  },
  stockInfo: {
    flex: 1,
  },
  stockSymbol: {
    fontSize: 18,
    fontWeight: 'bold',
    color: colors.text,
  },
  stockName: {
    fontSize: 14,
    color: colors.textSecondary,
  },
  stockData: {
    alignItems: 'flex-end',
  },
  stockPrice: {
    fontSize: 18,
    fontWeight: 'bold',
    color: colors.text,
  },
  bidAsk: {
    flexDirection: 'row',
    gap: spacing.sm,
    marginTop: spacing.xs,
  },
  bidAskText: {
    fontSize: 12,
    color: colors.textSecondary,
  },
  stockChange: {
    fontSize: 14,
    fontWeight: '600',
  },
  positive: {
    color: colors.success,
  },
  negative: {
    color: colors.danger,
  },
  error: {
    color: colors.danger,
    textAlign: 'center',
    fontSize: 16,
  },
});

export default StockList;
