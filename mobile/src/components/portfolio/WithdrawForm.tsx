import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { portfolioService } from '../../services/portfolioService';
import { colors, spacing } from '../../theme';

interface WithdrawFormProps {
  onWithdrawSuccess?: () => void;
  onClose?: () => void;
}

const WithdrawForm: React.FC<WithdrawFormProps> = ({ onWithdrawSuccess, onClose }) => {
  const [amount, setAmount] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async () => {
    setError('');
    
    // Валидация суммы
    const amountNum = parseFloat(amount);
    if (!amountNum || amountNum <= 0) {
      setError('Введите корректную сумму');
      return;
    }
    
    if (amountNum < 100) {
      setError('Минимальная сумма вывода: $100.00');
      return;
    }

    try {
      setLoading(true);
      const result = await portfolioService.withdrawBalance(amountNum.toFixed(2));
      
      Alert.alert(
        'Успешно!',
        `Запрос на вывод $${amountNum.toFixed(2)} отправлен`,
        [{ text: 'OK', onPress: () => {
          setAmount('');
          onWithdrawSuccess?.();
        }}]
      );
    } catch (err: any) {
      if (err.message?.includes('INSUFFICIENT_BALANCE')) {
        setError('Недостаточно средств на балансе');
      } else {
        setError(err.message || 'Ошибка вывода средств');
      }
    } finally {
      setLoading(false);
    }
  };

  const suggestedAmounts = ['100', '500', '1000', '5000'];

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Вывод средств</Text>
        {onClose && (
          <TouchableOpacity onPress={onClose} style={styles.closeButton}>
            <Text style={styles.closeButtonText}>✕</Text>
          </TouchableOpacity>
        )}
      </View>

      {error ? <Text style={styles.error}>{error}</Text> : null}

      <View style={styles.content}>
        <Text style={styles.label}>Сумма вывода (USD)</Text>
        <TextInput
          style={styles.input}
          value={amount}
          onChangeText={setAmount}
          placeholder="0.00"
          keyboardType="numeric"
          editable={!loading}
        />

        <View style={styles.suggestedAmounts}>
          <Text style={styles.suggestedLabel}>Быстрый вывод:</Text>
          <View style={styles.suggestedButtons}>
            {suggestedAmounts.map((suggestedAmount) => (
              <TouchableOpacity
                key={suggestedAmount}
                style={[
                  styles.suggestedButton,
                  amount === suggestedAmount && styles.suggestedButtonActive
                ]}
                onPress={() => setAmount(suggestedAmount)}
                disabled={loading}
              >
                <Text style={[
                  styles.suggestedButtonText,
                  amount === suggestedAmount && styles.suggestedButtonTextActive
                ]}>
                  ${suggestedAmount}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        <TouchableOpacity
          style={[styles.submitButton, loading && styles.submitButtonDisabled]}
          onPress={handleSubmit}
          disabled={loading}
        >
          <Text style={styles.submitButtonText}>
            {loading ? 'Обработка...' : 'Вывести'}
          </Text>
        </TouchableOpacity>

        <Text style={styles.warningText}>
          Вывод средств может занять несколько рабочих дней
        </Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: colors.background,
    borderRadius: 16,
    padding: spacing.lg,
    margin: spacing.md,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.lg,
  },
  title: {
    color: colors.text,
    fontSize: 20,
    fontWeight: 'bold',
  },
  closeButton: {
    padding: spacing.xs,
  },
  closeButtonText: {
    color: colors.textSecondary,
    fontSize: 20,
    fontWeight: 'bold',
  },
  content: {
    gap: spacing.md,
  },
  label: {
    color: colors.text,
    fontSize: 16,
    marginBottom: spacing.sm,
    fontWeight: '500',
  },
  input: {
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    padding: spacing.md,
    fontSize: 18,
    color: colors.text,
    textAlign: 'center',
  },
  suggestedAmounts: {
    marginTop: spacing.lg,
  },
  suggestedLabel: {
    color: colors.textSecondary,
    fontSize: 14,
    marginBottom: spacing.sm,
  },
  suggestedButtons: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
  },
  suggestedButton: {
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    minWidth: 80,
  },
  suggestedButtonActive: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  suggestedButtonText: {
    color: colors.text,
    fontSize: 14,
    fontWeight: '600',
    textAlign: 'center',
  },
  suggestedButtonTextActive: {
    color: colors.text,
  },
  submitButton: {
    backgroundColor: colors.danger,
    borderRadius: 8,
    paddingVertical: spacing.md,
    alignItems: 'center',
    marginTop: spacing.lg,
  },
  submitButtonDisabled: {
    backgroundColor: colors.border,
  },
  submitButtonText: {
    color: colors.text,
    fontSize: 16,
    fontWeight: 'bold',
  },
  error: {
    color: colors.danger,
    textAlign: 'center',
    marginBottom: spacing.md,
    fontSize: 14,
  },
  warningText: {
    color: colors.textSecondary,
    fontSize: 12,
    textAlign: 'center',
    marginTop: spacing.sm,
  },
});

export default WithdrawForm;
