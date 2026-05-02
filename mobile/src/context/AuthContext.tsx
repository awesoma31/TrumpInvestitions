import React, { createContext, useContext, useState, useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { apiClient } from '../services/apiClient';
import type { User } from '../types/auth';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (login: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const checkAuth = async () => {
      try {
        const token = await AsyncStorage.getItem('access_token');
        if (token) {
          const userData = await AsyncStorage.getItem('user_data');
          setUser(userData ? JSON.parse(userData) : null);
        }
      } catch (error) {
        console.error('Auth check error:', error);
      } finally {
        setIsLoading(false);
      }
    };

    checkAuth();
  }, []);

  const login = async (login: string, password: string) => {
    const response = await apiClient.login(login, password);
    
    const user: User = {
      id: response.user.id.toString(),
      email: response.user.email,
      name: response.user.username,
    };
    
    await AsyncStorage.setItem('user_data', JSON.stringify(user));
    setUser(user);
  };

  const register = async (username: string, email: string, password: string) => {
    const response = await apiClient.register(username, email, password);
    
    const user: User = {
      id: response.user.id.toString(),
      email: response.user.email,
      name: response.user.username,
    };
    
    await AsyncStorage.setItem('user_data', JSON.stringify(user));
    setUser(user);
  };

  const logout = async () => {
    await apiClient.logout();
    await AsyncStorage.removeItem('user_data');
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
