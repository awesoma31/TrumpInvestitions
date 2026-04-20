import React, { createContext, useContext, useState, useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
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
      const token = await AsyncStorage.getItem('auth_token');
      if (token) {
        const userData = await AsyncStorage.getItem('user_data');
        setUser(userData ? JSON.parse(userData) : null);
      }
      setIsLoading(false);
    };

    checkAuth();
  }, []);

  const login = async (login: string, password: string) => {
    const mockUser: User = {
      id: '1',
      email: login,
      name: login.split('@')[0],
    };
    const mockToken = 'mock_token_' + Date.now();
    
    await AsyncStorage.setItem('auth_token', mockToken);
    await AsyncStorage.setItem('user_data', JSON.stringify(mockUser));
    setUser(mockUser);
  };

  const register = async (username: string, email: string, password: string) => {
    const mockUser: User = {
      id: '1',
      email,
      name: username,
    };
    const mockToken = 'mock_token_' + Date.now();
    
    await AsyncStorage.setItem('auth_token', mockToken);
    await AsyncStorage.setItem('user_data', JSON.stringify(mockUser));
    setUser(mockUser);
  };

  const logout = async () => {
    await AsyncStorage.removeItem('auth_token');
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
