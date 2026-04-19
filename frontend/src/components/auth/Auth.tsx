import React, { useState } from 'react';
import Login from './Login';
import Register from './Register';
import './auth.css';

type AuthMode = 'login' | 'register';

interface AuthProps {
  onLogin?: (email: string, password: string) => void;
  onRegister?: (email: string, password: string, name: string) => void;
}

const Auth: React.FC<AuthProps> = ({ onLogin, onRegister }) => {
  const [mode, setMode] = useState<AuthMode>('login');

  const handleLogin = (email: string, password: string) => {
    console.log('Login attempt:', { email });
    if (onLogin) {
      onLogin(email, password);
    }
  };

  const handleRegister = (email: string, password: string, name: string) => {
    console.log('Register attempt:', { email, name });
    if (onRegister) {
      onRegister(email, password, name);
    }
  };

  return (
    <div className="auth-wrapper">
      {mode === 'login' ? (
        <Login
          onLogin={handleLogin}
          onSwitchToRegister={() => setMode('register')}
        />
      ) : (
        <Register
          onRegister={handleRegister}
          onSwitchToLogin={() => setMode('login')}
        />
      )}
    </div>
  );
};

export default Auth;
