import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import BrandMark from '../components/BrandMark';
import {
  ArrowRight,
  Check,
  Copy,
  Flame,
  Newspaper,
  Radio,
  Shield,
  ShoppingBag,
  Skull,
  Sparkles,
  Sword,
  Trophy,
  Users,
} from 'lucide-react';

const FEATURES = [
  {
    icon: Skull,
    title: 'Жесткая анархия',
    desc: 'Никаких мягких ограничений. Проект ощущается опасным, быстрым и живым с первых минут.',
  },
  {
    icon: Sword,
    title: 'Честное PvP-давление',
    desc: 'Упор на конфликт, выживание и доминирование, а не на случайный визуальный шум.',
  },
  {
    icon: Shield,
    title: 'Продуманная инфраструктура',
    desc: 'Приваты, гильдии, экономика и личный кабинет встроены в один цельный интерфейс.',
  },
  {
    icon: Users,
    title: 'Социальный слой',
    desc: 'Состав, профили игроков, новости и история проекта ощущаются как единая живая сцена.',
  },
];

function NewsCard({ news }) {
  return (
    <Link
      to={`/news/${news.id}`}
      className="glass group block overflow-hidden p-5 transition-all duration-300 hover:-translate-y-1 hover:border-orange-500/20"
    >
      {news.image && (
        <div className="mb-4 h-44 overflow-hidden rounded-2xl bg-dark-800">
          <img
            src={news.image}
            alt={news.title}
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
        </div>
      )}
      <div className="mb-3 flex items-center gap-2">
        <span className="badge-orange text-xs">{news.server === 'all' ? 'Вся сеть' : news.server}</span>
        <span className="text-xs text-gray-500">{new Date(news.created_at).toLocaleDateString('ru-RU')}</span>
      </div>
      <h3 className="line-clamp-2 text-lg font-bold text-white transition-all group-hover:text-orange-200">{news.title}</h3>
      {news.preview && <p className="mt-3 line-clamp-3 text-sm leading-6 text-gray-400">{news.preview}</p>}
    </Link>
  );
}

