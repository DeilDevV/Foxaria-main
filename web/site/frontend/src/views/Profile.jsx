import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../api/client';
import toast from 'react-hot-toast';
import Loading from '../components/Loading';
import Modal from '../components/Modal';
import {
  User, Clock, Users, Shield, Home, Star,
  AlertTriangle, Package, CreditCard, Crown,
  Ban, TrendingUp, Map, Coins,
  Flame, Terminal, Trophy, Sword, RefreshCw,
  BookOpen, ChevronDown, Zap, Heart
} from 'lucide-react';

/* ─── helpers ────────────────────────────────────────────────── */

function StatCard({ icon: Icon, label, value, color = 'orange', sub }) {
  const colors = {
    orange: ['bg-orange-500/15', 'text-orange-400'],
    purple: ['bg-violet-500/15', 'text-violet-400'],
    green:  ['bg-green-500/15',  'text-green-400'],
    blue:   ['bg-blue-500/15',   'text-blue-400'],
    red:    ['bg-red-500/15',    'text-red-400'],
    yellow: ['bg-yellow-500/15', 'text-yellow-400'],
  };
  const [bg, txt] = colors[color] || colors.orange;
  return (
    <div className="glass p-4 flex items-start gap-3 hover:border-orange-500/20 transition-all">
      <div className={`p-2 rounded-xl ${bg} flex-shrink-0`}>
        <Icon size={18} className={txt} />
      </div>
      <div className="min-w-0">
        <p className="text-gray-500 text-xs truncate">{label}</p>
        <p className="text-white font-bold text-lg leading-tight">{value ?? '—'}</p>
        {sub && <p className="text-gray-600 text-xs">{sub}</p>}
      </div>
    </div>
  );
}

function TabBtn({ active, onClick, children }) {
  return (
    <button
      onClick={onClick}
      className={`px-4 py-2.5 rounded-xl text-sm font-semibold transition-all whitespace-nowrap ${
        active ? 'bg-orange-500/15 text-orange-400 border border-orange-500/25' : 'text-gray-500 hover:text-white hover:bg-white/5'
      }`}
    >
      {children}
    </button>
  );
}

