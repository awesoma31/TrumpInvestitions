import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';
import { useAuth } from '../src/context/AuthContext';
import StockList from '../src/components/market/StockList';
import { colors, spacing } from '../src/theme';

export default function MarketScreen() {
  const router = useRouter();
  const { user } = useAuth();

  React.useEffect(() => {
    if (user) {
      router.replace('/dashboard');
    }
  }, [user, router]);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Трамплин инвестиций</Text>
        <TouchableOpacity 
          style={styles.loginButton} 
          onPress={() => router.push('/auth')}
        >
          <Text style={styles.loginButtonText}>Войти</Text>
        </TouchableOpacity>
      </View>
      <StockList />
    </View>
  );
}

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
});
