import React, { useEffect, useState } from 'react';
import { View, Text, ScrollView, StyleSheet, ActivityIndicator } from 'react-native';
import { marketService } from '../../services/marketService';
import type { Portfolio } from '../../types/market';
import { colors, spacing } from '../../theme';

const USE_MOCK = false;

const PortfolioComponent: React.FC = () => {
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    loadPortfolio();
    const interval = setInterval(loadPortfolio, 10000);
    return () => clearInterval(interval);
  }, []);

  const loadPortfolio = async () => {
    try {
      const service = !USE_MOCK ? marketService : null;
      if (service) {
        const data = await service.getPortfolio();
        setPortfolio(data);
        setError('');
      }
    } catch (err) {
      setError('Не удалось загрузить портфель');
    } finally {
      setLoading(false);
    }
  };

  const formatValue = (value: number) => `$${value.toFixed(2)}`;
  const formatPnL = (pnl: number) => `${pnl >= 0 ? '+' : ''}${pnl.toFixed(2)}`;

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

  if (!portfolio) {
    return null;
  }

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>Инвестиционный портфель</Text>

      <View style={styles.summary}>
        <View style={styles.summaryCard}>
          <Text style={styles.summaryLabel}>Доступные средства</Text>
          <Text style={styles.summaryValue}>{formatValue(portfolio.cash)}</Text>
        </View>

        <View style={styles.summaryCard}>
          <Text style={styles.summaryLabel}>Общая стоимость</Text>
          <Text style={styles.summaryValue}>{formatValue(portfolio.totalValue)}</Text>
        </View>

        <View style={styles.summaryCard}>
          <Text style={styles.summaryLabel}>P&L</Text>
          <Text style={[styles.summaryValue, portfolio.pnl >= 0 ? styles.positive : styles.negative]}>
            {formatPnL(portfolio.pnl)}
          </Text>
        </View>
      </View>

      <Text style={styles.sectionTitle}>Активы в портфеле</Text>

      {portfolio.holdings.length === 0 ? (
        <Text style={styles.empty}>Портфель пуст</Text>
      ) : (
        portfolio.holdings.map((holding) => (
          <View key={holding.stockId} style={styles.holdingItem}>
            <View style={styles.holdingInfo}>
              <Text style={styles.stockSymbol}>{holding.stockSymbol}</Text>
              <Text style={styles.stockName}>{holding.stockName}</Text>
            </View>
            <View style={styles.holdingData}>
              <Text style={styles.holdingAmount}>{holding.amount} шт</Text>
              <Text style={styles.holdingPrice}>{formatValue(holding.currentPrice)}</Text>
              <Text style={[styles.holdingPnl, holding.pnl >= 0 ? styles.positive : styles.negative]}>
                {formatPnL(holding.pnl)}
              </Text>
            </View>
          </View>
        ))
      )}
    </ScrollView>
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
  summary: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: spacing.xl,
    gap: spacing.sm,
  },
  summaryCard: {
    flex: 1,
    backgroundColor: colors.card,
    padding: spacing.md,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.border,
  },
  summaryLabel: {
    fontSize: 12,
    color: colors.textSecondary,
    marginBottom: spacing.xs,
  },
  summaryValue: {
    fontSize: 18,
    fontWeight: 'bold',
    color: colors.text,
  },
  positive: {
    color: colors.success,
  },
  negative: {
    color: colors.danger,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: colors.text,
    marginBottom: spacing.lg,
  },
  empty: {
    color: colors.textSecondary,
    textAlign: 'center',
    padding: spacing.xl,
  },
  holdingItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: colors.card,
    padding: spacing.md,
    borderRadius: 8,
    marginBottom: spacing.sm,
    borderWidth: 1,
    borderColor: colors.border,
  },
  holdingInfo: {
    flex: 1,
  },
  stockSymbol: {
    fontSize: 16,
    fontWeight: 'bold',
    color: colors.text,
  },
  stockName: {
    fontSize: 12,
    color: colors.textSecondary,
  },
  holdingData: {
    alignItems: 'flex-end',
  },
  holdingAmount: {
    fontSize: 14,
    color: colors.text,
  },
  holdingPrice: {
    fontSize: 14,
    color: colors.text,
    fontWeight: '600',
  },
  holdingPnl: {
    fontSize: 14,
    fontWeight: '600',
  },
  error: {
    color: colors.danger,
    textAlign: 'center',
    fontSize: 16,
  },
});

export default PortfolioComponent;
