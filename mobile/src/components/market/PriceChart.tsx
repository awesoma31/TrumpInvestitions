import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ActivityIndicator, TouchableOpacity } from 'react-native';
import { mockMarketService } from '../../services/mockMarketService';
import type { Stock } from '../../types/market';
import { colors, spacing } from '../../theme';

const USE_MOCK = true;

interface PriceChartProps {
  stock: Stock;
}

const PriceChart: React.FC<PriceChartProps> = ({ stock }) => {
  const [priceHistory, setPriceHistory] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [period, setPeriod] = useState<'1d' | '1w' | '1m' | '1y'>('1d');

  useEffect(() => {
    loadPriceHistory();
    const interval = setInterval(loadPriceHistory, 5000);
    return () => clearInterval(interval);
  }, [stock.id, period]);

  const loadPriceHistory = async () => {
    try {
      const service = USE_MOCK ? mockMarketService : null;
      if (service) {
        const history = await service.getPriceHistory(stock.id, period);
        setPriceHistory(history.map(h => h.price));
        setError('');
      }
    } catch (err) {
      setError('Не удалось загрузить историю цен');
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

  const minPrice = Math.min(...priceHistory);
  const maxPrice = Math.max(...priceHistory);
  const priceRange = maxPrice - minPrice;

  const renderChart = () => {
    if (priceHistory.length === 0) return null;

    const chartHeight = 100;
    const chartWidth = 250;
    const yAxisWidth = 35;
    const pointWidth = chartWidth / priceHistory.length;

    // Создаем точки
    const points = priceHistory.map((price, index) => {
      const x = yAxisWidth + index * pointWidth + pointWidth / 2;
      const y = chartHeight - (priceRange > 0 ? ((price - minPrice) / priceRange) * chartHeight : chartHeight / 2);
      return { x, y, price };
    });

    return (
      <View style={styles.chartContainer}>
        <View style={styles.chart}>
          {/* Ось Y */}
          <View style={styles.yAxis}>
            <View style={[styles.yAxisLine, { height: chartHeight }]} />
            <View style={[styles.yAxisLabel, { bottom: 0 }]}>
              <Text style={styles.axisText}>{Math.round(minPrice)}</Text>
            </View>
            <View style={[styles.yAxisLabel, { top: 0 }]}>
              <Text style={styles.axisText}>{Math.round(maxPrice)}</Text>
            </View>
          </View>

          {/* Ось X */}
          <View style={styles.xAxis}>
            <View style={styles.xAxisLine} />
            {points.map((point, index) => {
              if (index % Math.ceil(points.length / 5) === 0) {
                return (
                  <View key={index} style={[styles.xAxisLabel, { left: point.x - yAxisWidth }]}>
                    <Text style={styles.axisText}>{index + 1}</Text>
                  </View>
                );
              }
              return null;
            })}
          </View>

          {/* Линия из сегментов */}
          {points.map((point, index) => {
            const nextPoint = points[index + 1];
            if (!nextPoint) return null;
            
            const dx = nextPoint.x - point.x;
            const dy = nextPoint.y - point.y;
            const length = Math.sqrt(dx * dx + dy * dy);
            const angle = Math.atan2(dy, dx) * (180 / Math.PI);
            
            return (
              <View
                key={index}
                style={[
                  styles.lineSegment,
                  {
                    left: point.x,
                    top: point.y,
                    width: length,
                    transform: [{ rotate: `${angle}deg` }],
                  },
                ]}
              />
            );
          })}
          
          {/* Точки */}
          {points.map((point, index) => (
            <View
              key={index}
              style={[
                styles.point,
                {
                  left: point.x - 4,
                  top: point.y - 4,
                },
                index === priceHistory.length - 1 ? styles.lastPoint : {},
              ]}
            />
          ))}
        </View>
      </View>
    );
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>График цены: {stock.symbol}</Text>
      <View style={styles.periodButtons}>
        {(['1d', '1w', '1m', '1y'] as const).map((p) => (
          <TouchableOpacity
            key={p}
            style={[styles.periodButton, period === p && styles.activePeriod]}
            onPress={() => setPeriod(p)}
          >
            <Text style={[styles.periodButtonText, period === p && styles.activePeriodText]}>
              {p === '1d' ? 'День' : p === '1w' ? 'Неделя' : p === '1m' ? 'Месяц' : 'Год'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
      {renderChart()}
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
    marginBottom: spacing.md,
  },
  periodButtons: {
    flexDirection: 'row',
    gap: spacing.sm,
    marginBottom: spacing.md,
  },
  periodButton: {
    flex: 1,
    padding: spacing.sm,
    borderRadius: 6,
    backgroundColor: colors.background,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
  },
  activePeriod: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  periodButtonText: {
    color: colors.text,
    fontSize: 12,
    fontWeight: '600',
  },
  activePeriodText: {
    color: colors.text,
  },
  chartContainer: {
    backgroundColor: colors.card,
    borderRadius: 8,
    padding: spacing.sm,
    borderWidth: 1,
    borderColor: colors.border,
  },
  chart: {
    height: 100,
    marginBottom: spacing.sm,
    position: 'relative',
    paddingLeft: 30,
    paddingBottom: 15,
  },
  yAxis: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 15,
    width: 30,
  },
  yAxisLine: {
    position: 'absolute',
    left: 30,
    top: 0,
    bottom: 0,
    width: 1,
    backgroundColor: colors.border,
  },
  yAxisLabel: {
    position: 'absolute',
    right: 2,
  },
  xAxis: {
    position: 'absolute',
    left: 30,
    right: 0,
    bottom: 0,
    height: 15,
  },
  xAxisLine: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    width: '100%',
    height: 1,
    backgroundColor: colors.border,
  },
  xAxisLabel: {
    position: 'absolute',
    bottom: 2,
  },
  axisText: {
    fontSize: 9,
    color: colors.textSecondary,
  },
  lineSegment: {
    position: 'absolute',
    backgroundColor: colors.primary,
    height: 2,
    transformOrigin: 'left top',
  },
  point: {
    position: 'absolute',
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.primary,
  },
  lastPoint: {
    backgroundColor: colors.success,
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  error: {
    color: colors.danger,
    textAlign: 'center',
    fontSize: 16,
  },
});

export default PriceChart;
