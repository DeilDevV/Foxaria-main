import { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';
import { Eye, EyeOff, Lock, LogIn, User } from 'lucide-react';

export default function Login() {
  const { login, user } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (user) navigate('/profile', { replace: true });
  }, [navigate, user]);

  if (user) return null;

  async function handleSubmit(e) {
    e.preventDefault();
    if (!username.trim()) {
      toast.error('Введите никнейм');
      return;
    }
    if (!password) {
      toast.error('Введите пароль');
      return;
    }

    setLoading(true);
    try {
      await login(username.trim(), password);
      toast.success(`Добро пожаловать, ${username.trim()}!`);
      navigate('/profile', { replace: true });
    } catch (err) {
      toast.error(err.response?.data?.error || 'Ошибка входа');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden px-4 pt-20">
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="particle -left-16 top-20 h-80 w-80" />
        <div className="particle -right-12 bottom-10 h-72 w-72" style={{ background: 'radial-gradient(circle, rgba(255,160,84,0.4), transparent)' }} />
      </div>

      <div className="relative w-full max-w-md animate-slide-up">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-main text-2xl font-black text-white shadow-2xl shadow-orange-500/20">
            F
          </div>
          <h1 className="gradient-text text-3xl font-black">Войти в кабинет</h1>
          <p className="mt-2 text-gray-500">Используйте игровой никнейм и пароль от аккаунта Foxaria.</p>
        </div>

        <div className="glass p-8">
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="mb-2 block text-sm font-medium text-gray-400">Игровой никнейм</label>
              <div className="relative">
                <User size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-500" />
                <input
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="Ваш никнейм на сервере"
                  className="input-field pl-11"
                  autoComplete="username"
                  autoFocus
                />
              </div>
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-gray-400">Пароль</label>
              <div className="relative">
                <Lock size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-500" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Ваш пароль от аккаунта"
                  className="input-field pl-11 pr-11"
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((value) => !value)}
                  className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-500 transition-colors hover:text-gray-300"
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="btn-gradient w-full py-4 text-base disabled:cursor-not-allowed disabled:opacity-60"
            >
              <span className="inline-flex items-center gap-3">
                {loading ? <span className="h-5 w-5 rounded-full border-2 border-white/30 border-t-white animate-spin" /> : <LogIn size={18} />}
                Войти
              </span>
            </button>
          </form>

          <div className="glass-orange mt-6 rounded-2xl p-4">
            <p className="text-sm leading-6 text-gray-300">
              Используйте данные игрового аккаунта. Если вы еще не зарегистрированы, зайдите на сервер и выполните
              регистрацию прямо в игре.
            </p>
          </div>
        </div>

        <div className="mt-6 text-center">
          <div className="flex items-center justify-center gap-4 text-sm text-gray-500">
            <Link to="/news" className="transition-colors hover:text-orange-300">Новости</Link>
            <span>•</span>
            <Link to="/shop" className="transition-colors hover:text-orange-300">Магазин</Link>
            <span>•</span>
            <Link to="/bans" className="transition-colors hover:text-orange-300">Бан-лист</Link>
          </div>
        </div>
      </div>
    </div>
  );
}