function RoleBadge({ role }) {
  const map = {
    OWNER:   ['bg-yellow-500/20 text-yellow-400 border-yellow-500/30',  '👑 Владелец'],
    MASTER:  ['bg-orange-500/20 text-orange-400 border-orange-500/30',  '⭐ Мастер'],
    LEADER:  ['bg-red-500/20 text-red-400 border-red-500/30',           '🔥 Лидер'],
    OFFICER: ['bg-blue-500/20 text-blue-400 border-blue-500/30',        '🛡️ Офицер'],
    ADMIN:   ['bg-violet-500/20 text-violet-400 border-violet-500/30',  '⚡ Администратор'],
    MEMBER:  ['bg-gray-500/20 text-gray-400 border-gray-500/30',        'Участник'],
  };
  const [cls, label] = map[role] || map.MEMBER;
  return <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-lg text-xs font-semibold border ${cls}`}>{label}</span>;
}

function ProgressBar({ current, max, color = 'orange', label }) {
  const pct = max > 0 ? Math.min(100, (current / max) * 100) : 0;
  const grad = {
    orange: 'from-orange-500 to-red-500',
    green:  'from-green-500 to-emerald-500',
    red:    'from-red-600 to-red-400',
    yellow: 'from-yellow-500 to-orange-400',
  };
  return (
    <div>
      {label && <div className="flex justify-between text-xs text-gray-500 mb-1"><span>{label}</span><span>{current}/{max}</span></div>}
      <div className="w-full h-2.5 bg-dark-800 rounded-full overflow-hidden">
        <div className={`h-full rounded-full bg-gradient-to-r ${grad[color] || grad.orange} transition-all duration-700`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

/* ─── main ────────────────────────────────────────────────────── */

export default function Profile() {
  const { user } = useAuth();
  const { username } = useParams();
  const targetUser = username || user?.username;

  const [profile, setProfile]   = useState(null);
  const [loading, setLoading]   = useState(true);
  const [tab, setTab]           = useState('overview');
  const [modal, setModal]       = useState(null);
  const [cmdInput, setCmdInput] = useState('');
  const [cmdResult, setCmdResult] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [selectedServer, setSelectedServer] = useState(null);

  function loadProfile() {
    if (!targetUser) return;
    setLoading(true);
    api.get(`/profile/${targetUser}`)
      .then(r => {
        setProfile(r.data);
        if (!selectedServer && r.data.servers?.length > 0) {
          setSelectedServer(r.data.servers[0].id);
        }
      })
      .catch(() => toast.error('Не удалось загрузить профиль'))
      .finally(() => setLoading(false));
  }

  useEffect(() => { loadProfile(); }, [targetUser]);

  async function guildAction(action, data = {}) {
    setSubmitting(true);
    try {
      const res = await api.post(`/guild/${action}`, data);
      toast.success(res.data.message);
      setModal(null);
      loadProfile();
    } catch (err) {
      toast.error(err.response?.data?.error || 'Ошибка');
    } finally {
      setSubmitting(false);
    }
  }

  async function regionAction(action, data = {}) {
    setSubmitting(true);
    try {
      const res = await api.post(`/private/${action}`, data);
      toast.success(res.data.message);
      setModal(null);
    } catch (err) {
      toast.error(err.response?.data?.error || 'Ошибка');
    } finally {
      setSubmitting(false);
    }
  }

  async function execCmd() {
    if (!cmdInput.trim()) return;
    setSubmitting(true);
    try {
      const res = await api.post('/servers/anarchy/command', { command: cmdInput });
      setCmdResult(res.data.response || '(нет ответа)');
      toast.success('Команда выполнена');
    } catch (err) {
      const msg = err.response?.data?.error || 'Ошибка';
      setCmdResult('Ошибка: ' + msg);
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <Loading size="sm" />;
  if (!profile) return (
    <div className="min-h-screen pt-24 flex items-center justify-center">
      <div className="text-center">
        <User size={64} className="text-gray-700 mx-auto mb-4" />
        <p className="text-gray-400 text-xl font-semibold">Игрок не найден</p>
        <Link to="/" className="text-orange-400 hover:text-orange-300 text-sm mt-2 inline-block">← На главную</Link>
      </div>
    </div>
  );

  const g = profile.guild;
  const isOwnProfile = profile.uuid === user?.uuid;
  const serverName = profile.servers?.find(s => s.id === selectedServer)?.name || 'Анархия';

  return (
    <div className="min-h-screen pt-24 pb-16 px-4">
      <div className="max-w-6xl mx-auto">

        {/* ── HEADER ──────────────────────────────────── */}
        <div className="glass p-6 mb-6 animate-slide-up">
          <div className="flex flex-col md:flex-row gap-6 items-start">
            <div className="relative flex-shrink-0">
              <img src={profile.avatar} alt={profile.username} className="w-24 h-24 rounded-2xl" style={{ imageRendering: 'pixelated' }} />
              <div className={`absolute -bottom-1 -right-1 w-4 h-4 rounded-full border-2 border-dark-900 ${profile.banInfo ? 'bg-red-500' : 'bg-green-400'}`} />
            </div>

            <div className="flex-1 min-w-0">
              <div className="flex flex-wrap items-center gap-3 mb-2">
                <h1 className="text-3xl font-black text-white">{profile.username}</h1>
                <span className={`badge ${
                  profile.isAdmin   ? 'bg-red-500/20 text-red-400 border-red-500/30'   :
                  profile.isMod     ? 'bg-blue-500/20 text-blue-400 border-blue-500/30' :
                  profile.isDonate  ? 'badge-orange' : 'badge-gray'
                }`}>
                  {profile.isAdmin ? '👑 ' : profile.isMod ? '🛡️ ' : profile.isDonate ? '💎 ' : ''}{profile.donateLabel}
                </span>
                {profile.banInfo && <span className="badge bg-red-500/20 text-red-400 border-red-500/30"><Ban size={10} className="inline mr-1" />Заблокирован</span>}
              </div>

              <div className="flex flex-wrap gap-4 text-sm text-gray-400">
                <span className="flex items-center gap-1.5"><Clock size={14} className="text-orange-400" />{profile.playtime.formatted}</span>
                {g && <span className="flex items-center gap-1.5"><Users size={14} className="text-violet-400" />{g.name}</span>}
                {profile.loginStreak.current > 0 && (
                  <span className="flex items-center gap-1.5"><Flame size={14} className="text-red-400" />{profile.loginStreak.current}-дн. стрик</span>
                )}
                <span className="flex items-center gap-1.5">
                  <Coins size={14} className="text-yellow-400" />
                  <span className="text-white font-semibold">{parseFloat(profile.economy.balance).toLocaleString('ru-RU', { maximumFractionDigits: 0 })}</span>
                  <span className="text-gray-600">монет</span>
                </span>
                {profile.registeredAt && (
                  <span className="flex items-center gap-1.5 text-gray-600 text-xs">
                    С {new Date(profile.registeredAt).toLocaleDateString('ru-RU')}
                  </span>
                )}
              </div>

              {profile.banInfo && (
                <div className="mt-3 p-3 bg-red-500/10 border border-red-500/25 rounded-xl text-sm">
                  <p className="text-red-400 font-semibold flex items-center gap-2"><AlertTriangle size={14} />Аккаунт заблокирован</p>
                  <p className="text-gray-400 mt-1">Причина: {profile.banInfo.reason}</p>
                  <p className="text-gray-400">До: {profile.banInfo.permanent ? 'Навсегда' : new Date(profile.banInfo.until).toLocaleString('ru-RU')}</p>
                </div>
              )}
            </div>

            <div className="flex-shrink-0 hidden lg:block">
              <img src={profile.skin} alt="" className="h-36 opacity-80" style={{ imageRendering: 'pixelated' }} />
            </div>
          </div>
        </div>

        {/* ── SERVER SELECTOR ──────────────────────────── */}
        {profile.servers?.length > 0 && (
          <div className="flex items-center gap-3 mb-4 px-1">
            <span className="text-gray-500 text-sm">Сервер:</span>
            <div className="flex gap-2 flex-wrap">
              {profile.servers.map(s => (
                <button
                  key={s.id}
                  onClick={() => setSelectedServer(s.id)}
                  className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold transition-all ${
                    selectedServer === s.id
                      ? 'bg-orange-500/20 text-orange-400 border border-orange-500/30'
                      : 'glass text-gray-500 hover:text-white'
                  }`}
                >
                  {s.icon} {s.name}
                </button>
              ))}
            </div>
            <button onClick={loadProfile} className="ml-auto p-2 text-gray-500 hover:text-orange-400 hover:bg-orange-500/10 rounded-lg transition-all">
              <RefreshCw size={16} />
            </button>
          </div>
        )}

        {/* ── TABS ─────────────────────────────────────── */}
        <div className="flex gap-2 overflow-x-auto no-scrollbar mb-6 pb-1">
          <TabBtn active={tab === 'overview'}     onClick={() => setTab('overview')}>📊 Обзор</TabBtn>
          <TabBtn active={tab === 'guild'}        onClick={() => setTab('guild')}>⚔️ Гильдия</TabBtn>
          <TabBtn active={tab === 'regions'}      onClick={() => setTab('regions')}>🏰 Регионы</TabBtn>
          <TabBtn active={tab === 'homes'}        onClick={() => setTab('homes')}>📍 Дома</TabBtn>
          <TabBtn active={tab === 'quests'}       onClick={() => setTab('quests')}>📜 Квесты</TabBtn>
          <TabBtn active={tab === 'punishments'}  onClick={() => setTab('punishments')}>🔨 Наказания</TabBtn>
          <TabBtn active={tab === 'economy'}      onClick={() => setTab('economy')}>💰 Экономика</TabBtn>
          <TabBtn active={tab === 'purchases'}    onClick={() => setTab('purchases')}>🛒 Покупки</TabBtn>
          {user?.isAdmin && isOwnProfile && (
            <TabBtn active={tab === 'console'} onClick={() => setTab('console')}>⚡ Консоль</TabBtn>
          )}
        </div>

        {/* ── TAB: OVERVIEW ────────────────────────────── */}
        {tab === 'overview' && (
          <div className="space-y-6 animate-fade-in">
            {/* Global stats */}
            <div>
              <p className="text-gray-500 text-xs uppercase tracking-widest mb-3 px-1">Общая статистика</p>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4 stagger">
                <StatCard icon={Clock}      label="Наиграно"         value={profile.playtime.formatted}                  color="orange" />
                <StatCard icon={Star}       label="Ранг"             value={profile.donateLabel}                         color="purple" />
                <StatCard icon={Flame}      label="Стрик входов"     value={`${profile.loginStreak.current} дн.`}        color="red"    sub={profile.loginStreak.lastLoginDate ? `Последний: ${profile.loginStreak.lastLoginDate}` : undefined} />
                <StatCard icon={BookOpen}   label="Завершено квестов" value={profile.progression.completedQuestCount}   color="blue"   />
              </div>
            </div>

            {/* Per-server stats */}
            <div>
              <p className="text-gray-500 text-xs uppercase tracking-widest mb-3 px-1">Данные сервера <span className="text-orange-400">{serverName}</span></p>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4 stagger">
                <StatCard icon={Coins}      label="Баланс"           value={`${parseFloat(profile.economy.balance).toLocaleString('ru-RU', { maximumFractionDigits: 0 })} ₽`} color="yellow" />
                {profile.economy.tokens > 0 && (
                  <StatCard icon={Zap}    label="Токены"             value={profile.economy.tokens.toLocaleString()}     color="purple" />
                )}
                <StatCard icon={Home}       label="Домов"            value={profile.homes.length}                        color="blue"   />
                <StatCard icon={Map}        label="Регионов"         value={profile.regions.length}                      color="green"  />
                {g && <StatCard icon={Users} label="Гильдия"         value={g.name}                                      color="purple" sub={`Роль: ${g.myRole}`} />}
                <StatCard icon={CreditCard} label="Покупок (донат)"  value={profile.purchases.length}                    color="green"  />
              </div>
            </div>

            {/* Progression bar for knowledge level */}
            <div className="glass p-5">
              <div className="flex items-center justify-between mb-3">
                <h3 className="font-bold text-white flex items-center gap-2"><TrendingUp size={16} className="text-yellow-400" />Уровень знаний</h3>
                <span className="text-yellow-400 font-bold">Ур. {profile.progression.knowledgeLevel}</span>
              </div>
              <div className="flex items-center gap-3">
                <p className="text-gray-400 text-sm">
                  Активный квест: <span className="text-orange-400 font-mono">{profile.progression.currentQuestId || 'нет'}</span>
                </p>
                {profile.progression.questProgress > 0 && (
                  <span className="text-gray-500 text-xs">Прогресс: {profile.progression.questProgress}</span>
                )}
              </div>
              {profile.progression.completedQuestCount > 0 && (
                <p className="text-gray-500 text-sm mt-1">Завершено квестов: <span className="text-white">{profile.progression.completedQuestCount}</span></p>
              )}
            </div>

            {/* Extra permissions */}
            {profile.extraPerms.length > 0 && (
              <div className="glass p-5">
                <h3 className="text-white font-bold mb-3 flex items-center gap-2"><Shield size={16} className="text-violet-400" />Права</h3>
                <div className="flex flex-wrap gap-2">
                  {profile.extraPerms.slice(0, 30).map(p => (
                    <span key={p} className="badge-purple font-mono text-xs">{p}</span>
                  ))}
                  {profile.extraPerms.length > 30 && <span className="text-gray-600 text-xs">+{profile.extraPerms.length - 30} ещё</span>}
                </div>
              </div>
            )}
          </div>
        )}

        {/* ── TAB: GUILD ───────────────────────────────── */}
        {tab === 'guild' && (
          <div className="animate-fade-in">
            {!g ? (
              <div className="glass p-12 text-center">
                <Users size={64} className="text-gray-700 mx-auto mb-4" />
                <p className="text-xl font-bold text-gray-400">Не состоит в гильдии</p>
                <p className="text-gray-600 mt-2">Создайте или вступите в гильдию командой /guild</p>
              </div>
            ) : (
              <div className="space-y-5">
                {/* Guild header */}
                <div className="glass p-6">
                  <div className="flex items-start justify-between flex-wrap gap-4 mb-5">
                    <div>
                      <div className="flex items-center gap-3 mb-2">
                        <h2 className="text-2xl font-black text-white">{g.name}</h2>
                        {g.isLeader && <Crown size={22} className="text-yellow-400" />}
                        <span className="px-2.5 py-0.5 bg-yellow-500/15 text-yellow-400 border border-yellow-500/25 rounded-lg text-xs font-bold">
                          Ур. {g.tier}
                        </span>
                      </div>
                      {g.motd && <p className="text-gray-400 text-sm mb-3 italic">"{g.motd}"</p>}
                      <div className="flex flex-wrap gap-4 text-sm text-gray-500">
                        <span className="flex items-center gap-1.5"><Users size={14} className="text-orange-400" />{g.members.length} участников</span>
                        <span>Ваша роль: <RoleBadge role={g.myRole} /></span>
                        <span className={`flex items-center gap-1.5 ${g.friendlyFire ? 'text-red-400' : 'text-green-400'}`}>
                          <Sword size={14} />{g.friendlyFire ? 'PvP внутри: Вкл.' : 'PvP внутри: Выкл.'}
                        </span>
                      </div>
                    </div>
                    {isOwnProfile && (
                      <div className="flex gap-2 flex-wrap">
                        {(g.isLeader || g.isOfficer) && (
                          <button onClick={() => setModal('guild_invite')} className="btn-gradient text-sm px-3 py-2">+ Пригласить</button>
                        )}
                        {(g.isLeader || g.isOfficer) && (
                          <button onClick={() => setModal('guild_kick')} className="btn-outline text-sm px-3 py-2">Кикнуть</button>
                        )}
                        {g.isLeader && <button onClick={() => setModal('guild_promote')} className="btn-outline text-sm px-3 py-2">Повысить</button>}
                        {g.isLeader && <button onClick={() => setModal('guild_demote')} className="btn-outline text-sm px-3 py-2">Понизить</button>}
                        {g.myRole !== 'OWNER' && (
                          <button onClick={() => guildAction('leave')} className="px-3 py-2 rounded-xl bg-gray-500/15 text-gray-400 border border-gray-500/25 hover:bg-gray-500/25 text-sm font-semibold transition-all">
                            Покинуть
                          </button>
                        )}
                        {g.myRole === 'OWNER' && (
                          <button onClick={() => setModal('guild_disband')} className="px-3 py-2 rounded-xl bg-red-500/15 text-red-400 border border-red-500/25 hover:bg-red-500/25 text-sm font-semibold transition-all">
                            Распустить
                          </button>
                        )}
                      </div>
                    )}
                  </div>

                  {/* Guild resources */}
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    <div className="text-center p-3 bg-dark-800 rounded-xl">
                      <p className="text-xl font-black text-green-400">{parseFloat(g.bankBalance).toLocaleString('ru-RU', { maximumFractionDigits: 0 })}</p>
                      <p className="text-gray-500 text-xs mt-0.5">Банк гильдии</p>
                    </div>
                    <div className="text-center p-3 bg-dark-800 rounded-xl">
                      <p className="text-xl font-black text-yellow-400">{(g.guildCoins || 0).toLocaleString()}</p>
                      <p className="text-gray-500 text-xs mt-0.5">Монеты гильдии</p>
                    </div>
                    <div className="text-center p-3 bg-dark-800 rounded-xl">
                      <p className="text-xl font-black text-violet-400">{(g.guildPoints || 0).toLocaleString()}</p>
                      <p className="text-gray-500 text-xs mt-0.5">Очки гильдии</p>
                    </div>
                    <div className="text-center p-3 bg-dark-800 rounded-xl">
                      <p className="text-xl font-black text-red-400">{(g.totalKills || 0).toLocaleString()}</p>
                      <p className="text-gray-500 text-xs mt-0.5">Убийств</p>
                    </div>
                  </div>
                </div>

                {/* Members */}
                <div className="glass p-6">
                  <h3 className="font-bold text-white mb-4 flex items-center gap-2"><Users size={16} className="text-orange-400" />Участники ({g.members.length})</h3>
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                    {g.members.map(m => (
                      <Link key={m.uuid} to={`/profile/${m.username}`} className="flex items-center gap-3 p-3 bg-dark-800 rounded-xl hover:bg-dark-700 transition-all group">
                        <img src={m.avatar} alt={m.username} className="w-9 h-9 rounded-lg" style={{ imageRendering: 'pixelated' }} />
                        <div className="min-w-0 flex-1">
                          <p className="text-white text-sm font-semibold truncate group-hover:text-orange-400 transition-colors">{m.username}</p>
                          <div className="mt-0.5"><RoleBadge role={m.role} /></div>
                        </div>
                        {m.role === 'OWNER' && <Crown size={14} className="text-yellow-400 flex-shrink-0" />}
                      </Link>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* ── TAB: REGIONS (Foxaria привать система) ───── */}
        {tab === 'regions' && (
          <div className="animate-fade-in space-y-5">
            {isOwnProfile && (
              <div className="glass p-5">
                <h3 className="font-bold text-white mb-3 flex items-center gap-2"><Map size={16} className="text-orange-400" />Управление регионами</h3>
                <p className="text-gray-500 text-sm mb-4">Регионы создаются в игре с помощью специальных блоков. Здесь можно управлять участниками.</p>
                <div className="flex flex-wrap gap-3">
                  <button onClick={() => setModal('region_add')} className="btn-gradient text-sm px-4 py-2">
                    <span className="flex items-center gap-2">+ Добавить участника</span>
                  </button>
                  <button onClick={() => setModal('region_remove')} className="btn-outline text-sm px-4 py-2">Убрать участника</button>
                </div>
              </div>
            )}

            {profile.regions.length === 0 ? (
              <div className="glass p-12 text-center">
                <Map size={48} className="text-gray-700 mx-auto mb-4" />
                <p className="text-gray-400 font-semibold">Нет регионов</p>
                <p className="text-gray-600 text-sm mt-2">Создайте регион на сервере с помощью специальных блоков</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                {profile.regions.map(r => (
                  <div key={r.id} className="glass p-5">
                    <div className="flex items-center justify-between mb-4">
                      <div>
                        <h3 className="font-bold text-white text-lg">{r.displayName}</h3>
                        <p className="text-gray-500 text-sm">{r.world} · #{r.id}</p>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="px-2.5 py-1 bg-yellow-500/15 text-yellow-400 border border-yellow-500/25 rounded-lg text-xs font-bold">
                          Ур. {r.level}
                        </span>
                      </div>
                    </div>

                    {/* Core HP */}
                    <div className="mb-4">
                      <div className="flex items-center gap-2 mb-2">
                        <Heart size={14} className="text-red-400" />
                        <span className="text-sm text-gray-400">Прочность ядра</span>
                        <span className="ml-auto text-white font-bold text-sm">{r.coreHp}/{r.coreMaxHp}</span>
                      </div>
                      <ProgressBar current={r.coreHp} max={r.coreMaxHp} color="red" />
                    </div>

                    {/* Coordinates */}
                    <div className="grid grid-cols-2 gap-3 mb-4">
                      <div className="bg-dark-800 rounded-xl p-3">
                        <p className="text-gray-500 text-xs mb-1">Центр</p>
                        <p className="text-white font-mono text-sm">X{r.centerX} Z{r.centerZ}</p>
                        <p className="text-gray-500 text-xs">Радиус: {r.halfSize} блоков</p>
                      </div>
                      <div className="bg-dark-800 rounded-xl p-3">
                        <p className="text-gray-500 text-xs mb-1">Площадь</p>
                        <p className="text-white font-bold text-lg">{(r.size).toLocaleString()}</p>
                        <p className="text-gray-500 text-xs">блоков</p>
                      </div>
                    </div>

                    {/* Resources deposited */}
                    {(r.depositedWood > 0 || r.depositedIron > 0) && (
                      <div className="flex gap-3 mb-4">
                        {r.depositedWood > 0 && (
                          <div className="flex-1 bg-dark-800 rounded-xl p-3 text-center">
                            <p className="text-2xl">🪵</p>
                            <p className="text-white font-bold">{r.depositedWood.toLocaleString()}</p>
                            <p className="text-gray-500 text-xs">Дерево</p>
                          </div>
                        )}
                        {r.depositedIron > 0 && (
                          <div className="flex-1 bg-dark-800 rounded-xl p-3 text-center">
                            <p className="text-2xl">⚙️</p>
                            <p className="text-white font-bold">{r.depositedIron.toLocaleString()}</p>
                            <p className="text-gray-500 text-xs">Железо</p>
                          </div>
                        )}
                      </div>
                    )}

                    {/* Members */}
                    {r.members.length > 0 && (
                      <div>
                        <p className="text-gray-500 text-xs mb-2">Участники ({r.members.length})</p>
                        <div className="flex flex-wrap gap-2">
                          {r.members.map((m, i) => (
                            <span key={i} className="flex items-center gap-1.5 px-2.5 py-1 bg-dark-800 rounded-lg text-sm text-white">
                              <img src={`https://mc-heads.net/avatar/${m.username || 'Steve'}/16`} className="w-4 h-4 rounded" style={{ imageRendering: 'pixelated' }} alt="" />
                              {m.username}
                              <span className="text-gray-600 text-xs">({m.role})</span>
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ── TAB: HOMES ───────────────────────────────── */}
        {tab === 'homes' && (
          <div className="animate-fade-in">
            {profile.homes.length === 0 ? (
              <div className="glass p-12 text-center">
                <Home size={48} className="text-gray-700 mx-auto mb-4" />
                <p className="text-gray-400 font-semibold">Нет сохранённых домов</p>
                <p className="text-gray-600 text-sm mt-2">Установите дом командой /sethome &lt;название&gt;</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {profile.homes.map(h => (
                  <div key={h.id} className="glass p-4 hover:border-orange-500/20 transition-all">
                    <div className="flex items-center gap-2 mb-3">
                      <Home size={16} className="text-orange-400" />
                      <span className="font-bold text-white">{h.name}</span>
                    </div>
                    <div className="bg-dark-800 rounded-xl p-3 font-mono text-sm space-y-1">
                      <p className="text-gray-400">Мир: <span className="text-white">{h.world}</span></p>
                      <p className="text-gray-400">X: <span className="text-white">{h.x}</span></p>
                      <p className="text-gray-400">Y: <span className="text-white">{h.y}</span></p>
                      <p className="text-gray-400">Z: <span className="text-white">{h.z}</span></p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ── TAB: QUESTS ──────────────────────────────── */}
        {tab === 'quests' && (
          <div className="animate-fade-in space-y-5">
            <div className="glass p-6">
              <div className="flex items-center justify-between mb-5">
                <h3 className="font-bold text-white text-lg flex items-center gap-2">
                  <BookOpen size={18} className="text-blue-400" />Квестовая система
                </h3>
                <span className="px-3 py-1 bg-yellow-500/15 text-yellow-400 border border-yellow-500/25 rounded-xl text-sm font-bold">
                  Уровень знаний {profile.progression.knowledgeLevel}
                </span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
                <div className="text-center p-4 bg-dark-800 rounded-xl">
                  <p className="text-3xl font-black text-green-400">{profile.progression.completedQuestCount}</p>
                  <p className="text-gray-500 text-sm mt-1">Завершено квестов</p>
                </div>
                <div className="text-center p-4 bg-dark-800 rounded-xl">
                  <p className="text-lg font-bold text-orange-400 font-mono">{profile.progression.currentQuestId || '—'}</p>
                  <p className="text-gray-500 text-sm mt-1">Активный квест</p>
                </div>
                <div className="text-center p-4 bg-dark-800 rounded-xl">
                  <p className="text-3xl font-black text-blue-400">{profile.progression.questProgress}</p>
                  <p className="text-gray-500 text-sm mt-1">Прогресс</p>
                </div>
              </div>

              {profile.progression.currentQuestId && (
                <div className="p-4 border border-orange-500/25 bg-orange-500/5 rounded-xl">
                  <p className="text-orange-400 font-semibold text-sm mb-1">Текущий квест</p>
                  <p className="text-white font-bold font-mono">{profile.progression.currentQuestId}</p>
                  <p className="text-gray-500 text-sm mt-2">Прогресс: {profile.progression.questProgress} / ?</p>
                </div>
              )}
            </div>
          </div>
        )}

        {/* ── TAB: PUNISHMENTS ─────────────────────────── */}
        {tab === 'punishments' && (
          <div className="animate-fade-in">
            {profile.punishments.length === 0 ? (
              <div className="glass p-12 text-center">
                <Shield size={48} className="text-green-400 mx-auto mb-4" />
                <p className="text-gray-400 font-semibold">Нет нарушений</p>
              </div>
            ) : (
              <div className="space-y-3">
                {profile.punishments.map((p, i) => (
                  <div key={i} className={`glass p-4 border ${p.active ? 'border-red-500/25 bg-red-500/5' : 'border-white/5'}`}>
                    <div className="flex items-start justify-between gap-4 flex-wrap">
                      <div>
                        <div className="flex items-center gap-2 mb-1">
                          <span className={`badge ${p.type === 'ban' ? 'bg-red-500/20 text-red-400 border-red-500/30' : 'bg-orange-500/20 text-orange-400 border-orange-500/30'}`}>
                            {p.type === 'ban' ? '🔨 Бан' : '🔇 Мут'}
                          </span>
                          {p.active ? (
                            <span className="badge bg-red-500/20 text-red-400 border-red-500/30 text-xs">Активно</span>
                          ) : (
                            <span className="badge bg-gray-500/20 text-gray-400 border-gray-500/30 text-xs">Истекло</span>
                          )}
                        </div>
                        <p className="text-white font-semibold">{p.reason}</p>
                        <p className="text-gray-500 text-sm mt-1">
                          Кем: <span className="text-gray-400">{p.by}</span>
                          {p.bannedAt && ` · ${new Date(p.bannedAt).toLocaleString('ru-RU')}`}
                        </p>
                      </div>
                      <div className="text-right text-sm text-gray-500">
                        {p.permanent ? 'Навсегда' : p.until ? `До ${new Date(p.until).toLocaleString('ru-RU')}` : '—'}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ── TAB: ECONOMY ─────────────────────────────── */}
        {tab === 'economy' && (
          <div className="animate-fade-in space-y-5">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <div className="glass p-6 text-center">
                <Coins size={32} className="text-yellow-400 mx-auto mb-3" />
                <p className="text-4xl font-black text-white">{parseFloat(profile.economy.balance).toLocaleString('ru-RU', { maximumFractionDigits: 2 })}</p>
                <p className="text-gray-500 mt-1">Монеты</p>
              </div>
              {profile.economy.tokens > 0 && (
                <div className="glass p-6 text-center">
                  <Zap size={32} className="text-violet-400 mx-auto mb-3" />
                  <p className="text-4xl font-black text-white">{profile.economy.tokens.toLocaleString()}</p>
                  <p className="text-gray-500 mt-1">Токены</p>
                </div>
              )}
            </div>

            {/* Auction history */}
            {profile.auctionHistory.length > 0 && (
              <div className="glass p-5">
                <h3 className="font-bold text-white mb-4 flex items-center gap-2"><Trophy size={16} className="text-orange-400" />История аукциона</h3>
                <div className="space-y-2">
                  {profile.auctionHistory.map((a, i) => (
                    <div key={i} className="flex items-center justify-between p-3 bg-dark-800 rounded-xl">
                      <div>
                        <span className={`badge text-xs ${a.status === 'SOLD' ? 'bg-green-500/20 text-green-400 border-green-500/30' : a.status === 'ACTIVE' ? 'badge-orange' : 'badge-gray'}`}>
                          {a.status}
                        </span>
                        <span className="ml-2 text-gray-500 text-xs">{a.isSeller ? 'Продажа' : 'Покупка'}</span>
                        {a.createdAt && <span className="ml-2 text-gray-600 text-xs">{new Date(a.createdAt).toLocaleDateString('ru-RU')}</span>}
                      </div>
                      <span className="text-yellow-400 font-bold">{parseFloat(a.price).toLocaleString()} ₽</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* ── TAB: PURCHASES ───────────────────────────── */}
        {tab === 'purchases' && (
          <div className="animate-fade-in">
            {profile.purchases.length === 0 ? (
              <div className="glass p-12 text-center">
                <Package size={48} className="text-gray-700 mx-auto mb-4" />
                <p className="text-gray-400 font-semibold">Нет покупок</p>
              </div>
            ) : (
              <div className="space-y-3">
                {profile.purchases.map((p, i) => (
                  <div key={i} className="glass p-4 flex items-center justify-between gap-4 flex-wrap">
                    <div className="flex items-center gap-3">
                      <div className="p-2 bg-orange-500/15 rounded-xl"><Package size={18} className="text-orange-400" /></div>
                      <div>
                        <p className="text-white font-semibold">{p.item_name}</p>
                        <p className="text-gray-500 text-sm">{p.payment_method} · {new Date(p.completed_at).toLocaleDateString('ru-RU')}</p>
                      </div>
                    </div>
                    <span className="text-green-400 font-bold">{parseFloat(p.amount).toFixed(2)} ₽</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ── TAB: CONSOLE (admin only) ─────────────────── */}
        {tab === 'console' && user?.isAdmin && (
          <div className="animate-fade-in space-y-5">
            <div className="glass p-6">
              <h3 className="font-bold text-white text-lg mb-4 flex items-center gap-2">
                <Terminal size={18} className="text-orange-400" />RCON Консоль — Анархия
              </h3>
              <div className="flex gap-2 mb-3">
                <input
                  type="text"
                  value={cmdInput}
                  onChange={e => setCmdInput(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && execCmd()}
                  placeholder="Команда сервера... (Enter)"
                  className="input-field font-mono text-sm flex-1"
                />
                <button onClick={execCmd} disabled={submitting || !cmdInput.trim()} className="btn-gradient px-5 disabled:opacity-50">
                  <span>▶</span>
                </button>
              </div>
              <div className="flex flex-wrap gap-2 mb-4">
                {['list', 'tps', 'memory', `teleport ${profile.username} 0 64 0`].map(cmd => (
                  <button key={cmd} onClick={() => setCmdInput(cmd)} className="text-xs px-3 py-1.5 glass rounded-lg text-gray-400 hover:text-orange-400 hover:border-orange-500/25 transition-all">{cmd}</button>
                ))}
              </div>
              {cmdResult && (
                <div className="p-4 bg-dark-950 rounded-xl font-mono text-sm text-green-400 border border-green-500/20 max-h-48 overflow-y-auto whitespace-pre-wrap">
                  {cmdResult}
                </div>
              )}
            </div>
          </div>
        )}

      </div>

      {/* ── MODALS ───────────────────────────────────────── */}
      <Modal isOpen={modal === 'guild_invite'} onClose={() => setModal(null)} title="Пригласить в гильдию">
        <SimpleInput label="Никнейм игрока" onSubmit={v => guildAction('invite', { targetUsername: v })} loading={submitting} btnText="Пригласить" />
      </Modal>
      <Modal isOpen={modal === 'guild_kick'} onClose={() => setModal(null)} title="Кикнуть из гильдии">
        <SimpleInput label="Никнейм" onSubmit={v => guildAction('kick', { targetUsername: v })} loading={submitting} btnText="Кикнуть" btnRed />
      </Modal>
      <Modal isOpen={modal === 'guild_promote'} onClose={() => setModal(null)} title="Повысить участника">
        <SimpleInput label="Никнейм" onSubmit={v => guildAction('promote', { targetUsername: v })} loading={submitting} btnText="Повысить" />
      </Modal>
      <Modal isOpen={modal === 'guild_demote'} onClose={() => setModal(null)} title="Понизить участника">
        <SimpleInput label="Никнейм" onSubmit={v => guildAction('demote', { targetUsername: v })} loading={submitting} btnText="Понизить" />
      </Modal>
      <Modal isOpen={modal === 'guild_disband'} onClose={() => setModal(null)} title="Распустить гильдию">
        <div className="space-y-4">
          <p className="text-gray-400 text-sm">Вы уверены? Гильдия <strong className="text-white">{g?.name}</strong> будет удалена навсегда.</p>
          <div className="flex gap-3">
            <button onClick={() => guildAction('disband', {})} disabled={submitting} className="flex-1 py-2.5 rounded-xl bg-red-500/15 text-red-400 border border-red-500/25 hover:bg-red-500/25 font-semibold text-sm transition-all disabled:opacity-50">
              {submitting ? '...' : 'Да, распустить'}
            </button>
            <button onClick={() => setModal(null)} className="flex-1 btn-outline text-sm py-2.5">Отмена</button>
          </div>
        </div>
      </Modal>
      <Modal isOpen={modal === 'region_add'} onClose={() => setModal(null)} title="Добавить участника в регион">
        <RegionMemberForm onSubmit={(u, r) => regionAction('add-member', { targetUsername: u, regionName: r })} loading={submitting} regions={profile.regions} action="add" />
      </Modal>
      <Modal isOpen={modal === 'region_remove'} onClose={() => setModal(null)} title="Убрать участника из региона">
        <RegionMemberForm onSubmit={(u, r) => regionAction('remove-member', { targetUsername: u, regionName: r })} loading={submitting} regions={profile.regions} action="remove" />
      </Modal>
    </div>
  );
}

/* ─── sub-components ─────────────────────────────────────────── */

function SimpleInput({ label, onSubmit, loading, btnText, btnRed }) {
  const [value, setValue] = useState('');
  return (
    <div className="space-y-4">
      <div>
        <label className="text-sm text-gray-400 mb-2 block">{label}</label>
        <input value={value} onChange={e => setValue(e.target.value)} className="input-field" placeholder="Никнейм..." onKeyDown={e => e.key === 'Enter' && value && onSubmit(value)} />
      </div>
      <button onClick={() => onSubmit(value)} disabled={loading || !value}
        className={`w-full py-2.5 rounded-xl font-semibold text-sm transition-all disabled:opacity-50 ${btnRed ? 'bg-red-500/15 text-red-400 border border-red-500/25 hover:bg-red-500/25' : 'btn-gradient'}`}>
        <span>{loading ? 'Выполняется...' : btnText}</span>
      </button>
    </div>
  );
}

function RegionMemberForm({ onSubmit, loading, regions, action }) {
  const [username, setUsername] = useState('');
  const [regionName, setRegionName] = useState(regions[0]?.displayName || '');
  return (
    <div className="space-y-4">
      <div>
        <label className="text-sm text-gray-400 mb-2 block">Никнейм</label>
        <input value={username} onChange={e => setUsername(e.target.value)} className="input-field" placeholder="Никнейм игрока..." />
      </div>
      {regions.length > 1 && (
        <div>
          <label className="text-sm text-gray-400 mb-2 block">Регион</label>
          <select value={regionName} onChange={e => setRegionName(e.target.value)} className="input-field">
            {regions.map(r => <option key={r.id} value={r.displayName}>{r.displayName}</option>)}
          </select>
        </div>
      )}
      <button onClick={() => onSubmit(username, regionName)} disabled={loading || !username}
        className={`w-full py-2.5 rounded-xl font-semibold text-sm transition-all disabled:opacity-50 ${action === 'remove' ? 'bg-red-500/15 text-red-400 border border-red-500/25 hover:bg-red-500/25' : 'btn-gradient'}`}>
        <span>{loading ? 'Выполняется...' : action === 'add' ? 'Добавить' : 'Убрать'}</span>
      </button>
    </div>
  );
}
