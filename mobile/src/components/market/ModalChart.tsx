import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Dimensions,
  Modal,
  SafeAreaView,
  StatusBar,
  Animated,
  PanResponder,
} from 'react-native';

import Svg, { Rect, Line, Text as SvgText, G } from 'react-native-svg';

const { width: screenWidth, height: screenHeight } = Dimensions.get('window');

type Timeframe = '1ч' | '4ч' | '1д' | '1н' | '1м';

const COLORS = {
  bg: '#000000',
  green: '#26a69a',
  red: '#ef5350',
  grid: '#1a1a1a',
  text: '#888',
  active: '#6c5ce7',
};

export default function ModalChart({ visible, onClose, stock }: any) {
  const [data, setData] = useState<any[]>([]);
  const [timeframe, setTimeframe] = useState<Timeframe>('1д');
  const [selected, setSelected] = useState<any>(null);

  // Если акция не выбрана, используем тестовую
  const currentStock = stock || {
    symbol: 'BTCUSD',
    name: 'Bitcoin',
    price: 19500,
  };

  const translateX = useRef(new Animated.Value(0)).current;
  const lastTranslate = useRef(0);
  const chartWidthRef = useRef(0);

  const chartHeight = screenHeight * 0.55;
  const volumeHeight = chartHeight * 0.2;
  const candlesHeight = chartHeight - volumeHeight - 20;

  const RIGHT_PADDING = 60;
  const TOP_PADDING = 10;

  useEffect(() => {
    if (!visible) return;

    const lenMap = { '1ч': 30, '4ч': 40, '1д': 50, '1н': 60, '1м': 70 };

    const fake = Array.from({ length: lenMap[timeframe] }).map((_, i) => {
      const base = 19500 + Math.sin(i / 3) * 5;
      return {
        open: base,
        close: base + (Math.random() - 0.5) * 6,
        high: base + Math.random() * 6,
        low: base - Math.random() * 6,
        volume: Math.random() * 100,
        time: Date.now() + i * 60000,
      };
    });

    setData(fake);
    setSelected(null);
    lastTranslate.current = 0;
    translateX.setValue(0);
  }, [visible, timeframe]);

  const panResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: () => true,

      onPanResponderMove: (_, g) => {
        const next = lastTranslate.current + g.dx;

        const clamped = Math.max(
          Math.min(next, 0),
          screenWidth - chartWidthRef.current
        );

        translateX.setValue(clamped);
      },

      onPanResponderRelease: (_, g) => {
        const next = lastTranslate.current + g.dx;

        const clamped = Math.max(
          Math.min(next, 0),
          screenWidth - chartWidthRef.current
        );

        lastTranslate.current = clamped;
      },
    })
  ).current;

  const renderChart = () => {
    if (!data.length) return null;

    const highs = data.map(d => d.high);
    const lows = data.map(d => d.low);

    const max = Math.max(...highs);
    const min = Math.min(...lows);
    const range = max - min;

    const yMin = min - range * 0.15;
    const yMax = max + range * 0.15;

    const priceToY = (p: number) =>
      TOP_PADDING +
      (candlesHeight - TOP_PADDING) -
      ((p - yMin) / (yMax - yMin)) * (candlesHeight - TOP_PADDING);

    const maxVolume = Math.max(...data.map(d => d.volume));

    const candleWidth = 4;
    const spacing = 4;

    const totalWidth = data.length * (candleWidth + spacing);
    const chartWidth = Math.max(screenWidth, totalWidth) + RIGHT_PADDING;

    chartWidthRef.current = chartWidth;

    const candles: any[] = [];
    const volumes: any[] = [];

    data.forEach((c, i) => {
      const x = i * (candleWidth + spacing);

      const isGreen = c.close >= c.open;
      const color = isGreen ? COLORS.green : COLORS.red;

      const openY = priceToY(c.open);
      const closeY = priceToY(c.close);
      const highY = priceToY(c.high);
      const lowY = priceToY(c.low);

      const bodyTop = Math.min(openY, closeY);
      const bodyHeight = Math.max(1, Math.abs(openY - closeY));

      candles.push(
        <G key={i}>
          <Line
            x1={x + candleWidth / 2}
            x2={x + candleWidth / 2}
            y1={highY}
            y2={lowY}
            stroke={color}
            strokeWidth={1}
          />
          <Rect
            x={x}
            y={bodyTop}
            width={candleWidth}
            height={bodyHeight}
            fill={color}
            stroke={selected && selected.time === c.time ? '#fff' : isGreen ? '#00A86B' : '#D32F2F'}
            strokeWidth={selected && selected.time === c.time ? 2 : 0.5}
          />
        </G>
      );

      const volH = (c.volume / maxVolume) * volumeHeight;

      volumes.push(
        <Rect
          key={'v' + i}
          x={x}
          y={candlesHeight + volumeHeight - volH}
          width={candleWidth}
          height={volH}
          fill={color}
          opacity={0.3}
        />
      );
    });

    const levels = Array.from({ length: 5 }).map((_, i) =>
      yMin + (i / 4) * (yMax - yMin)
    );

    const grid = levels.map((p, i) => {
      const y = priceToY(p);
      return (
        <G key={i}>
          <Line
            x1={0}
            x2={chartWidth - RIGHT_PADDING}
            y1={y}
            y2={y}
            stroke={COLORS.grid}
            strokeWidth={0.5}
          />
          <SvgText
            x={chartWidth - 5}
            y={y + 4}
            fontSize={10}
            fill={COLORS.text}
            textAnchor="end"
          >
            {p.toFixed(2)}
          </SvgText>
        </G>
      );
    });

    const handleTouch = (e: any) => {
      // Учитываем текущее смещение графика
      const touchX = e.nativeEvent.locationX;
      const adjustedX = touchX - lastTranslate.current;
      const index = Math.floor(adjustedX / (candleWidth + spacing));

      if (index >= 0 && index < data.length) {
        setSelected(data[index]);
      }
    };

    return (
      <Animated.View
        style={{ transform: [{ translateX }] }}
        {...panResponder.panHandlers}
      >
        <Svg width={chartWidth} height={chartHeight} onPress={handleTouch}>
          <Rect width="100%" height="100%" fill="#000" />

          {grid}

          <G>{candles}</G>

          <G y={candlesHeight}>
            {volumes}
          </G>
        </Svg>
      </Animated.View>
    );
  };

  if (!visible) return null;

  return (
    <Modal visible animationType="slide">
      <SafeAreaView style={styles.container}>
        <StatusBar barStyle="light-content" />

        <View style={styles.header}>
          <TouchableOpacity onPress={onClose}>
            <Text style={styles.close}>✕</Text>
          </TouchableOpacity>
          <Text style={styles.title}>График</Text>
          <View style={{ width: 20 }} />
        </View>

        <View style={styles.tfBar}>
          {(['1ч','4ч','1д','1н','1м'] as Timeframe[]).map(tf => (
            <TouchableOpacity
              key={tf}
              onPress={() => setTimeframe(tf)}
              style={[styles.tfBtn, timeframe === tf && styles.tfActive]}
            >
              <Text style={{ color: '#fff' }}>{tf}</Text>
            </TouchableOpacity>
          ))}
        </View>

        <View style={{ flex: 1, marginTop: 10 }}>
          {renderChart()}
          {/* 📌 TOOLTIP */}
          {selected && (
            <View style={styles.tooltip}>
              <Text style={styles.tooltipText}>
                Дата: {new Date(selected.time).toLocaleDateString()}
              </Text>
              <Text style={styles.tooltipText}>
                Время: {new Date(selected.time).toLocaleTimeString()}
              </Text>
              <Text style={styles.tooltipText}>
                Цена: {selected.close.toFixed(2)}
              </Text>
            </View>
          )}
        </View>

        {/*
        {selected && (
          <View style={styles.tooltip}>
            <Text style={styles.tooltipText}>
              {new Date(selected.time).toLocaleDateString()}
            </Text>
            <Text style={styles.tooltipText}>
              {new Date(selected.time).toLocaleTimeString()}
            </Text>
            <Text style={styles.tooltipText}>
              Цена: {selected.close.toFixed(2)}
            </Text>
          </View>
        )}
        */}
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000' },

  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    padding: 16,
  },

  title: { color: '#fff', fontSize: 18 },
  close: { color: '#fff', fontSize: 20 },

  tfBar: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingVertical: 10,
  },

  tfBtn: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 10,
    backgroundColor: '#111',
  },

  tfActive: {
    backgroundColor: '#6c5ce7',
  },

  tooltip: {
    position: 'absolute',
    bottom: 100,
    left: 20,
    right: 20,
    backgroundColor: '#111',
    padding: 15,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#6c5ce7',
  },

  tooltipText: {
    color: '#fff',
    fontSize: 12,
  },

  tooltipTitle: {
    color: '#fff',
    fontSize: 14,
    fontWeight: 'bold',
    marginBottom: 8,
  },
});