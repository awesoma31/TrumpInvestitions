import React, { useEffect, useState } from 'react';
import { View, Text, ScrollView, StyleSheet, TouchableOpacity, ActivityIndicator } from 'react-native';
import { marketService } from '../../services/marketService';
import type { Trade, Order } from '../../types/market';
import { colors, spacing } from '../../theme';

const USE_MOCK = false;

const TradeHistory: React.FC = () => {
  const [trades, setTrades] = useState<Trade[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [activeTab, setActiveTab] = useState<'trades' | 'orders'>('trades');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, 10000);
    return () => clearInterval(interval);
  }, [activeTab]);

  const loadData = async () => {
    try {
      setLoading(true);
      const service = !USE_MOCK ? marketService : null;
      if (service) {
        if (activeTab === 'trades') {
          const tradesData = await service.getUserTrades();
          setTrades(tradesData);
        } else {
          const ordersData = await service.getUserOrders();
          setOrders(ordersData);
        }
        setError('');
      }
    } catch (err) {
      setError('Не удалось загрузить историю');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelOrder = async (orderId: string) => {
    try {
      const service = !USE_MOCK ? marketService : null;
      if (service) {
        await service.cancelOrder(orderId);
        loadData();
      }
    } catch (err) {
      setError('Не удалось отменить заявку');
    }
  };

  const formatValue = (value: number) => `$${value.toFixed(2)}`;
  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('ru-RU');
  };

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>История</Text>

      <View style={styles.tabs}>
        <TouchableOpacity
          style={[styles.tabButton, activeTab === 'trades' && styles.activeTab]}
          onPress={() => setActiveTab('trades')}
        >
          <Text style={[styles.tabText, activeTab === 'trades' && styles.activeTabText]}>Сделки</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tabButton, activeTab === 'orders' && styles.activeTab]}
          onPress={() => setActiveTab('orders')}
        >
          <Text style={[styles.tabText, activeTab === 'orders' && styles.activeTabText]}>Заявки</Text>
        </TouchableOpacity>
      </View>

      {error ? <Text style={styles.error}>{error}</Text> : null}

      <ScrollView style={styles.scrollContainer}>
        {activeTab === 'trades' ? (
          trades.length === 0 ? (
            <Text style={styles.empty}>История сделок пуста</Text>
          ) : (
            trades.map((trade) => (
              <View key={trade.id} style={styles.item}>
                <View style={styles.itemInfo}>
                  <Text style={styles.symbol}>{trade.stockSymbol}</Text>
                  <Text style={[styles.type, trade.type === 'buy' ? styles.buy : styles.sell]}>
                    {trade.type === 'buy' ? 'Покупка' : 'Продажа'}
                  </Text>
                </View>
                <View style={styles.itemData}>
                  <Text style={styles.dataValue}>{formatValue(trade.price)}</Text>
                  <Text style={styles.dataAmount}>{trade.amount} шт</Text>
                  <Text style={styles.dataTotal}>{formatValue(trade.total)}</Text>
                  <Text style={styles.dataDate}>{formatDate(trade.timestamp)}</Text>
                </View>
              </View>
            ))
          )
        ) : orders.length === 0 ? (
          <Text style={styles.empty}>История заявок пуста</Text>
        ) : (
          orders.map((order) => (
            <View key={order.id} style={styles.item}>
              <View style={styles.itemInfo}>
                <Text style={styles.symbol}>{order.stockSymbol}</Text>
                <Text style={[styles.type, order.type === 'buy' ? styles.buy : styles.sell]}>
                  {order.type === 'buy' ? 'Покупка' : 'Продажа'}
                </Text>
              </View>
              <View style={styles.itemData}>
                <Text style={styles.dataValue}>{order.price ? formatValue(order.price) : '-'}</Text>
                <Text style={styles.dataAmount}>{order.amount} шт</Text>
                <Text style={styles.dataStatus}>{order.status}</Text>
                {order.status === 'pending' && (
                  <TouchableOpacity
                    style={styles.cancelButton}
                    onPress={() => handleCancelOrder(order.id)}
                  >
                    <Text style={styles.cancelButtonText}>Отменить</Text>
                  </TouchableOpacity>
                )}
              </View>
            </View>
          ))
        )}
      </ScrollView>
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
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: colors.text,
    marginBottom: spacing.lg,
  },
  tabs: {
    flexDirection: 'row',
    marginBottom: spacing.lg,
    gap: spacing.sm,
  },
  tabButton: {
    flex: 1,
    padding: spacing.md,
    borderRadius: 8,
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
  },
  activeTab: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  tabText: {
    color: colors.textSecondary,
    fontSize: 14,
    fontWeight: '600',
  },
  activeTabText: {
    color: colors.text,
  },
  scrollContainer: {
    flex: 1,
  },
  empty: {
    color: colors.textSecondary,
    textAlign: 'center',
    padding: spacing.xl,
  },
  item: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: colors.card,
    padding: spacing.md,
    borderRadius: 8,
    marginBottom: spacing.sm,
    borderWidth: 1,
    borderColor: colors.border,
  },
  itemInfo: {
    flex: 1,
  },
  symbol: {
    fontSize: 16,
    fontWeight: 'bold',
    color: colors.text,
  },
  type: {
    fontSize: 12,
    fontWeight: '600',
  },
  buy: {
    color: colors.success,
  },
  sell: {
    color: colors.danger,
  },
  itemData: {
    alignItems: 'flex-end',
  },
  dataValue: {
    fontSize: 14,
    color: colors.text,
    fontWeight: '600',
  },
  dataAmount: {
    fontSize: 12,
    color: colors.textSecondary,
  },
  dataTotal: {
    fontSize: 12,
    color: colors.textSecondary,
  },
  dataDate: {
    fontSize: 10,
    color: colors.textTertiary,
  },
  dataStatus: {
    fontSize: 12,
    color: colors.textSecondary,
  },
  cancelButton: {
    marginTop: spacing.xs,
    padding: spacing.xs,
    backgroundColor: colors.danger,
    borderRadius: 4,
  },
  cancelButtonText: {
    color: colors.text,
    fontSize: 10,
    fontWeight: '600',
  },
  error: {
    color: colors.danger,
    textAlign: 'center',
    marginBottom: spacing.md,
  },
});

export default TradeHistory;
