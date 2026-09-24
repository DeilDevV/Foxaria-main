import { createContext, useContext, useState, useEffect } from 'react';
import api from '../api/client';

const AuthContext = createContext({
  user: null,
  loading: true,
  login: async () => null,
  logout: () => {},
});

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('foxaria_token');
    const savedUser = localStorage.getItem('foxaria_user');
    if (token && savedUser) {
      setUser(JSON.parse(savedUser));
      // Refresh user data from server
      api.get('/auth/me')
        .then(res => {
          setUser(res.data);
          localStorage.setItem('foxaria_user', JSON.stringify(res.data));
        })
        .catch(() => {
          localStorage.removeItem('foxaria_token');
          localStorage.removeItem('foxaria_user');
          setUser(null);
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  async function login(username, password) {
    const res = await api.post('/auth/login', { username, password });
    localStorage.setItem('foxaria_token', res.data.token);
    localStorage.setItem('foxaria_user', JSON.stringify(res.data.user));
    setUser(res.data.user);
    return res.data.user;
  }

  function logout() {
    localStorage.removeItem('foxaria_token');
    localStorage.removeItem('foxaria_user');
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