export default function Home() {
  const { user } = useAuth();
  const [servers, setServers] = useState([]);
  const [news, setNews] = useState([]);
  const [copied, setCopied] = useState(false);
  const [bungeeOnline, setBungeeOnline] = useState(null);

  useEffect(() => {
    function loadServers() {
      api.get('/servers').then((r) => setServers(r.data || [])).catch(() => {});
    }

    function loadOnline() {
      api.get('/servers/online').then((r) => {
        const data = r.data;
        setBungeeOnline(data?.available === false ? null : (data?.total ?? null));
      }).catch(() => setBungeeOnline(null));
    }

    loadServers();
    loadOnline();
    api.get('/news?limit=3').then((r) => setNews(r.data.news || [])).catch(() => {});

    const interval = setInterval(() => {
      loadServers();
      loadOnline();
    }, 15000);

    return () => clearInterval(interval);
  }, []);

  const totalOnline = bungeeOnline !== null
    ? bungeeOnline
    : servers.reduce((sum, server) => sum + (server.players || 0), 0);
  const anyOnline = bungeeOnline !== null || servers.some((server) => server.online);
  const mainServer = servers[0] || null;
  const serverIp = mainServer?.ip || 'play.foxaria.ru';

  function copyIP() {
    navigator.clipboard.writeText(serverIp);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  }

  return (
    <div className="pt-20">
      <section className="relative overflow-hidden px-4 pb-10 pt-12 sm:px-6 lg:px-8">
        <div className="pointer-events-none absolute inset-0">
          <div className="particle -left-20 top-24 h-[24rem] w-[24rem]" />
          <div className="particle bottom-10 right-0 h-[22rem] w-[22rem]" style={{ animationDelay: '2s' }} />
        </div>

        <div className="relative mx-auto grid max-w-7xl items-center gap-8 lg:grid-cols-[1.2fr_0.8fr]">
          <div className="animate-slide-up">
            <div className="mb-6 inline-flex items-center gap-3 rounded-full border border-orange-500/20 bg-orange-500/10 px-4 py-2 text-sm text-orange-200">
              <Radio size={15} className={anyOnline ? 'text-green-400' : 'text-orange-300'} />
              {anyOnline ? (
                <span>Сеть в онлайне: <strong className="text-white">{totalOnline}</strong> игроков прямо сейчас</span>
              ) : (
                <span>Серверная сеть запускается и поднимает статусы</span>
              )}
            </div>

            <div className="mb-6 flex items-center gap-5">
              <BrandMark />
              <div>
                <p className="text-sm font-semibold uppercase tracking-[0.35em] text-orange-300/80">Minecraft Anarchy Experience</p>
                <h1 className="gradient-text text-shadow text-5xl font-black leading-none sm:text-7xl xl:text-8xl">
                  FOXARIA
                </h1>
              </div>
            </div>

            <p className="max-w-2xl text-2xl font-semibold leading-tight text-white sm:text-3xl">
              Темная, агрессивная и собранная оболочка для проекта, где каждое действие должно ощущаться весомо.
            </p>
            <p className="mt-5 max-w-2xl text-base leading-8 text-gray-400 sm:text-lg">
              Выживай, строй, воюй, удерживай территорию и дави на экономику. Новый фронтенд делает
              проект визуально увереннее: меньше хаоса в интерфейсе, больше напряжения и характера.
            </p>

            <div className="mt-8 flex flex-col gap-4 sm:flex-row">
              <Link to="/shop" className="btn-gradient px-8 py-4 text-base">
                <span className="inline-flex items-center gap-2">
                  <ShoppingBag size={18} />
                  Открыть магазин
                </span>
              </Link>
              <Link to={user ? '/profile' : '/login'} className="btn-outline px-8 py-4 text-base">
                <span className="inline-flex items-center gap-2">
                  <Trophy size={18} />
                  {user ? 'Перейти в профиль' : 'Войти в кабинет'}
                </span>
              </Link>
            </div>

            <div className="mt-10 grid gap-4 sm:grid-cols-3">
              <div className="glass p-4">
                <p className="text-xs uppercase tracking-[0.3em] text-orange-300/70">Фокус</p>
                <p className="mt-2 text-xl font-bold text-white">Темный + оранжевый</p>
                <p className="mt-2 text-sm leading-6 text-gray-400">Без фиолетового шума и случайных акцентов.</p>
              </div>
              <div className="glass p-4">
                <p className="text-xs uppercase tracking-[0.3em] text-orange-300/70">Темп</p>
                <p className="mt-2 text-xl font-bold text-white">Быстрая читаемость</p>
                <p className="mt-2 text-sm leading-6 text-gray-400">Крупные блоки, чище навигация, лучше иерархия.</p>
              </div>
              <div className="glass p-4">
                <p className="text-xs uppercase tracking-[0.3em] text-orange-300/70">Образ</p>
                <p className="mt-2 text-xl font-bold text-white">Брутальный стиль</p>
                <p className="mt-2 text-sm leading-6 text-gray-400">Интерфейс больше похож на продукт, а не на шаблон.</p>
              </div>
            </div>
          </div>

          <div className="animate-slide-up lg:pl-6">
            <div className="glass relative overflow-hidden p-6 sm:p-8">
              <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-orange-400/70 to-transparent" />
              <div className="mb-6 flex items-start justify-between gap-4">
                <div>
                  <p className="text-xs uppercase tracking-[0.3em] text-orange-300/70">Основной вход</p>
                  <h2 className="mt-2 text-3xl font-black text-white">play.foxaria.ru</h2>
                </div>
                <div className="rounded-2xl border border-orange-500/20 bg-orange-500/10 p-3">
                  <Flame size={24} className="text-orange-300" />
                </div>
              </div>

              <button
                onClick={copyIP}
                className="flex w-full items-center justify-between rounded-[22px] border border-white/10 bg-dark-800/80 px-5 py-4 text-left transition-all hover:border-orange-500/30 hover:bg-dark-700"
              >
                <div>
                  <p className="text-xs uppercase tracking-[0.24em] text-gray-500">Server IP</p>
                  <p className="mt-2 font-mono text-lg font-bold text-orange-300">{serverIp}</p>
                </div>
                <div className="inline-flex items-center gap-2 rounded-full border border-orange-500/20 bg-orange-500/10 px-3 py-2 text-sm font-semibold text-orange-200">
                  {copied ? <Check size={16} /> : <Copy size={16} />}
                  {copied ? 'Скопировано' : 'Копировать'}
                </div>
              </button>

              <div className="mt-6 grid gap-3 sm:grid-cols-2">
                <div className="rounded-2xl border border-white/8 bg-dark-800/80 p-4">
                  <p className="text-xs uppercase tracking-[0.25em] text-gray-500">Игроков</p>
                  <p className="mt-2 text-3xl font-black text-white">{totalOnline}</p>
                </div>
                <div className="rounded-2xl border border-white/8 bg-dark-800/80 p-4">
                  <p className="text-xs uppercase tracking-[0.25em] text-gray-500">Состояние</p>
                  <p className={`mt-2 text-2xl font-black ${anyOnline ? 'text-green-400' : 'text-orange-300'}`}>
                    {anyOnline ? 'ONLINE' : 'BOOTING'}
                  </p>
                </div>
              </div>

              <div className="gradient-divider my-6" />

              <div className="space-y-3">
                <div className="flex items-center gap-3 text-sm text-gray-300">
                  <Sparkles size={16} className="text-orange-300" />
                  Новый дизайн держит единый визуальный ритм на всех разделах.
                </div>
                <div className="flex items-center gap-3 text-sm text-gray-300">
                  <Shield size={16} className="text-orange-300" />
                  Акценты остались теплыми и агрессивными, без холодного фиолета.
                </div>
                <div className="flex items-center gap-3 text-sm text-gray-300">
                  <Newspaper size={16} className="text-orange-300" />
                  Новостной, профильный и торговый блоки теперь легче читать.
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {servers.length > 0 && (
        <section className="page-container">
          <div className="mb-8 flex items-end justify-between gap-4">
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-[0.3em] text-orange-300/70">Network Status</p>
              <h2 className="section-title">Текущие сервера</h2>
              <p className="max-w-2xl text-gray-400">Живые статусы, игроки и быстрый доступ к основным точкам входа.</p>
            </div>
          </div>
          <div className={`grid gap-5 ${servers.length === 1 ? 'max-w-xl' : 'lg:grid-cols-2'}`}>
            {servers.map((server) => (
              <div key={server.id} className="glass animate-slide-up p-6 transition-all duration-300 hover:border-orange-500/20">
                <div className="mb-6 flex items-start gap-4">
                  <div className="rounded-2xl border border-orange-500/20 bg-orange-500/10 p-4 text-3xl">
                    {server.icon || '🔥'}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-3">
                      <h3 className="text-2xl font-black text-white">{server.name}</h3>
                      <span className={`badge ${server.online ? 'badge-green' : 'badge-red'}`}>
                        {server.online ? 'Онлайн' : 'Оффлайн'}
                      </span>
                    </div>
                    <p className="mt-2 text-sm leading-6 text-gray-400">{server.description}</p>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div className="rounded-2xl border border-white/8 bg-dark-800/80 p-4">
                    <p className="text-xs uppercase tracking-[0.25em] text-gray-500">Игроков</p>
                    <p className="mt-2 text-3xl font-black text-white">{server.players || 0}</p>
                  </div>
                  <div className="rounded-2xl border border-white/8 bg-dark-800/80 p-4">
                    <p className="text-xs uppercase tracking-[0.25em] text-gray-500">Слотов</p>
                    <p className="mt-2 text-3xl font-black text-white">{server.maxPlayers || '∞'}</p>
                  </div>
                </div>

                <div className="mt-4 flex items-center justify-between rounded-2xl border border-white/8 bg-white/[0.03] px-4 py-3">
                  <span className="font-mono text-sm font-semibold text-orange-300">{server.ip}</span>
                  <button onClick={copyIP} className="text-gray-400 transition-colors hover:text-orange-300">
                    {copied ? <Check size={16} className="text-green-400" /> : <Copy size={16} />}
                  </button>
                </div>

                {server.online && server.playerList?.length > 0 && (
                  <div className="mt-4 flex flex-wrap gap-2">
                    {server.playerList.slice(0, 6).map((player) => (
                      <Link
                        key={player}
                        to={`/profile/${player}`}
                        className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-xs text-gray-300 transition-all hover:border-orange-500/20 hover:text-orange-200"
                      >
                        <img
                          src={`https://mc-heads.net/avatar/${player}/16`}
                          alt={player}
                          className="h-4 w-4 rounded"
                          style={{ imageRendering: 'pixelated' }}
                        />
                        {player}
                      </Link>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="page-container">
        <div className="mb-10 flex items-end justify-between gap-4">
          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-[0.3em] text-orange-300/70">Why It Hits Harder</p>
            <h2 className="section-title">Почему новый стиль работает лучше</h2>
          </div>
        </div>
        <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4 stagger">
          {FEATURES.map(({ icon: Icon, title, desc }) => (
            <div key={title} className="glass animate-slide-up p-6 transition-all duration-300 hover:-translate-y-1 hover:border-orange-500/20">
              <div className="mb-5 inline-flex rounded-2xl border border-orange-500/20 bg-orange-500/10 p-3">
                <Icon size={24} className="text-orange-300" />
              </div>
              <h3 className="text-lg font-bold text-white">{title}</h3>
              <p className="mt-3 text-sm leading-7 text-gray-400">{desc}</p>
            </div>
          ))}
        </div>
      </section>

      {news.length > 0 && (
        <section className="page-container">
          <div className="mb-8 flex items-end justify-between gap-4">
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-[0.3em] text-orange-300/70">Latest Drops</p>
              <h2 className="section-title">Последние новости</h2>
            </div>
            <Link to="/news" className="inline-flex items-center gap-2 text-sm font-semibold text-orange-300 transition-colors hover:text-orange-200">
              Все новости <ArrowRight size={16} />
            </Link>
          </div>
          <div className="grid gap-5 md:grid-cols-3 stagger">
            {news.map((item) => <NewsCard key={item.id} news={item} />)}
          </div>
        </section>
      )}
    </div>
  );
}
