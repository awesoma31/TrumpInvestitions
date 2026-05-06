import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { marketService } from '../../services/marketService';
import type { Stock } from '../../types/market';
import { colors, spacing } from '../../theme';

const USE_MOCK = false;

interface OrderFormProps {
  stock: Stock;
  onOrderCreated?: () => void;
  onClose?: () => void;
}

const OrderForm: React.FC<OrderFormProps> = ({ stock, onOrderCreated, onClose }) => {
  const [orderType, setOrderType] = useState<'buy' | 'sell'>('buy');
  const [amount, setAmount] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async () => {
    setError('');
    const amountNum = parseFloat(amount);
    if (!amountNum || amountNum <= 0) {
      setError('Введите корректное количество');
      return;
    }

    try {
      setLoading(true);
      const service = !USE_MOCK ? marketService : null;
      if (service) {
        await service.createOrder({
          stockId: stock.id,
          type: orderType,
          orderType: 'market',
          amount: amountNum,
        });
        setAmount('');
        onOrderCreated?.();
      }
    } catch (err: any) {
      setError(err.message || 'Ошибка создания заявки');
    } finally {
      setLoading(false);
    }
  };

  const estimatedTotal = amount
    ? (parseFloat(amount) * stock.currentPrice).toFixed(2)
    : '0.00';

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Создать заявку: {stock.symbol}</Text>
        <TouchableOpacity onPress={onClose} style={styles.closeButton}>
          <Text style={styles.closeButtonText}>✕</Text>
        </TouchableOpacity>
      </View>
      
      {error ? <Text style={styles.error}>{error}</Text> : null}

      <View style={styles.typeToggle}>
        <TouchableOpacity
          style={[styles.typeButton, orderType === 'buy' && styles.buyButton]}
          onPress={() => setOrderType('buy')}
        >
          <Text style={styles.buttonText}>Купить</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.typeButton, orderType === 'sell' && styles.sellButton]}
          onPress={() => setOrderType('sell')}
        >
          <Text style={styles.buttonText}>Продать</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.inputContainer}>
        <Text style={styles.label}>Количество</Text>
        <TextInput
          style={styles.input}
          placeholder="Введите количество"
          placeholderTextColor={colors.textTertiary}
          value={amount}
          onChangeText={setAmount}
          keyboardType="decimal-pad"
        />
      </View>

      <View style={styles.summary}>
        <View style={styles.summaryRow}>
          <Text style={styles.summaryLabel}>Текущая цена:</Text>
          <Text style={styles.summaryValue}>${stock.currentPrice.toFixed(2)}</Text>
        </View>
        <View style={styles.summaryRow}>
          <Text style={styles.summaryLabel}>Ориентировочная сумма:</Text>
          <Text style={styles.summaryValue}>${estimatedTotal}</Text>
        </View>
      </View>

      <TouchableOpacity
        style={[styles.submitButton, orderType === 'buy' ? styles.buyButton : styles.sellButton, loading && styles.disabledButton]}
        onPress={handleSubmit}
        disabled={loading}
      >
        <Text style={styles.buttonText}>{loading ? 'Создание...' : orderType === 'buy' ? 'Купить' : 'Продать'}</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: colors.card,
    padding: spacing.lg,
    borderRadius: 12,
    margin: spacing.md,
    borderWidth: 1,
    borderColor: colors.border,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.lg,
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: colors.text,
  },
  closeButton: {
    padding: spacing.sm,
  },
  closeButtonText: {
    color: colors.textSecondary,
    fontSize: 24,
    fontWeight: 'bold',
  },
  error: {
    color: colors.danger,
    marginBottom: spacing.md,
    textAlign: 'center',
  },
  typeToggle: {
    flexDirection: 'row',
    marginBottom: spacing.lg,
    gap: spacing.sm,
  },
  typeButton: {
    flex: 1,
    padding: spacing.sm,
    borderRadius: 8,
    alignItems: 'center',
    backgroundColor: colors.background,
    borderWidth: 1,
    borderColor: colors.border,
  },
  buyButton: {
    backgroundColor: colors.success,
    borderColor: colors.success,
  },
  sellButton: {
    backgroundColor: colors.danger,
    borderColor: colors.danger,
  },
  buttonText: {
    color: colors.text,
    fontSize: 16,
    fontWeight: '600',
  },
  inputContainer: {
    marginBottom: spacing.lg,
  },
  label: {
    color: colors.textSecondary,
    marginBottom: spacing.sm,
    fontSize: 14,
  },
  input: {
    backgroundColor: colors.background,
    color: colors.text,
    padding: spacing.md,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.border,
    fontSize: 16,
  },
  summary: {
    backgroundColor: colors.background,
    padding: spacing.md,
    borderRadius: 8,
    marginBottom: spacing.lg,
  },
  summaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: spacing.sm,
  },
  summaryLabel: {
    color: colors.textSecondary,
    fontSize: 14,
  },
  summaryValue: {
    color: colors.text,
    fontSize: 16,
    fontWeight: '600',
  },
  submitButton: {
    padding: spacing.md,
    borderRadius: 8,
    alignItems: 'center',
  },
  disabledButton: {
    opacity: 0.5,
  },
});

export default OrderForm;
