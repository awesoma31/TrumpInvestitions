import React, { useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Modal, ScrollView, Dimensions } from 'react-native';
import { useAuth } from '../context/AuthContext';
import { useRouter } from 'expo-router';
import StockList from '../components/market/StockList';
import OrderForm from '../components/market/OrderForm';
import PortfolioComponent from '../components/market/Portfolio';
import TradeHistory from '../components/market/TradeHistory';
import ModalChart from '../components/market/ModalChart';
import type { Stock } from '../types/market';
import { colors, spacing } from '../theme';

const getAvatarColor = (username: string): string => {
  const colorsList = [
    '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
    '#DDA0DD', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E9'
  ];
  let hash = 0;
  for (let i = 0; i < username.length; i++) {
    hash = username.charCodeAt(i) + ((hash << 5) - hash);
  }
  return colorsList[Math.abs(hash) % colorsList.length];
};

const Dashboard: React.FC = () => {
  const { logout, user } = useAuth();
  const router = useRouter();
  const [selectedStock, setSelectedStock] = useState<Stock | null>(null);
  const [activeView, setActiveView] = useState<'market' | 'portfolio' | 'history'>('market');
  const [showModalChart, setShowModalChart] = useState(false);
  const [showAvatarMenu, setShowAvatarMenu] = useState(false);
  const [showStockModal, setShowStockModal] = useState(false);

  const handleStockSelect = (stock: Stock) => {
    setSelectedStock(stock);
    setShowStockModal(true);
  };

  const handleOrderCreated = () => {
    setSelectedStock(null);
  };

  const handleLogout = async () => {
    await logout();
    router.replace('/');
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Трамплин инвестиций</Text>
        <View style={styles.userSection}>
          {user && (
            <>
              <TouchableOpacity onPress={() => setShowAvatarMenu(!showAvatarMenu)}>
                <View style={[styles.avatarCircle, { backgroundColor: getAvatarColor(user.name) }]}>
                  <Text style={styles.avatarText}>{user?.name?.charAt(0).toUpperCase()}</Text>
                </View>
              </TouchableOpacity>
              {showAvatarMenu && (
                <View style={styles.avatarMenu}>
                  <TouchableOpacity style={styles.menuItem} onPress={handleLogout}>
                    <Text style={styles.menuItemText}>← Выйти</Text>
                  </TouchableOpacity>
                </View>
              )}
            </>
          )}
        </View>
      </View>

      <View style={styles.nav}>
        <TouchableOpacity
          style={[styles.navButton, activeView === 'market' && styles.activeNav]}
          onPress={() => setActiveView('market')}
        >
          <Text style={[styles.navText, activeView === 'market' && styles.activeNavText]}>Рынок</Text>
        </TouchableOpacity>
        {user && (
          <>
            <TouchableOpacity
              style={[styles.navButton, activeView === 'portfolio' && styles.activeNav]}
              onPress={() => setActiveView('portfolio')}
            >
              <Text style={[styles.navText, activeView === 'portfolio' && styles.activeNavText]}>Портфель</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.navButton, activeView === 'history' && styles.activeNav]}
              onPress={() => setActiveView('history')}
            >
              <Text style={[styles.navText, activeView === 'history' && styles.activeNavText]}>История</Text>
            </TouchableOpacity>
          </>
        )}
      </View>

      {activeView === 'market' && (
        <View style={styles.content}>
          <StockList onStockSelect={handleStockSelect} />
        </View>
      )}

      {/* Всплывающее окно для выбранной акции */}
      {showStockModal && selectedStock !== null && (
        <View style={styles.popupOverlay}>
          <TouchableOpacity 
            style={styles.overlayBackground} 
            onPress={() => {
              setShowStockModal(false);
              setShowModalChart(false);
            }}
          />
          <View style={styles.popupContainer}>
            <View style={styles.popupHeader}>
              <TouchableOpacity onPress={() => {
                setShowStockModal(false);
                setShowModalChart(false);
              }}>
                <Text style={styles.closeButton}>✕</Text>
              </TouchableOpacity>
              <Text style={styles.popupTitle}>{selectedStock?.symbol}</Text>
              <View style={styles.placeholder} />
            </View>
            
            <ScrollView style={styles.popupContent} showsVerticalScrollIndicator={false}>
              <View style={styles.stockInfo}>
                <Text style={styles.stockName}>{selectedStock?.name}</Text>
                <Text style={styles.stockPrice}>${selectedStock?.currentPrice.toFixed(2)}</Text>
                <Text style={[
                  styles.stockChange,
                  (selectedStock?.change24h ?? 0) >= 0 ? styles.positive : styles.negative
                ]}>
                  {(selectedStock?.change24h ?? 0) >= 0 ? '+' : ''}{(selectedStock?.change24h ?? 0).toFixed(2)}%
                </Text>
              </View>

              <View style={styles.stockActions}>
                <TouchableOpacity
                  style={[styles.actionButton, showModalChart && styles.activeAction]}
                  onPress={() => setShowModalChart(true)}
                >
                  <Text style={styles.actionButtonText}>График</Text>
                </TouchableOpacity>
                {user && (
                  <TouchableOpacity
                    style={[styles.actionButton, styles.tradeButton]}
                    onPress={() => {}}
                  >
                    <Text style={styles.actionButtonText}>Торговать</Text>
                  </TouchableOpacity>
                )}
              </View>

              {user && selectedStock && (
                <OrderForm 
                  stock={selectedStock} 
                  onOrderCreated={handleOrderCreated} 
                  onClose={() => {
                    setSelectedStock(null);
                    setShowStockModal(false);
                  }} 
                />
              )}
            </ScrollView>
          </View>
        </View>
      )}

      {activeView === 'portfolio' && user && (
        <View style={styles.content}>
          <PortfolioComponent />
        </View>
      )}

      {!user && activeView === 'portfolio' && (
        <View style={styles.content}>
          <Text style={styles.authRequired}>Требуется авторизация</Text>
        </View>
      )}

      {activeView === 'history' && user && (
        <View style={styles.content}>
          <TradeHistory />
        </View>
      )}

      {!user && activeView === 'history' && (
        <View style={styles.content}>
          <Text style={styles.authRequired}>Требуется авторизация</Text>
        </View>
      )}
      
      {/* Модальное окно для полного графика */}
      {showModalChart && selectedStock && (
        <ModalChart
          stock={selectedStock}
          visible={showModalChart}
          onClose={() => setShowModalChart(false)}
        />
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: spacing.lg,
    paddingTop: 60,
    backgroundColor: colors.card,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: colors.text,
  },
  userSection: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    position: 'relative',
  },
  avatarCircle: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  avatarText: {
    color: colors.text,
    fontSize: 18,
    fontWeight: 'bold',
  },
  avatarMenu: {
    position: 'absolute',
    top: 50,
    right: 0,
    backgroundColor: '#1a1a1a',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.border,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 5,
    zIndex: 1000,
  },
  menuItem: {
    padding: spacing.md,
    minWidth: 100,
  },
  menuItemText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  logoutButton: {
    backgroundColor: colors.primary,
    padding: spacing.sm,
    borderRadius: 6,
  },
  logoutText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '600',
  },
  loginButton: {
    backgroundColor: colors.primary,
    padding: spacing.sm,
    borderRadius: 6,
  },
  loginButtonText: {
    color: colors.text,
    fontSize: 14,
    fontWeight: '600',
  },
  nav: {
    flexDirection: 'row',
    padding: spacing.md,
    gap: spacing.sm,
    backgroundColor: colors.card,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  navButton: {
    flex: 1,
    padding: spacing.md,
    borderRadius: 8,
    backgroundColor: colors.background,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
  },
  activeNav: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  navText: {
    color: colors.textSecondary,
    fontSize: 14,
    fontWeight: '600',
  },
  activeNavText: {
    color: colors.text,
  },
  content: {
    flex: 1,
  },
  stockActions: {
    flexDirection: 'row',
    padding: spacing.sm,
    gap: spacing.sm,
    marginBottom: spacing.sm,
  },
  actionButton: {
    flex: 1,
    padding: spacing.sm,
    borderRadius: 8,
    backgroundColor: colors.background,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
  },
  activeAction: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  tradeButton: {
    backgroundColor: colors.success,
    borderColor: colors.success,
  },
  actionButtonText: {
    color: colors.text,
    fontSize: 14,
    fontWeight: '600',
  },
  authRequired: {
    color: colors.textSecondary,
    fontSize: 18,
    textAlign: 'center',
    marginTop: spacing.xl,
  },
  // Стили для всплывающего окна
  popupOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 1000,
  },
  overlayBackground: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
  },
  popupContainer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: colors.background,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    maxHeight: '80%',
  },
  popupHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: spacing.md,
    paddingTop: spacing.lg,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  popupTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: colors.text,
  },
  popupContent: {
    flex: 1,
    padding: spacing.md,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: spacing.md,
    paddingTop: 50,
    backgroundColor: colors.background,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  closeButton: {
    fontSize: 20,
    color: colors.textSecondary,
    padding: spacing.sm,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: colors.text,
  },
  placeholder: {
    width: 40,
  },
  modalContent: {
    flex: 1,
    padding: spacing.md,
  },
  stockInfo: {
    backgroundColor: colors.card,
    padding: spacing.lg,
    borderRadius: 12,
    margin: spacing.md,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    marginBottom: spacing.md,
  },
  stockName: {
    fontSize: 16,
    color: colors.textSecondary,
    marginBottom: spacing.xs,
  },
  stockPrice: {
    fontSize: 20,
    fontWeight: 'bold',
    color: colors.text,
    marginBottom: spacing.xs,
  },
  stockChange: {
    fontSize: 16,
    fontWeight: '500',
  },
  positive: {
    color: colors.success,
  },
  negative: {
    color: colors.danger,
  },
  chartContainer: {
    backgroundColor: colors.card,
    padding: spacing.lg,
    borderRadius: 12,
    margin: spacing.md,
    borderWidth: 1,
    borderColor: colors.border,
    marginBottom: spacing.md,
  },
});

export default Dashboard;
