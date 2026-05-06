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
        if (!token) {
          setUser(null);
          return;
        }

        const userData = await AsyncStorage.getItem('user_data');
        if (userData) {
          const loadedUser: User = JSON.parse(userData);
          setUser(loadedUser);
        } else {
          // No user data, clear tokens
          await AsyncStorage.removeItem('access_token');
          await AsyncStorage.removeItem('refresh_token');
          setUser(null);
        }
      } catch (error) {
        console.error('Auth check error:', error);
        await AsyncStorage.removeItem('user_data');
        await AsyncStorage.removeItem('access_token');
        await AsyncStorage.removeItem('refresh_token');
        setUser(null);
      } finally {
        setIsLoading(false);
      }
    };

    checkAuth();
  }, []);

  const login = async (login: string, password: string) => {
    try {
      console.log('Attempting login with:', login);
      const response = await apiClient.login(login, password);
      console.log('Login response:', response);
      
      const user: User = {
        id: response.user.id.toString(),
        email: response.user.email,
        name: response.user.username,
      };
      
      await AsyncStorage.setItem('user_data', JSON.stringify(user));
      await AsyncStorage.setItem('access_token', response.accessToken);
      await AsyncStorage.setItem('refresh_token', response.refreshToken);
      setUser(user);
      console.log('Login successful, user stored:', user);
    } catch (error) {
      console.error('Login error:', error);
      throw error;
    }
  };

  const register = async (username: string, email: string, password: string) => {
    try {
      console.log('Attempting registration with:', username, email);
      const response = await apiClient.register(username, email, password);
      console.log('Registration response:', response);
      
      const user: User = {
        id: response.user.id.toString(),
        email: response.user.email,
        name: response.user.username,
      };
      
      await AsyncStorage.setItem('user_data', JSON.stringify(user));
      await AsyncStorage.setItem('access_token', response.accessToken);
      await AsyncStorage.setItem('refresh_token', response.refreshToken);
      setUser(user);
      console.log('Registration successful, user stored:', user);
    } catch (error) {
      console.error('Registration error:', error);
      throw error;
    }
  };

  const logout = async () => {
    try {
      console.log('Attempting logout...');
      await apiClient.logout();
      await AsyncStorage.removeItem('user_data');
      await AsyncStorage.removeItem('access_token');
      await AsyncStorage.removeItem('refresh_token');
      setUser(null);
      console.log('Logout successful');
    } catch (error) {
      console.error('Logout error:', error);
      // Даже если ошибка logout, все равно очищаем данные
      await AsyncStorage.removeItem('user_data');
      await AsyncStorage.removeItem('access_token');
      await AsyncStorage.removeItem('refresh_token');
      setUser(null);
    }
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
