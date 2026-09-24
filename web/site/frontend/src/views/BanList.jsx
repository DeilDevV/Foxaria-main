import { useState, useEffect, useCallback } from 'react';
import api from '../api/client';
import { Search, Shield, AlertTriangle, Clock, Server } from 'lucide-react';
import Loading from '../components/Loading';

export default function BanList() {
  const [bans, setBans] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [search, setSearch] = useState('');
  const [type, setType] = useState('ban');
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({});

  const fetchBans = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, type, limit: 20 };
      if (search) params.search = search;
      const res = await api.get('/bans', { params });
      setBans(res.data.bans);
      setTotal(res.data.total);
      setTotalPages(res.data.totalPages);
    } catch {}
    setLoading(false);
  }, [page, type, search]);

  useEffect(() => {
    api.get('/bans/stats').then(r => setStats(r.data)).catch(() => {});
  }, []);

  useEffect(() => {
    const t = setTimeout(fetchBans, search ? 400 : 0);
    return () => clearTimeout(t);
  }, [fetchBans]);

  useEffect(() => { setPage(1); }, [search, type]);

  return (
    <div className="min-h-screen pt-24 pb-16">
      <div className="page-container">
        {/* Header */}
        <div className="text-center mb-10 animate-slide-up">
          <div className="inline-flex p-4 bg-red-500/10 border border-red-500/20 rounded-2xl mb-4">
            <Shield size={36} className="text-red-400" />
          </div>
          <h1 className="section-title gradient-text">Бан-лист</h1>
          <p className="text-gray-500">Список заблокированных игроков на серверах Foxaria</p>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8 stagger">
          <div className="glass p-5 text-center">
            <p className="text-3xl font-black text-red-400">{stats.activeBans || 0}</p>
            <p className="text-gray-500 text-sm mt-1">Активных банов</p>
          </div>
          <div className="glass p-5 text-center">
            <p className="text-3xl font-black text-orange-400">{stats.activeMutes || 0}</p>
            <p className="text-gray-500 text-sm mt-1">Активных мутов</p>
          </div>
          <div className="glass p-5 text-center">
            <p className="text-3xl font-black text-yellow-400">{stats.recentBans || 0}</p>
            <p className="text-gray-500 text-sm mt-1">За последние 7 дней</p>
          </div>
        </div>

        {/* Filters */}
        <div className="glass p-4 mb-6 flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <Search size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-500" />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Поиск по нику или причине..."
              className="input-field pl-11 py-3"
            />
          </div>
          <div className="flex gap-2">
            {['ban', 'mute'].map(t => (
              <button
                key={t}
                onClick={() => setType(t)}
                className={`px-5 py-3 rounded-xl text-sm font-semibold transition-all ${
                  type === t ? 'bg-red-500/15 text-red-400 border border-red-500/25' : 'text-gray-400 hover:text-white hover:bg-white/5'
                }`}
              >
                {t === 'ban' ? '🔨 Баны' : '🔇 Муты'}
              </button>
            ))}
          </div>
        </div>

        {/* List */}
        {loading ? (
          <Loading size="sm" />
        ) : bans.length === 0 ? (
          <div className="glass p-16 text-center">
            <Shield size={56} className="text-gray-700 mx-auto mb-4" />
            <p className="text-xl font-bold text-gray-400">
              {search ? 'Ничего не найдено' : 'Бан-лист пуст'}
            </p>
          </div>
        ) : (
          <div className="space-y-3 animate-fade-in">
            {bans.map((ban) => (
              <div key={`${ban.uuid}-${ban.bannedAt}-${ban.reason || ''}`} className="glass p-5 hover:border-red-500/20 transition-all duration-200">
                <div className="flex flex-col sm:flex-row gap-4 items-start">
                  {/* Avatar */}
                  <div className="flex items-center gap-3 flex-shrink-0">
                    {ban.avatar ? (
                      <img src={ban.avatar} alt={ban.username} className="w-12 h-12 rounded-xl" style={{ imageRendering: 'pixelated' }} />
                    ) : (
                      <div className="w-12 h-12 rounded-xl bg-dark-700 flex items-center justify-center text-gray-600">
                        <Shield size={20} />
                      </div>
                    )}
                    <div>
                      <p className="font-bold text-white text-lg leading-none">{ban.username}</p>
                      <div className="flex items-center gap-2 mt-1">
                        <span className={`badge ${ban.permanent ? 'badge-red' : 'badge-orange'}`}>
                          {ban.permanent ? '∞ Навсегда' : ban.until ? new Date(ban.until).toLocaleDateString('ru-RU') : '?'}
                        </span>
                        {ban.ipBan && <span className="badge badge-gray">IP-бан</span>}
                      </div>
                    </div>
                  </div>

                  {/* Details */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-start gap-2 mb-2">
                      <AlertTriangle size={14} className="text-red-400 flex-shrink-0 mt-0.5" />
                      <p className="text-gray-300 text-sm font-medium">{ban.reason}</p>
                    </div>
                    <div className="flex flex-wrap gap-4 text-xs text-gray-500">
                      <span className="flex items-center gap-1"><Shield size={11} className="text-gray-600" />{type === 'mute' ? 'Замутил' : 'Забанил'}: <span className="text-gray-400">{ban.bannedBy}</span></span>
                      <span className="flex items-center gap-1"><Clock size={11} className="text-gray-600" />{new Date(ban.bannedAt).toLocaleString('ru-RU')}</span>
                      {ban.server && <span className="flex items-center gap-1"><Server size={11} className="text-gray-600" />{ban.server}</span>}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 mt-8">
            <button onClick={() => setPage(p => Math.max(1, p - 1))} disabled={page === 1} className="btn-outline px-4 py-2 text-sm disabled:opacity-40">← Назад</button>
            <div className="flex gap-1">
              {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                const p = Math.max(1, Math.min(page - 2 + i, totalPages - 4 + i));
                return (
                  <button key={p} onClick={() => setPage(p)} className={`w-10 h-10 rounded-xl text-sm font-semibold transition-all ${page === p ? 'bg-orange-500/15 text-orange-400 border border-orange-500/25' : 'text-gray-500 hover:bg-white/5 hover:text-white'}`}>{p}</button>
                );
              })}
            </div>
            <button onClick={() => setPage(p => Math.min(totalPages, p + 1))} disabled={page === totalPages} className="btn-outline px-4 py-2 text-sm disabled:opacity-40">Вперёд →</button>
          </div>
        )}
        <p className="text-center text-gray-600 text-sm mt-4">Всего: {total} записей</p>
      </div>
    </div>
  );
}
