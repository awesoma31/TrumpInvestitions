import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, Alert, KeyboardAvoidingView, Platform, Keyboard, TouchableWithoutFeedback } from 'react-native';
import { portfolioService } from '../../services/portfolioService';
import { colors, spacing } from '../../theme';

interface DepositFormProps {
  onDepositSuccess?: () => void;
  onClose?: () => void;
}

const DepositForm: React.FC<DepositFormProps> = ({ onDepositSuccess, onClose }) => {
  const [amount, setAmount] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async () => {
    Keyboard.dismiss();
    setError('');
    
    const amountNum = parseFloat(amount);
    if (!amountNum || amountNum <= 0) {
      setError('Введите корректную сумму');
      return;
    }
    
    if (amountNum < 100) {
      setError('Минимальная сумма пополнения: $100.00');
      return;
    }

    try {
      setLoading(true);
      await portfolioService.depositBalance(amountNum.toFixed(2));
      
      Alert.alert(
        'Успешно!',
        `Баланс пополнен на $${amountNum.toFixed(2)}`,
        [{ text: 'OK', onPress: () => {
          setAmount('');
          onDepositSuccess?.();
        }}]
      );
    } catch (err: any) {
      setError(err.message || 'Ошибка пополнения баланса');
    } finally {
      setLoading(false);
    }
  };

  const suggestedAmounts = ['100', '500', '1000', '5000'];

  return (
    <TouchableWithoutFeedback onPress={Keyboard.dismiss}>
      <KeyboardAvoidingView 
        style={styles.container}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 20}
      >
        <View style={styles.header}>
          <Text style={styles.title}>Пополнить баланс</Text>
          {onClose && (
            <TouchableOpacity onPress={onClose} style={styles.closeButton}>
              <Text style={styles.closeButtonText}>✕</Text>
            </TouchableOpacity>
          )}
        </View>
        
        {error ? <Text style={styles.error}>{error}</Text> : null}
        
        <View style={styles.content}>
          <Text style={styles.label}>Сумма пополнения (USD)</Text>
          <TextInput
            style={styles.input}
            value={amount}
            onChangeText={(text) => {
              setAmount(text.replace(/[^0-9.]/g, ''));
            }}
            placeholder="0.00"
            placeholderTextColor="rgba(255,255,255,0.4)"
            keyboardType="numeric"
            returnKeyType="done"
            onSubmitEditing={handleSubmit}
            onEndEditing={Keyboard.dismiss}
          />
          
          <View style={styles.suggestedAmounts}>
            <Text style={styles.suggestedLabel}>Быстрое пополнение:</Text>
            <View style={styles.suggestedButtons}>
              {suggestedAmounts.map((suggestedAmount) => (
                <TouchableOpacity
                  key={suggestedAmount}
                  style={[
                    styles.suggestedButton,
                    amount === suggestedAmount && styles.suggestedButtonActive
                  ]}
                  onPress={() => {
                    setAmount(suggestedAmount);
                    Keyboard.dismiss();
                  }}
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
              {loading ? 'Обработка...' : 'Пополнить'}
            </Text>
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </TouchableWithoutFeedback>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#080808',
    borderRadius: 24,
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
    color: '#ffffff',
    fontSize: 22,
    fontWeight: 'bold',
  },
  closeButton: {
    padding: spacing.xs,
  },
  closeButtonText: {
    color: '#ffffff',
    fontSize: 22,
    fontWeight: 'bold',
  },
  content: {
    gap: spacing.md,
  },
  label: {
    color: 'rgba(255,255,255,0.8)',
    fontSize: 16,
    marginBottom: spacing.sm,
    fontWeight: '500',
  },
  input: {
    backgroundColor: '#121212',
    borderWidth: 0,
    borderRadius: 20,
    padding: spacing.lg,
    fontSize: 24,
    color: '#ffffff',
    textAlign: 'center',
  },
  suggestedAmounts: {
    marginTop: spacing.lg,
  },
  suggestedLabel: {
    color: 'rgba(255,255,255,0.65)',
    fontSize: 14,
    marginBottom: spacing.sm,
  },
  suggestedButtons: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
  },
  suggestedButton: {
    backgroundColor: '#121212',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.12)',
    borderRadius: 16,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    minWidth: 86,
  },
  suggestedButtonActive: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  suggestedButtonText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '600',
    textAlign: 'center',
  },
  suggestedButtonTextActive: {
    color: '#ffffff',
  },
  submitButton: {
    backgroundColor: '#4CAF50',
    borderRadius: 16,
    paddingVertical: spacing.md,
    alignItems: 'center',
    marginTop: spacing.lg,
    width: '100%',
  },
  submitButtonDisabled: {
    backgroundColor: 'rgba(255,255,255,0.12)',
  },
  submitButtonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '700',
  },
  error: {
    color: colors.danger,
    textAlign: 'center',
    marginBottom: spacing.md,
    fontSize: 14,
  },
});

export default DepositForm;
