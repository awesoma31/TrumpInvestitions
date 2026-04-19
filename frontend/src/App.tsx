import { useAuth } from './context/AuthContext';
import { Auth } from './components/auth';

function App() {
  const { login, register, isAuthenticated, user } = useAuth();

  if (isAuthenticated && user) {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        minHeight: '100vh',
        background: '#000000'
      }}>
        <div style={{ 
          background: '#1a1a1a', 
          border: '1px solid #333',
          padding: '40px', 
          borderRadius: '12px',
          textAlign: 'center',
          boxShadow: '0 10px 40px rgba(0, 0, 0, 0.5)'
        }}>
          <h1 style={{ color: '#ffffff', marginBottom: '10px' }}>
            Добро пожаловать, {user.name}!
          </h1>
          <p style={{ color: '#cccccc', marginBottom: '20px' }}>
            Email: {user.email}
          </p>
          <button 
            onClick={() => window.location.reload()}
            style={{
              padding: '12px 24px',
              background: '#667eea',
              color: 'white',
              border: 'none',
              borderRadius: '8px',
              cursor: 'pointer',
              fontSize: '16px',
              fontWeight: '600',
              marginTop: '20px'
            }}
          >
            Выйти
          </button>
        </div>
      </div>
    );
  }

  return (
    <Auth 
      onLogin={login}
      onRegister={register}
    />
  );
}

export default App
