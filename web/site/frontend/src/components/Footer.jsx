import { Link } from 'react-router-dom';
import { Shield, Newspaper, Users, ShoppingBag, Calendar } from 'lucide-react';

export default function Footer() {
  return (
    <footer className="mt-20 border-t border-white/5">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-12">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
          {/* Brand */}
          <div className="md:col-span-1">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-8 h-8 rounded-lg bg-gradient-main flex items-center justify-center text-white font-black text-sm">F</div>
              <span className="gradient-text font-black text-lg">FOXARIA</span>
            </div>
            <p className="text-gray-500 text-sm leading-relaxed">
              Minecraft-анархия Foxaria: PvP, гильдии, приваты и честная экономика.
            </p>
            <div className="mt-4 flex gap-3">
              <a href="#" className="w-9 h-9 rounded-lg bg-white/5 hover:bg-white/10 flex items-center justify-center text-gray-400 hover:text-white transition-all text-sm font-bold">VK</a>
              <a href="#" className="w-9 h-9 rounded-lg bg-white/5 hover:bg-white/10 flex items-center justify-center text-gray-400 hover:text-white transition-all text-sm">DC</a>
              <a href="#" className="w-9 h-9 rounded-lg bg-white/5 hover:bg-white/10 flex items-center justify-center text-gray-400 hover:text-white transition-all text-sm">TG</a>
            </div>
          </div>

          {/* Navigation */}
          <div>
            <h3 className="text-white font-semibold mb-4">Навигация</h3>
            <ul className="space-y-2">
              {[
                { to: '/news', label: 'Новости', icon: Newspaper },
                { to: '/shop', label: 'Магазин', icon: ShoppingBag },
                { to: '/bans', label: 'Бан-лист', icon: Shield },
                { to: '/staff', label: 'Состав', icon: Users },
                { to: '/wipes', label: 'Вайпы', icon: Calendar },
              ].map(({ to, label, icon: Icon }) => (
                <li key={to}>
                  <Link to={to} className="flex items-center gap-2 text-gray-500 hover:text-orange-400 text-sm transition-colors">
                    <Icon size={14} />
                    {label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          {/* Servers */}
          <div>
            <h3 className="text-white font-semibold mb-4">Серверы</h3>
            <div className="space-y-2">
              <div className="flex items-center gap-2 text-sm text-gray-500">
                <div className="w-1.5 h-1.5 rounded-full bg-green-400" />
                Анархия (основной режим)
              </div>
              <div className="flex items-center gap-2 text-sm text-gray-500">
                <div className="w-1.5 h-1.5 rounded-full bg-orange-400" />
                Лобби и сеть BungeeCord
              </div>
            </div>
            <div className="mt-4 p-3 glass-orange rounded-xl">
              <p className="text-xs text-gray-400">IP адрес</p>
              <p className="text-orange-400 font-mono text-sm font-semibold mt-1">play.foxaria.ru</p>
            </div>
          </div>

          {/* Info */}
          <div>
            <h3 className="text-white font-semibold mb-4">Информация</h3>
            <ul className="space-y-2 text-sm text-gray-500">
              <li><a href="#" className="hover:text-white transition-colors">Правила сервера</a></li>
              <li><a href="#" className="hover:text-white transition-colors">Пользовательское соглашение</a></li>
              <li><a href="#" className="hover:text-white transition-colors">Политика возврата</a></li>
              <li><a href="#" className="hover:text-white transition-colors">Поддержка</a></li>
            </ul>
          </div>
        </div>

        <div className="gradient-divider my-8" />
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4 text-gray-600 text-sm">
          <p>© {new Date().getFullYear()} Foxaria. Все права защищены.</p>
          <p>Minecraft — торговая марка Mojang Studios. Не связан с Mojang.</p>
        </div>
      </div>
    </footer>
  );
}
