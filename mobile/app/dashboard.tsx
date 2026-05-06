import React from 'react';
import { View } from 'react-native';
import { useAuth } from '../src/context/AuthContext';
import Dashboard from '../src/pages/Dashboard';
import { useRouter } from 'expo-router';

export default function DashboardScreen() {
  const { user } = useAuth();
  const router = useRouter();

  React.useEffect(() => {
    if (!user) {
      router.replace('/');
    }
  }, [user, router]);

  return <Dashboard />;
}
