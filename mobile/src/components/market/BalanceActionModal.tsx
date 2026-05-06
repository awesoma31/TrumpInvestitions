import React from 'react';
import { SafeAreaView, View, Text, TouchableOpacity, StyleSheet, Modal, StatusBar } from 'react-native';
import { colors, spacing } from '../../theme';

interface BalanceActionModalProps {
  visible: boolean;
  onClose: () => void;
  onDeposit: () => void;
  onWithdraw: () => void;
  cash: number;
}

const BalanceActionModal: React.FC<BalanceActionModalProps> = ({
  visible,
  onClose,
  onDeposit,
  onWithdraw,
  cash,
}) => {
  if (!visible) return null;

  return (
    <Modal
      visible={visible}
      animationType="slide"
      presentationStyle="fullScreen"
      onRequestClose={onClose}
    >
      <SafeAreaView style={styles.modalContainer}>
        <StatusBar barStyle="light-content" backgroundColor="#000" />
        <View style={styles.header}>
          <TouchableOpacity onPress={onClose} style={styles.closeButton}>
            <Text style={styles.closeButtonText}>✕</Text>
          </TouchableOpacity>
          <Text style={styles.title}>Действия с балансом</Text>
          <View style={styles.placeholder} />
        </View>

        <View style={styles.balanceInfo}>
          <Text style={styles.balanceLabel}>Доступные средства</Text>
          <Text style={styles.balanceAmount}>${cash.toFixed(2)}</Text>
        </View>

        <View style={styles.actions}>
          <TouchableOpacity
            style={[styles.actionButton, styles.depositButton]}
            onPress={() => {
              onDeposit();
              onClose();
            }}
          >
            <Text style={styles.actionButtonText}>Пополнить баланс</Text>
            <Text style={styles.actionButtonSubtext}>Добавить средства на счет</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.actionButton, styles.withdrawButton]}
            onPress={() => {
              onWithdraw();
              onClose();
            }}
          >
            <Text style={styles.actionButtonText}>Вывести средства</Text>
            <Text style={styles.actionButtonSubtext}>Вывести средства на карту</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    </Modal>
  );
};

const styles = StyleSheet.create({
  modalContainer: {
    flex: 1,
    backgroundColor: '#000',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    backgroundColor: '#000',
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#fff',
  },
  closeButton: {
    padding: spacing.xs,
  },
  closeButtonText: {
    color: '#fff',
    fontSize: 24,
    fontWeight: 'bold',
  },
  placeholder: {
    width: 40,
  },
  balanceInfo: {
    backgroundColor: '#111',
    margin: spacing.md,
    padding: spacing.lg,
    borderRadius: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.1)',
  },
  balanceLabel: {
    fontSize: 16,
    color: 'rgba(255,255,255,0.7)',
    marginBottom: spacing.sm,
  },
  balanceAmount: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#fff',
  },
  actions: {
    padding: spacing.md,
    gap: spacing.md,
  },
  actionButton: {
    backgroundColor: '#111',
    borderRadius: 14,
    padding: spacing.lg,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.1)',
    alignItems: 'center',
  },
  depositButton: {
    backgroundColor: '#4CAF50',
    borderColor: '#4CAF50',
  },
  withdrawButton: {
    backgroundColor: '#667eea',
    borderColor: '#667eea',
  },
  actionButtonText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#fff',
    marginBottom: spacing.xs,
  },
  actionButtonSubtext: {
    fontSize: 12,
    color: 'rgba(255,255,255,0.75)',
    textAlign: 'center',
  },
});

export default BalanceActionModal;
