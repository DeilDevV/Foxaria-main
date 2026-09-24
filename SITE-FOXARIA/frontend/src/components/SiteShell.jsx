'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  Calendar,
  ChevronRight,
  LogIn,
  LogOut,
  Menu,
  Newspaper,
  Shield,
  ShoppingBag,
  User,
  Users,
  X,
} from 'lucide-react';
import BrandMark from '@/components/BrandMark';
import { useAuth } from '@/context/AuthContext';

const navItems = [
  { href: '/news', label: 'Новости', icon: Newspaper },
  { href: '/shop', label: 'Магазин', icon: ShoppingBag },
  { href: '/bans', label: 'Бан-лист', icon: Shield },
  { href: '/staff', label: 'Состав', icon: Users },
  { href: '/wipes', label: 'Вайпы', icon: Calendar },
];

export default function SiteShell({ children }) {
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12);
    onScroll();
    window.addEventListener('scroll', onScroll);
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  const isActive = (href) => pathname === href || pathname.startsWith(`${href}/`);

  return (
    <div className="min-h-screen">
      <header className={`fixed inset-x-0 top-0 z-50 transition-all duration-300 ${scrolled ? 'px-3 pt-3' : 'px-0 pt-0'}`}>
        <div className={`mx-auto max-w-7xl transition-all duration-300 ${scrolled ? 'rounded-[28px] border border-white/10 bg-[rgba(10,12,16,0.8)] shadow-2xl shadow-black/30 backdrop-blur-2xl' : 'bg-transparent'}`}>
          <div className="flex items-center justify-between gap-4 px-4 py-4 sm:px-6">
            <Link href="/" className="group flex items-center gap-3">
              <BrandMark compact />
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.35em] text-orange-400/80">Anarchy Realm</p>
                <p className="text-lg font-black tracking-[0.2em] text-white transition-colors group-hover:text-orange-300">FOXARIA</p>
              </div>
            </Link>

            <nav className="hidden items-center gap-2 lg:flex">
              {navItems.map(({ href, label, icon: Icon }) => (
                <Link
                  key={href}
                  href={href}
                  className={`nav-pill ${isActive(href) ? 'nav-pill-active' : ''}`}
                >
                  <Icon size={16} />
                  <span>{label}</span>
                </Link>
              ))}
            </nav>

            <div className="hidden items-center gap-3 lg:flex">
              <div className="status-chip">
                <span className="status-chip-dot" />
                <span>play.foxaria.ru</span>
              </div>

              {user ? (
                <div className="flex items-center gap-2">
                  <Link href="/profile" className="user-pill">
                    <img
                      src={user.avatar || `https://mc-heads.net/avatar/${user.username}/32`}
                      alt={user.username}
                      className="h-9 w-9 rounded-xl border border-white/10"
                    />
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-white">{user.username}</p>
                      <p className="truncate text-xs text-orange-300">{user.donateLabel || user.group}</p>
                    </div>
                    <ChevronRight size={16} className="text-orange-400" />
                  </Link>
                  <button onClick={logout} className="icon-button" aria-label="Выйти">
                    <LogOut size={16} />
                  </button>
                </div>
              ) : (
                <Link href="/login" className="btn-gradient px-5 py-3 text-sm">
                  <span className="inline-flex items-center gap-2">
                    <LogIn size={16} />
                    Войти
                  </span>
                </Link>
              )}
            </div>

            <button
              onClick={() => setMobileOpen((value) => !value)}
              className="icon-button lg:hidden"
              aria-label="Открыть меню"
            >
              {mobileOpen ? <X size={20} /> : <Menu size={20} />}
            </button>
          </div>

          {mobileOpen && (
            <div className="border-t border-white/10 px-4 pb-4 lg:hidden">
              <div className="grid gap-2 pt-4">
                {navItems.map(({ href, label, icon: Icon }) => (
                  <Link
                    key={href}
                    href={href}
                    className={`nav-pill justify-between ${isActive(href) ? 'nav-pill-active' : ''}`}
                  >
                    <span className="inline-flex items-center gap-3">
                      <Icon size={16} />
                      {label}
                    </span>
                    <ChevronRight size={15} />
                  </Link>
                ))}

                {user ? (
                  <>
                    <Link href="/profile" className="nav-pill justify-between">
                      <span className="inline-flex items-center gap-3">
                        <User size={16} />
                        Профиль
                      </span>
                      <ChevronRight size={15} />
                    </Link>
                    <button onClick={logout} className="nav-pill w-full justify-between text-left">
                      <span className="inline-flex items-center gap-3">
                        <LogOut size={16} />
                        Выйти
                      </span>
                    </button>
                  </>
                ) : (
                  <Link href="/login" className="btn-gradient mt-2 text-center">
                    <span>Войти в кабинет</span>
                  </Link>
                )}
              </div>
            </div>
          )}
        </div>
      </header>

      <main>{children}</main>

      <footer className="px-4 pb-8 pt-20">
        <div className="mx-auto max-w-7xl rounded-[32px] border border-white/10 bg-[linear-gradient(180deg,rgba(255,122,26,0.08),rgba(10,12,16,0.9))] p-8 shadow-2xl shadow-black/20">
          <div className="grid gap-10 lg:grid-cols-[1.3fr_1fr_1fr]">
            <div>
              <div className="mb-5 flex items-center gap-4">
                <BrandMark />
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.35em] text-orange-400/80">No Rules. No Mercy.</p>
                  <h2 className="text-2xl font-black tracking-[0.18em] text-white">FOXARIA</h2>
                </div>
              </div>
              <p className="max-w-xl text-sm leading-7 text-gray-400">
                Темный, агрессивный и более собранный интерфейс для Minecraft-анархии: новости, покупки,
                профиль игрока и админка в одной цельной оболочке.
              </p>
            </div>

            <div>
              <p className="mb-4 text-sm font-semibold uppercase tracking-[0.25em] text-orange-300">Разделы</p>
              <div className="grid gap-2">
                {navItems.map(({ href, label }) => (
                  <Link key={href} href={href} className="footer-link">
                    {label}
                  </Link>
                ))}
              </div>
            </div>

            <div>
              <p className="mb-4 text-sm font-semibold uppercase tracking-[0.25em] text-orange-300">Подключение</p>
              <div className="space-y-4">
                <div className="rounded-2xl border border-orange-500/20 bg-orange-500/10 p-4">
                  <p className="text-xs uppercase tracking-[0.25em] text-orange-200/80">Server IP</p>
                  <p className="mt-2 font-mono text-lg font-bold text-white">play.foxaria.ru</p>
                </div>
                <p className="text-sm text-gray-500">
                  Minecraft является торговой маркой Mojang Studios. Проект FOXARIA не аффилирован с Mojang.
                </p>
              </div>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
