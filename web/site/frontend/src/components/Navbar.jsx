import { useState, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Menu, X, ShoppingBag, Newspaper, Users, Calendar, Shield, User, LogOut, Settings, ChevronDown } from 'lucide-react';

const navLinks = [
  { to: '/news', label: 'Новости', icon: Newspaper },
  { to: '/shop', label: 'Магазин', icon: ShoppingBag },
  { to: '/bans', label: 'Бан-лист', icon: Shield },
  { to: '/staff', label: 'Состав', icon: Users },
  { to: '/wipes', label: 'Вайпы', icon: Calendar },
];

export default function Navbar() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handler = () => setScrolled(window.scrollY > 20);
    window.addEventListener('scroll', handler);
    return () => window.removeEventListener('scroll', handler);
  }, []);

  useEffect(() => {
    setMobileOpen(false);
    setUserMenuOpen(false);
  }, [location.pathname]);

  function handleLogout() {
    logout();
    navigate('/');
  }

  const isActive = (to) => location.pathname === to || location.pathname.startsWith(to + '/');

  return (
    <nav className={`fixed top-0 left-0 right-0 z-40 transition-all duration-300 ${scrolled ? 'glass border-b border-white/5 shadow-2xl' : 'bg-transparent'}`}>
      <div className="max-w-7xl mx-auto px-4 sm:px-6">
        <div className="flex items-center justify-between h-16">

          {/* Logo */}
          <Link to="/" className="flex items-center gap-3 group">
            <div className="relative w-9 h-9">
              <div className="absolute inset-0 rounded-lg bg-gradient-main opacity-80 group-hover:opacity-100 transition-opacity animate-glow-pulse" />
              <div className="absolute inset-0 flex items-center justify-center text-white font-black text-sm">F</div>
            </div>
            <span className="gradient-text font-black text-xl tracking-tight">FOXARIA</span>
          </Link>

          {/* Desktop nav */}
          <div className="hidden md:flex items-center gap-1">
            {navLinks.map(({ to, label, icon: Icon }) => (
              <Link
                key={to}
                to={to}
                className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition-all duration-200 ${
                  isActive(to)
                    ? 'text-orange-400 bg-orange-500/10 border border-orange-500/20'
                    : 'text-gray-400 hover:text-white hover:bg-white/5'
                }`}
              >
                <Icon size={16} />
                {label}
              </Link>
            ))}
          </div>

          {/* Right: user / login */}
          <div className="hidden md:flex items-center gap-3">
            {user ? (
              <div className="relative">
                <button
                  onClick={() => setUserMenuOpen(!userMenuOpen)}
                  className="flex items-center gap-3 pl-2 pr-3 py-2 glass rounded-xl hover:border-orange-500/30 transition-all duration-200 group"
                >
                  <img
                    src={user.avatar || `https://mc-heads.net/avatar/${user.username}/32`}
                    alt={user.username}
                    className="w-8 h-8 rounded-lg"
                  />
                  <div className="text-left">
                    <p className="text-sm font-semibold text-white leading-none">{user.username}</p>
                    <p className="text-xs text-orange-400 leading-none mt-0.5">{user.donateLabel || user.group}</p>
                  </div>
                  <ChevronDown size={14} className={`text-gray-400 transition-transform ${userMenuOpen ? 'rotate-180' : ''}`} />
                </button>

                {userMenuOpen && (
                  <div className="absolute right-0 top-full mt-2 w-52 glass border border-white/10 rounded-xl overflow-hidden shadow-2xl animate-slide-up">
                    <div className="p-1">
                      <Link to="/profile" className="flex items-center gap-3 px-4 py-3 rounded-lg hover:bg-white/5 text-gray-300 hover:text-white transition-all">
                        <User size={16} className="text-orange-400" />
                        <span className="text-sm">Личный кабинет</span>
                      </Link>
                      {user.isAdmin && (
                        <Link to="/admin" className="flex items-center gap-3 px-4 py-3 rounded-lg hover:bg-white/5 text-gray-300 hover:text-white transition-all">
                          <Settings size={16} className="text-violet-400" />
                          <span className="text-sm">Администрирование</span>
                        </Link>
                      )}
                      <div className="my-1 h-px bg-white/5" />
                      <button
                        onClick={handleLogout}
                        className="w-full flex items-center gap-3 px-4 py-3 rounded-lg hover:bg-red-500/10 text-gray-400 hover:text-red-400 transition-all"
                      >
                        <LogOut size={16} />
                        <span className="text-sm">Выйти</span>
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <Link to="/login" className="btn-gradient text-sm px-5 py-2">
                <span>Войти</span>
              </Link>
            )}
          </div>

          {/* Mobile burger */}
          <button
            onClick={() => setMobileOpen(!mobileOpen)}
            className="md:hidden p-2 rounded-lg text-gray-400 hover:text-white hover:bg-white/5 transition-all"
          >
            {mobileOpen ? <X size={22} /> : <Menu size={22} />}
          </button>
        </div>
      </div>

      {/* Mobile menu */}
      {mobileOpen && (
        <div className="md:hidden glass border-t border-white/5 animate-slide-up">
          <div className="px-4 py-4 space-y-1">
            {navLinks.map(({ to, label, icon: Icon }) => (
              <Link
                key={to}
                to={to}
                className={`flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-all ${
                  isActive(to)
                    ? 'text-orange-400 bg-orange-500/10'
                    : 'text-gray-400 hover:text-white hover:bg-white/5'
                }`}
              >
                <Icon size={18} />
                {label}
              </Link>
            ))}
            <div className="pt-3 border-t border-white/5">
              {user ? (
                <>
                  <Link to="/profile" className="flex items-center gap-3 px-4 py-3 rounded-xl text-gray-300 hover:bg-white/5">
                    <img src={user.avatar} alt="" className="w-7 h-7 rounded-lg" />
                    <div>
                      <p className="text-sm font-semibold">{user.username}</p>
                      <p className="text-xs text-orange-400">{user.group}</p>
                    </div>
                  </Link>
                  {user.isAdmin && (
                    <Link to="/admin" className="flex items-center gap-3 px-4 py-3 rounded-xl text-gray-300 hover:bg-white/5">
                      <Settings size={18} className="text-violet-400" /> Администрирование
                    </Link>
                  )}
                  <button onClick={handleLogout} className="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-red-400 hover:bg-red-500/10">
                    <LogOut size={18} /> Выйти
                  </button>
                </>
              ) : (
                <Link to="/login" className="btn-gradient block text-center text-sm">
                  <span>Войти</span>
                </Link>
              )}
            </div>
          </div>
        </div>
      )}
    </nav>
  );
}
