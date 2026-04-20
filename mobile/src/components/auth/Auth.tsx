import React, { useState, useEffect } from 'react';
import { View } from 'react-native';
import Login from './Login';
import Register from './Register';
import { useAuth } from '../../context/AuthContext';

interface AuthProps {
  onRegister?: () => void;
  onAuthComplete?: () => void;
}

const Auth: React.FC<AuthProps> = ({ onRegister, onAuthComplete }) => {
  const [isLogin, setIsLogin] = useState(true);
  const { user } = useAuth();

  // Закрыть модальное окно после успешной авторизации
  useEffect(() => {
    if (user && onAuthComplete) {
      onAuthComplete();
    }
  }, [user, onAuthComplete]);

  return (
    <View style={{ flex: 1 }}>
      {isLogin ? (
        <Login onRegister={() => setIsLogin(false)} />
      ) : (
        <Register onLogin={() => setIsLogin(true)} />
      )}
    </View>
  );
};

export default Auth;
