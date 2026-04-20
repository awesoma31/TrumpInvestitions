import React from 'react';
import { View, StyleSheet } from 'react-native';
import { useAuth } from '../src/context/AuthContext';
import Auth from '../src/components/auth/Auth';
import { useRouter } from 'expo-router';

export default function AuthScreen() {
  const { user } = useAuth();
  const router = useRouter();

  React.useEffect(() => {
    if (user) {
      router.replace('/dashboard');
    }
  }, [user, router]);

  return (
    <View style={styles.container}>
      <Auth />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
});
