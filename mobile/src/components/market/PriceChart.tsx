import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Dimensions,
  ActivityIndicator,
  ScrollView,
} from 'react-native';
import { marketService } from '../../services/marketService';
import type { Stock } from '../../types/market';
import type { PriceHistory } from '../../types/market';
import { colors, spacing } from '../../theme';

const USE_MOCK = false; // Используем реальные данные из API

interface PriceChartProps {
  stock: Stock;
}

const PriceChart: React.FC<PriceChartProps> = ({ stock }) => {
  const [priceHistory, setPriceHistory] = useState<PriceHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedPoint, setSelectedPoint] = useState<PriceHistory | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const scrollViewRef = useRef<ScrollView>(null);

  useEffect(() => {
    loadInitialData();
  }, [stock.id]);

  const loadInitialData = async () => {
    try {
      setLoading(true);
      const service = !USE_MOCK ? marketService : null;
      if (service) {
        const history = await service.getPriceHistory(stock.symbol);
        setPriceHistory(history);
        setError('');
      }
    } catch (err) {
      console.error('Ошибка загрузки истории цен:', err);
      setError(err instanceof Error ? `Не удалось загрузить историю цен: ${err.message}` : 'Не удалось загрузить историю цен');
    } finally {
      setLoading(false);
    }
  };

  const loadMoreData = async () => {
    if (loadingMore || priceHistory.length === 0) return;
    
    try {
      setLoadingMore(true);
      const service = !USE_MOCK ? marketService : null;
      if (service) {
        // Загружаем дополнительные данные
        const additionalHistory = await service.getPriceHistory(stock.symbol);
        // Объединяем все данные для большего диапазона
        setPriceHistory(prev => [...prev, ...additionalHistory]);
      }
    } catch (err) {
      console.error('Ошибка загрузки дополнительных данных:', err);
    } finally {
      setLoadingMore(false);
    }
  };

  const formatDate = (timestamp: number) => {
    const date = new Date(timestamp);
    return date.toLocaleString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
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

  if (priceHistory.length === 0) {
    return (
      <View style={styles.center}>
        <Text style={styles.noDataText}>Нет данных для отображения графика</Text>
      </View>
    );
  }

  const renderChart = () => {
    const chartHeight = 320;
    const chartWidth = Math.min(440, Dimensions.get('window').width - 40);
    const yAxisWidth = 70;
    const bottomPadding = 50;
    const topPadding = 20;
    const effectiveChartHeight = chartHeight - bottomPadding;
    
    // Рассчитываем цены с запасом для лучшего отображения
    const prices = priceHistory.map(h => h.price);
    const minPrice = Math.min(...prices);
    const maxPrice = Math.max(...prices);
    const priceRange = maxPrice - minPrice;
    const pricePadding = priceRange * 0.1; // 10% запас
    const adjustedMinPrice = minPrice - pricePadding;
    const adjustedMaxPrice = maxPrice + pricePadding;
    const adjustedPriceRange = adjustedMaxPrice - adjustedMinPrice;
    const priceStep = adjustedPriceRange / 6;

    // Рассчитываем размеры свечей
    const availableWidth = chartWidth - yAxisWidth;
    const candleWidth = Math.max(6, Math.floor(availableWidth / priceHistory.length) - 2);
    const candleSpacing = Math.floor(availableWidth / priceHistory.length);

    // Конвертируем PriceHistory в свечи с правильными OHLC данными
    const candles = priceHistory.map((point, index) => {
      const open = index > 0 ? priceHistory[index - 1].price : point.price;
      const close = point.price;
      const high = Math.max(open, close);
      const low = Math.min(open, close);
      
      const x = yAxisWidth + index * candleSpacing + candleSpacing / 2;
      
      return {
        x,
        open,
        high,
        low,
        close,
        volume: point.volume,
        timestamp: point.timestamp,
        isGreen: close >= open,
        isRed: close < open,
      };
    });

    // Генерируем линии сетки и ценовые метки
    const gridLines = [];
    for (let i = 0; i <= 6; i++) {
      const y = (effectiveChartHeight / 6) * i;
      const price = adjustedMaxPrice - (priceStep * i);
      gridLines.push({ y, price });
    }

    return (
      <View style={styles.chartContainer}>
        <View style={[styles.chart, { height: chartHeight }]}>
          {/* Фон графика */}
          <View style={styles.chartBackground} />
          
          {/* Горизонтальные линии сетки */}
          {gridLines.map((line, index) => (
            <View key={`grid-${index}`}>
              {/* Линия сетки */}
              <View
                style={[
                  styles.gridLine,
                  {
                    left: yAxisWidth,
                    top: line.y + topPadding,
                    width: availableWidth,
                    backgroundColor: index === 0 || index === 6 ? 'rgba(0, 0, 0, 0.2)' : 'rgba(0, 0, 0, 0.05)',
                  },
                ]}
              />
              {/* Ценовая метка */}
              <View
                style={[
                  styles.yAxisLabel,
                  {
                    top: line.y + topPadding - 10,
                    right: 10,
                  },
                ]}
              >
                <Text style={styles.axisText}>{line.price.toFixed(2)}</Text>
              </View>
            </View>
          ))}
          
          {/* Ось Y */}
          <View style={[styles.yAxis, { height: chartHeight - bottomPadding }]}>
            <View style={[styles.yAxisLine, { height: chartHeight - bottomPadding }]} />
          </View>

          {/* Скроллящийся контейнер для свечей */}
          <ScrollView
            ref={scrollViewRef}
            horizontal
            showsHorizontalScrollIndicator={false}
            style={styles.scrollContainer}
            contentContainerStyle={styles.scrollContent}
            onScroll={(e) => {
              const { contentOffset, contentSize, layoutMeasurement } = e.nativeEvent;
              const isNearEnd = contentOffset.x + layoutMeasurement.width >= contentSize.width - 50;
              if (isNearEnd && !loadingMore) {
                loadMoreData();
              }
            }}
            scrollEventThrottle={16}
          >
            <View style={[styles.candlesScrollContent, { width: Math.max(chartWidth, candles.length * (candleSpacing + 5)) }]}>
              {candles.map((candle, index) => {
                const isSelected = selectedPoint?.timestamp === candle.timestamp;
                
                // Рассчитываем Y координаты с учетом скорректированного диапазона
                const highY = effectiveChartHeight - ((candle.high - adjustedMinPrice) / adjustedPriceRange) * effectiveChartHeight;
                const lowY = effectiveChartHeight - ((candle.low - adjustedMinPrice) / adjustedPriceRange) * effectiveChartHeight;
                const openY = effectiveChartHeight - ((candle.open - adjustedMinPrice) / adjustedPriceRange) * effectiveChartHeight;
                const closeY = effectiveChartHeight - ((candle.close - adjustedMinPrice) / adjustedPriceRange) * effectiveChartHeight;
                
                // Рассчитываем размеры тела свечи
                const bodyHeight = Math.abs(openY - closeY) || 2;
                const bodyTop = Math.min(openY, closeY);
                const bodyBottom = Math.max(openY, closeY);
                
                return (
                  <TouchableOpacity
                    key={`candle-${index}`}
                    style={[
                      styles.candleContainer,
                      {
                        left: candle.x - candleWidth / 2,
                        top: topPadding + bodyBottom,
                        width: candleWidth,
                        height: bodyHeight + 2,
                      },
                      isSelected && styles.selectedCandle,
                    ]}
                    onPress={() => setSelectedPoint({
                      timestamp: candle.timestamp,
                      price: candle.close,
                      volume: candle.volume,
                    })}
                    activeOpacity={0.7}
                  >
                    {/* Тень свечи (wick) */}
                    <View
                      style={[
                        styles.candleWick,
                        {
                          left: candleWidth / 2 - 0.5,
                          top: topPadding + bodyBottom - (highY - bodyBottom),
                          height: Math.abs(highY - lowY),
                          backgroundColor: candle.isGreen ? '#00D084' : '#FF3B30',
                          width: 1,
                        },
                      ]}
                    />
                    
                    {/* Тело свечи */}
                    <View
                      style={[
                        styles.candleBody,
                        {
                          left: 0,
                          top: topPadding + bodyBottom - (highY - bodyBottom),
                          width: candleWidth,
                          height: bodyHeight,
                          backgroundColor: candle.isGreen ? '#00D084' : '#FF3B30',
                          borderRadius: 1,
                          borderWidth: candle.isRed ? 0 : 0,
                          borderColor: candle.isRed ? '#FF3B30' : 'transparent',
                        },
                      ]}
                    />
                    
                    {/* Выделение выбранной свечи */}
                    {isSelected && (
                      <View
                        style={[
                          styles.candleSelection,
                          {
                            left: candle.x - candleWidth / 2 - 2,
                            top: topPadding + bodyBottom - (highY - bodyBottom) - 2,
                            width: candleWidth + 4,
                            height: bodyHeight + 4,
                          },
                        ]}
                      />
                    )}
                  </TouchableOpacity>
                );
              })}
            </View>
          </ScrollView>
          
          {/* Ось X */}
          <View style={[styles.xAxis, { bottom: bottomPadding - 20 }]}>
            <View style={[styles.xAxisLine, { width: availableWidth }]} />
          </View>
        </View>
      </View>
    );
  };

  return (
    <View style={styles.container}>
      {/* Информация о выбранной точке */}
      {selectedPoint && (
        <View style={styles.tooltip}>
          <Text style={styles.tooltipText}>
            Цена: {selectedPoint.price.toFixed(2)}
          </Text>
          <Text style={styles.tooltipText}>
            Объем: {selectedPoint.volume}
          </Text>
          <Text style={styles.tooltipText}>
            Время: {formatDate(selectedPoint.timestamp)}
          </Text>
          <TouchableOpacity 
            style={styles.closeTooltipButton}
            onPress={() => setSelectedPoint(null)}
          >
            <Text style={styles.closeTooltipText}>✕</Text>
          </TouchableOpacity>
        </View>
      )}

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
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: spacing.lg,
  },
  error: {
    color: colors.danger,
    textAlign: 'center',
    marginBottom: spacing.md,
    fontSize: 16,
  },
  noDataText: {
    color: colors.textSecondary,
    textAlign: 'center',
    fontSize: 16,
  },
  chartContainer: {
    backgroundColor: colors.card,
    borderRadius: 12,
    padding: spacing.md,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 4,
  },
  chart: {
    height: 320,
    marginBottom: spacing.sm,
    position: 'relative',
  },
  chartBackground: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.02)',
    borderRadius: 8,
  },
  scrollContainer: {
    position: 'absolute',
    left: 80,
    right: 0,
    top: 20,
    bottom: 40,
  },
  scrollContent: {
    flexGrow: 1,
  },
  candlesScrollContent: {
    height: '100%',
    flexDirection: 'row',
    alignItems: 'flex-end',
  },
  gridLine: {
    position: 'absolute',
    height: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.1)',
  },
  yAxis: {
    position: 'absolute',
    left: 0,
    top: 20,
    bottom: 40,
    width: 70,
  },
  yAxisLine: {
    position: 'absolute',
    right: 0,
    top: 20,
    bottom: 40,
    width: 1,
    backgroundColor: colors.border,
  },
  yAxisLabel: {
    position: 'absolute',
    alignItems: 'flex-end',
    right: 10,
  },
  xAxis: {
    position: 'absolute',
    left: 70,
    right: 0,
    bottom: 30,
    height: 20,
  },
  xAxisLine: {
    position: 'absolute',
    left: 70,
    bottom: 30,
    height: 1,
    backgroundColor: colors.border,
  },
  axisText: {
    fontSize: 10,
    color: colors.textSecondary,
    fontWeight: '500',
  },
  candleContainer: {
    position: 'absolute',
    alignItems: 'center',
  },
  candleWick: {
    position: 'absolute',
    borderRadius: 1,
  },
  candleBody: {
    position: 'absolute',
    borderRadius: 1,
    shadowColor: 'rgba(0, 0, 0, 0.1)',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 2,
  },
  candleSelection: {
    position: 'absolute',
    backgroundColor: 'rgba(59, 130, 246, 0.2)',
    borderWidth: 1,
    borderColor: '#3B82F6',
    borderRadius: 2,
  },
  selectedCandle: {
    zIndex: 10,
  },
  tooltip: {
    position: 'absolute',
    top: spacing.md,
    left: spacing.md,
    right: spacing.md,
    backgroundColor: colors.card,
    borderRadius: 8,
    padding: spacing.md,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 8,
    elevation: 8,
    zIndex: 1000,
  },
  tooltipText: {
    fontSize: 12,
    color: colors.text,
    marginBottom: spacing.xs,
  },
  closeTooltipButton: {
    position: 'absolute',
    top: spacing.sm,
    right: spacing.sm,
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: colors.background,
    justifyContent: 'center',
    alignItems: 'center',
  },
  closeTooltipText: {
    fontSize: 14,
    color: colors.textSecondary,
  },
});

export default PriceChart;
