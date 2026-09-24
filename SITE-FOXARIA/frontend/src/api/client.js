import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

// Attach JWT token to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('foxaria_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Handle 401 globally — но не при самом логине (там 401 = неверный пароль)
api.interceptors.response.use(
  (res) => res,
  (err) => {
    const isAuthEndpoint = err.config?.url?.includes('/auth/login') || err.config?.url?.includes('/auth/register');
    if (err.response?.status === 401 && !isAuthEndpoint) {
      localStorage.removeItem('foxaria_token');
      localStorage.removeItem('foxaria_user');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export default api;
