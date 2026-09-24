import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../api/client';
import toast from 'react-hot-toast';
import Modal from '../components/Modal';
import {
  Settings, Users, Shield, Newspaper, ShoppingBag, Terminal, CreditCard,
  Calendar, BarChart2, Send, Plus, Trash2, Edit2, RefreshCw, Star, AlertCircle
} from 'lucide-react';

function AdminStat({ label, value, icon: Icon, color = 'orange' }) {
  const colors = {
    orange: 'text-orange-400 bg-orange-500/15',
    purple: 'text-violet-400 bg-violet-500/15',
    green: 'text-green-400 bg-green-500/15',
    red: 'text-red-400 bg-red-500/15',
    yellow: 'text-yellow-400 bg-yellow-500/15',
  };
  return (
    <div className="glass p-5">
      <div className="flex items-center gap-3 mb-3">
        <div className={`p-2 rounded-xl ${colors[color]}`}><Icon size={18} className={colors[color].split(' ')[0]} /></div>
        <p className="text-gray-400 text-sm">{label}</p>
      </div>
      <p className="text-3xl font-black text-white">{value ?? '—'}</p>
    </div>
  );
}

const TABS = [
  { id: 'overview', label: '📊 Обзор' },
  { id: 'news', label: '📰 Новости' },
  { id: 'shop', label: '🛒 Магазин' },
  { id: 'players', label: '👥 Игроки' },
  { id: 'payments', label: '💳 Платежи' },
  { id: 'wipes', label: '💥 Вайпы' },
  { id: 'staff', label: '🛡️ Состав' },
  { id: 'console', label: '⚡ Консоль' },
  { id: 'settings', label: '⚙️ Оплата' },
];

const EMPTY_SHOP_FORM = { category_id: '', name: '', description: '', image: '', price: '', original_price: '', commands: '', server_id: 'anarchy', item_type: 'donate', featured: false, visible: true };

// Вынесен ЗА пределы Admin чтобы не пересоздаваться при каждом рендере (иначе input теряет фокус)
function InputField({ label, value, onChange, placeholder, type = 'text', as }) {
  return (
    <div>
      <label className="text-sm font-medium text-gray-400 mb-1.5 block">{label}</label>
      {as === 'textarea' ? (
        <textarea value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder}
          rows={4} className="input-field resize-none" />
      ) : (
        <input type={type} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder}
          className="input-field" />
      )}
    </div>
  );
}

export default function Admin() {
  const { user } = useAuth();
  const [tab, setTab] = useState('overview');
  const [stats, setStats] = useState({});
  const [news, setNews] = useState([]);
  const [shopItems, setShopItems] = useState([]);
  const [shopCats, setShopCats] = useState([]);
  const [payments, setPayments] = useState([]);
  const [wipes, setWipes] = useState([]);
  const [staffList, setStaffList] = useState([]);
  const [cmdInput, setCmdInput] = useState('');
  const [cmdResult, setCmdResult] = useState('');
  const [selectedServer, setSelectedServer] = useState('anarchy');
  const [loading, setLoading] = useState(false);
  const [pendingCmdsCount, setPendingCmdsCount] = useState(0);

  // Forms
  const [newsForm, setNewsForm] = useState({ title: '', content: '', preview: '', image: '', server: 'all', tags: '', pinned: false });
  const [editingNews, setEditingNews] = useState(null);
  const [wipeForm, setWipeForm] = useState({ server_id: '', server_name: '', wipe_date: '', description: '', wipe_type: 'full', kick_now: false });
  const [staffForm, setStaffForm] = useState({ name: '', role: '', role_color: '#FF6B35', avatar: '', discord: '', vk: '', telegram: '', description: '' });

  // Shop
  const [shopForm, setShopForm] = useState(EMPTY_SHOP_FORM);
  const [shopCatForm, setShopCatForm] = useState({ name: '', icon: '🎁' });
  const [editingItem, setEditingItem] = useState(null);
  const [shopItemModal, setShopItemModal] = useState(false);

  // Player actions
  const [playerNick, setPlayerNick] = useState('');
  const [playerAction, setPlayerAction] = useState('kick');
  const [playerReason, setPlayerReason] = useState('');

  // Broadcast
  const [broadcastMsg, setBroadcastMsg] = useState('');

  useEffect(() => { loadStats(); loadPendingCount(); }, []);

  useEffect(() => {
    if (tab === 'news') loadNews();
    if (tab === 'shop') { loadShopItems(); loadShopCats(); }
    if (tab === 'payments') { loadPayments(); loadPendingCount(); }
    if (tab === 'wipes') loadWipes();
    if (tab === 'staff') loadStaff();
  }, [tab]);

  async function loadStats() { const r = await api.get('/admin/stats').catch(() => ({ data: {} })); setStats(r.data); }
  async function loadNews() { const r = await api.get('/news?limit=50').catch(() => ({ data: { news: [] } })); setNews(r.data.news || []); }
  async function loadShopItems() { const r = await api.get('/shop/items').catch(() => ({ data: [] })); setShopItems(r.data); }
  async function loadShopCats() { const r = await api.get('/shop/categories').catch(() => ({ data: [] })); setShopCats(r.data); }
  async function loadPayments() { const r = await api.get('/admin/payments').catch(() => ({ data: { payments: [] } })); setPayments(r.data.payments || []); }
  async function loadWipes() { const r = await api.get('/wipes').catch(() => ({ data: [] })); setWipes(r.data); }
  async function loadStaff() { const r = await api.get('/staff').catch(() => ({ data: [] })); setStaffList(r.data); }
  async function loadPendingCount() { const r = await api.get('/payments/pending-count').catch(() => ({ data: { count: 0 } })); setPendingCmdsCount(r.data.count || 0); }

  async function saveNews() {
    try {
      if (editingNews) {
        await api.put(`/news/${editingNews.id}`, { ...newsForm, published: 1 });
        toast.success('Новость обновлена');
      } else {
        await api.post('/news', newsForm);
        toast.success('Новость создана');
      }
      setNewsForm({ title: '', content: '', preview: '', image: '', server: 'all', tags: '', pinned: false });
      setEditingNews(null);
      loadNews();
    } catch (err) { toast.error(err.response?.data?.error || 'Ошибка'); }
  }

  async function deleteNews(id) {
    if (!confirm('Удалить новость?')) return;
    await api.delete(`/news/${id}`).catch(() => {});
    toast.success('Удалено');
    loadNews();
  }

  // Shop
  async function saveShopCat() {
    if (!shopCatForm.name) { toast.error('Введите название'); return; }
    try {
      await api.post('/shop/categories', shopCatForm);
      toast.success('Категория добавлена');
      setShopCatForm({ name: '', icon: '🎁' });
      loadShopCats();
    } catch (err) { toast.error(err.response?.data?.error || 'Ошибка'); }
  }

  async function deleteShopCat(id) {
    if (!confirm('Удалить категорию?')) return;
    await api.delete(`/shop/categories/${id}`).catch(() => {});
    toast.success('Удалено');
    loadShopCats();
  }

  function openEditItem(item) {
    let cmds = '';
    try { cmds = (JSON.parse(item.commands) || []).join('\n'); } catch {}
    setEditingItem(item);
    setShopForm({
      category_id: item.category_id || '',
      name: item.name,
      description: item.description || '',
      image: item.image || '',
      price: item.price,
      original_price: item.original_price || '',
      commands: cmds,
      server_id: item.server_id || 'anarchy',
      item_type: item.item_type || 'donate',
      featured: !!item.featured,
      visible: item.visible !== 0,
    });
    setShopItemModal(true);
  }

  async function saveShopItem() {
    if (!shopForm.name || !shopForm.price) { toast.error('Название и цена обязательны'); return; }
    try {
      if (editingItem) {
        await api.put(`/shop/items/${editingItem.id}`, shopForm);
        toast.success('Товар обновлён');
      } else {
        await api.post('/shop/items', shopForm);
        toast.success('Товар добавлен');
      }
      setShopItemModal(false);
      setEditingItem(null);
      setShopForm(EMPTY_SHOP_FORM);
      loadShopItems();
    } catch (err) { toast.error(err.response?.data?.error || 'Ошибка'); }
  }

  async function deleteShopItem(id) {
    if (!confirm('Удалить товар?')) return;
    await api.delete(`/shop/items/${id}`).catch(() => {});
    toast.success('Удалено');
    loadShopItems();
  }

  async function retryPending() {
    setLoading(true);
    try {
      const r = await api.post('/payments/retry-pending');
      toast.success(`Выполнено. Осталось в очереди: ${r.data.remaining}`);
      loadPendingCount();
    } catch { toast.error('Ошибка'); }
    finally { setLoading(false); }
  }

  async function saveWipe() {
    try {
      await api.post('/wipes', wipeForm);
      toast.success(wipeForm.kick_now ? 'Вайп добавлен, все игроки кикнуты' : 'Вайп добавлен');
      setWipeForm({ server_id: '', server_name: '', wipe_date: '', description: '', wipe_type: 'full', kick_now: false });
      loadWipes();
    } catch (err) { toast.error(err.response?.data?.error || 'Ошибка'); }
  }

  async function deleteWipe(id) { await api.delete(`/wipes/${id}`).catch(() => {}); loadWipes(); }

  async function saveStaff() {
    try {
      await api.post('/staff', staffForm);
      toast.success('Добавлено');
      setStaffForm({ name: '', role: '', role_color: '#FF6B35', avatar: '', discord: '', vk: '', telegram: '', description: '' });
      loadStaff();
    } catch (err) { toast.error(err.response?.data?.error || 'Ошибка'); }
  }

  async function removeStaff(id) { await api.delete(`/staff/${id}`).catch(() => {}); loadStaff(); }

  async function playerActionSubmit() {
    if (!playerNick) { toast.error('Введите ник'); return; }
    setLoading(true);
    try {
      const endpoints = {
        kick: '/admin/player/kick', mute: '/admin/player/mute', unmute: '/admin/player/unmute',
        ban: '/admin/player/ban', unban: '/admin/player/unban', warn: '/admin/player/warn',
        'give-rank': '/admin/player/give-rank',
        'give-money': '/admin/player/give-money',
        'give-tokens': '/admin/player/give-tokens',
      };
      const body =
        playerAction === 'give-rank' ? { username: playerNick, rank: playerReason } :
        playerAction === 'give-money' ? { username: playerNick, amount: playerReason } :
        playerAction === 'give-tokens' ? { username: playerNick, amount: playerReason } :
        ['unban', 'unmute'].includes(playerAction) ? { username: playerNick } :
        { username: playerNick, reason: playerReason };
      const res = await api.post(endpoints[playerAction], body);
      toast.success(res.data.message || 'Выполнено');
    } catch (err) { toast.error(err.response?.data?.error || 'Ошибка'); }
    finally { setLoading(false); }
  }

  async function sendBroadcast() {
    if (!broadcastMsg) { toast.error('Введите сообщение'); return; }
    try {
      await api.post('/admin/broadcast', { message: broadcastMsg });
      toast.success('Отправлено');
      setBroadcastMsg('');
    } catch (err) { toast.error(err.response?.data?.error || 'Ошибка'); }
  }

  async function execCmd() {
    if (!cmdInput.trim()) return;
    setLoading(true);
    try {
      const endpoint = selectedServer === 'bungeecord'
        ? '/servers/bungeecord/command'
        : `/servers/${selectedServer}/command`;
      const res = await api.post(endpoint, { command: cmdInput });
      setCmdResult(res.data.response || 'Выполнено (нет ответа)');
      toast.success('Команда выполнена');
    } catch (err) {
      const msg = err.response?.data?.error || 'Ошибка';
      setCmdResult('Ошибка: ' + msg);
      toast.error(msg);
    } finally { setLoading(false); }
  }

  return (
    <div className="min-h-screen pt-24 pb-16">
      <div className="max-w-7xl mx-auto px-4">
        <div className="flex items-center gap-4 mb-8 animate-slide-up">
          <div className="p-3 bg-gradient-main rounded-2xl">
            <Settings size={28} className="text-white" />
          </div>
          <div>
            <h1 className="text-3xl font-black gradient-text">Панель управления</h1>
            <p className="text-gray-500">Добро пожаловать, {user?.username}</p>
          </div>
          {pendingCmdsCount > 0 && (
            <div className="ml-auto flex items-center gap-2 px-4 py-2 bg-yellow-500/15 border border-yellow-500/30 rounded-xl">
              <AlertCircle size={16} className="text-yellow-400" />
              <span className="text-yellow-400 text-sm font-semibold">В очереди: {pendingCmdsCount} команд</span>
            </div>
          )}
        </div>

        <div className="flex gap-6">
          {/* Sidebar */}
          <div className="w-52 flex-shrink-0 hidden lg:block">
            <div className="glass p-2 sticky top-24 space-y-1">
              {TABS.map(t => (
                <button key={t.id} onClick={() => setTab(t.id)}
                  className={`nav-item w-full text-left text-sm ${tab === t.id ? 'active' : ''}`}>
                  {t.label}
                </button>
              ))}
            </div>
          </div>

          {/* Mobile tabs */}
          <div className="lg:hidden w-full mb-4 flex gap-2 overflow-x-auto no-scrollbar">
            {TABS.map(t => (
              <button key={t.id} onClick={() => setTab(t.id)}
                className={`flex-shrink-0 px-4 py-2 rounded-xl text-sm font-semibold transition-all ${tab === t.id ? 'bg-orange-500/15 text-orange-400 border border-orange-500/25' : 'text-gray-400 hover:bg-white/5'}`}>
                {t.label}
              </button>
            ))}
          </div>

          <div className="flex-1 min-w-0 space-y-6 animate-fade-in">

            {/* OVERVIEW */}
            {tab === 'overview' && (
              <div className="space-y-6">
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 stagger">
                  <AdminStat label="Игроков" value={stats.totalPlayers?.toLocaleString()} icon={Users} color="orange" />
                  <AdminStat label="Активных банов" value={stats.activeBans} icon={Shield} color="red" />
                  <AdminStat label="Доход всего" value={`${stats.totalRevenue} ₽`} icon={CreditCard} color="green" />
                  <AdminStat label="Доход за месяц" value={`${stats.monthRevenue} ₽`} icon={CreditCard} color="purple" />
                </div>
                {pendingCmdsCount > 0 && (
                  <div className="glass p-5 border border-yellow-500/25">
                    <div className="flex items-center justify-between">
                      <div>
                        <h3 className="font-bold text-white flex items-center gap-2">
                          <AlertCircle size={16} className="text-yellow-400" />Очередь команд
                        </h3>
                        <p className="text-gray-500 text-sm mt-1">{pendingCmdsCount} команд ждут выдачи (сервер был офлайн). Автоматически выдадутся каждые 30 сек.</p>
                      </div>
                      <button onClick={retryPending} disabled={loading} className="btn-gradient text-sm px-5 py-2.5 flex-shrink-0">
                        <span>Выдать сейчас</span>
                      </button>
                    </div>
                  </div>
                )}
                <div className="glass p-6">
                  <h3 className="font-bold text-white mb-4 flex items-center gap-2"><RefreshCw size={16} className="text-orange-400" />Статистика</h3>
                  <button onClick={loadStats} className="btn-gradient text-sm px-5 py-2.5"><span>Обновить</span></button>
                </div>
              </div>
            )}

            {/* NEWS */}
            {tab === 'news' && (
              <div className="space-y-5">
                <div className="glass p-6">
                  <h3 className="font-bold text-white mb-5 flex items-center gap-2">
                    <Plus size={16} className="text-orange-400" />{editingNews ? 'Редактировать' : 'Добавить новость'}
                  </h3>
                  <div className="space-y-4">
                    <InputField label="Заголовок" value={newsForm.title} onChange={v => setNewsForm(p => ({ ...p, title: v }))} placeholder="Заголовок..." />
                    <InputField label="Текст" value={newsForm.content} onChange={v => setNewsForm(p => ({ ...p, content: v }))} placeholder="Полный текст..." as="textarea" />
                    <InputField label="Краткое описание" value={newsForm.preview} onChange={v => setNewsForm(p => ({ ...p, preview: v }))} placeholder="Превью..." />
                    <InputField label="URL изображения" value={newsForm.image} onChange={v => setNewsForm(p => ({ ...p, image: v }))} placeholder="https://..." />
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <label className="text-sm font-medium text-gray-400 mb-1.5 block">Сервер</label>
                        <select value={newsForm.server} onChange={e => setNewsForm(p => ({ ...p, server: e.target.value }))} className="input-field">
                          <option value="all">Все серверы</option>
                          <option value="anarchy">Анархия</option>
                        </select>
                      </div>
                      <InputField label="Теги" value={newsForm.tags} onChange={v => setNewsForm(p => ({ ...p, tags: v }))} placeholder="обновление, ивент" />
                    </div>
                    <label className="flex items-center gap-3 cursor-pointer">
                      <input type="checkbox" checked={newsForm.pinned} onChange={e => setNewsForm(p => ({ ...p, pinned: e.target.checked }))} className="w-4 h-4 rounded accent-orange-500" />
                      <span className="text-sm text-gray-400">Закрепить новость</span>
                    </label>
                    <div className="flex gap-3">
                      <button onClick={saveNews} className="btn-gradient px-6 py-2.5 text-sm"><span>{editingNews ? 'Сохранить' : 'Опубликовать'}</span></button>
                      {editingNews && <button onClick={() => { setEditingNews(null); setNewsForm({ title: '', content: '', preview: '', image: '', server: 'all', tags: '', pinned: false }); }} className="btn-outline px-5 py-2.5 text-sm">Отмена</button>}
                    </div>
                  </div>
                </div>
                <div className="space-y-3">
                  {news.map(n => (
                    <div key={n.id} className="glass p-4 flex items-center gap-4">
                      <div className="flex-1 min-w-0">
                        <p className="text-white font-semibold truncate">{n.title}</p>
                        <p className="text-gray-500 text-xs">{new Date(n.created_at).toLocaleString('ru-RU')} · {n.views} просмотров</p>
                      </div>
                      <div className="flex gap-2">
                        <button onClick={() => { setEditingNews(n); setNewsForm({ title: n.title, content: n.content || '', preview: n.preview || '', image: n.image || '', server: n.server, tags: n.tags || '', pinned: !!n.pinned }); }} className="p-2 text-gray-400 hover:text-orange-400 hover:bg-orange-500/10 rounded-lg transition-all"><Edit2 size={16} /></button>
                        <button onClick={() => deleteNews(n.id)} className="p-2 text-gray-400 hover:text-red-400 hover:bg-red-500/10 rounded-lg transition-all"><Trash2 size={16} /></button>
                      </div>
                    </div>
                  ))}
                  {news.length === 0 && <div className="glass p-8 text-center text-gray-500">Нет новостей</div>}
                </div>
              </div>
            )}

            {/* SHOP */}
            {tab === 'shop' && (
              <div className="space-y-6">
                {/* Categories */}
                <div className="glass p-6">
                  <h3 className="font-bold text-white mb-4 flex items-center gap-2">
                    <Plus size={16} className="text-orange-400" />Категории
                  </h3>
                  <div className="flex gap-3 mb-4">
                    <input value={shopCatForm.name} onChange={e => setShopCatForm(p => ({ ...p, name: e.target.value }))}
                      placeholder="Название категории..." className="input-field flex-1" />
                    <input value={shopCatForm.icon} onChange={e => setShopCatForm(p => ({ ...p, icon: e.target.value }))}
                      placeholder="🎁" className="input-field w-16 text-center text-2xl" />
                    <button onClick={saveShopCat} className="btn-gradient px-5 py-2.5 text-sm flex-shrink-0"><span>Добавить</span></button>
                  </div>
                  <div className="space-y-2">
                    {shopCats.map(c => (
                      <div key={c.id} className="flex items-center gap-3 p-3 bg-dark-800 rounded-xl">
                        <span className="text-2xl">{c.icon}</span>
                        <span className="text-white font-medium flex-1">{c.name}</span>
                        <span className="text-gray-500 text-xs">{shopItems.filter(i => i.category_id === c.id).length} товаров</span>
                        <button onClick={() => deleteShopCat(c.id)} className="p-1.5 text-gray-500 hover:text-red-400 hover:bg-red-500/10 rounded-lg transition-all"><Trash2 size={14} /></button>
                      </div>
                    ))}
                    {shopCats.length === 0 && <p className="text-gray-500 text-sm text-center py-2">Нет категорий</p>}
                  </div>
                </div>

                {/* Items */}
                <div className="glass p-6">
                  <div className="flex items-center justify-between mb-4">
                    <h3 className="font-bold text-white flex items-center gap-2">
                      <ShoppingBag size={16} className="text-orange-400" />Товары ({shopItems.length})
                    </h3>
                    <button
                      onClick={() => { setEditingItem(null); setShopForm(EMPTY_SHOP_FORM); setShopItemModal(true); }}
                      className="btn-gradient px-4 py-2 text-sm">
                      <span className="flex items-center gap-2"><Plus size={14} />Добавить</span>
                    </button>
                  </div>
                  <div className="space-y-2">
                    {shopItems.map(item => (
                      <div key={item.id} className="flex items-center gap-3 p-3 bg-dark-800 rounded-xl">
                        <div className="w-10 h-10 rounded-lg overflow-hidden bg-dark-900 flex-shrink-0 flex items-center justify-center text-xl">
                          {item.image
                            ? <img src={item.image} alt="" className="w-full h-full object-cover" />
                            : item.item_type === 'currency' ? '💰' : item.item_type === 'case' ? '📦' : '👑'}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <p className="text-white font-medium truncate">{item.name}</p>
                            {item.featured ? <Star size={12} className="text-yellow-400 flex-shrink-0" fill="currentColor" /> : null}
                            {!item.visible ? <span className="text-xs text-gray-600">[скрыт]</span> : null}
                          </div>
                          <p className="text-gray-500 text-xs">{item.category_name || 'Без категории'} · {item.item_type} · {item.server_id}</p>
                        </div>
                        <span className="text-orange-400 font-bold text-sm flex-shrink-0">{parseFloat(item.price).toFixed(0)} ₽</span>
                        <div className="flex gap-1">
                          <button onClick={() => openEditItem(item)} className="p-1.5 text-gray-500 hover:text-orange-400 hover:bg-orange-500/10 rounded-lg transition-all"><Edit2 size={14} /></button>
                          <button onClick={() => deleteShopItem(item.id)} className="p-1.5 text-gray-500 hover:text-red-400 hover:bg-red-500/10 rounded-lg transition-all"><Trash2 size={14} /></button>
                        </div>
                      </div>
                    ))}
                    {shopItems.length === 0 && <p className="text-gray-500 text-sm text-center py-6">Нет товаров. Добавьте первый!</p>}
                  </div>
                </div>

                {/* Command help */}
                <div className="glass p-5 border border-orange-500/15 text-sm text-gray-400">
                  <p className="font-semibold text-white mb-2">Переменные в командах:</p>
                  <p><code className="text-orange-400">{'{player}'}</code> — ник покупателя</p>
                  <p><code className="text-orange-400">{'{amount}'}</code> — сумма платежа</p>
                  <p><code className="text-orange-400">{'{item}'}</code> — название товара</p>
                  <p className="mt-2 text-gray-500">Каждая команда — отдельная строка. Ранг через прокси Foxaria (как в игре):</p>
                  <code className="block mt-1 text-green-400 text-xs">grantpriv {'{player}'} vip</code>
                  <p className="mt-2 text-gray-500">Пример для токенов на анархии:</p>
                  <code className="block mt-1 text-green-400 text-xs">eco token give {'{player}'} 1000</code>
                  <p className="mt-3 text-yellow-400 text-xs">⏳ Если сервер офлайн при покупке — команды встанут в очередь и выдадутся автоматически при следующем старте сервера.</p>
                </div>
              </div>
            )}

            {/* PLAYERS */}
            {tab === 'players' && (
              <div className="space-y-5">
                <div className="glass p-6">
                  <h3 className="font-bold text-white mb-5">Управление игроками</h3>
                  <div className="space-y-4">
                    <div className="grid grid-cols-2 gap-4">
                      <InputField label="Никнейм игрока" value={playerNick} onChange={setPlayerNick} placeholder="Никнейм..." />
                      <div>
                        <label className="text-sm font-medium text-gray-400 mb-1.5 block">Действие</label>
                        <select value={playerAction} onChange={e => setPlayerAction(e.target.value)} className="input-field">
                          <option value="kick">Кикнуть (BungeeCord)</option>
                          <option value="ban">Забанить</option>
                          <option value="unban">Разбанить</option>
                          <option value="mute">Замутить</option>
                          <option value="unmute">Размутить</option>
                          <option value="warn">Предупредить</option>
                          <option value="give-rank">Выдать ранг</option>
                          <option value="give-money">Выдать монеты</option>
                          <option value="give-tokens">Выдать токены</option>
                        </select>
                      </div>
                    </div>
                    {!['unban', 'unmute'].includes(playerAction) && (
                      <InputField
                        label={
                          playerAction === 'give-rank' ? 'Ранг (группа)' :
                          playerAction === 'give-money' ? 'Сумма монет' :
                          playerAction === 'give-tokens' ? 'Количество токенов' :
                          playerAction === 'ban' ? 'Код (1.1=читы, 1.3=перм)' :
                          playerAction === 'mute' ? 'Код (2.1=оскорбл, 2.3=спам)' :
                          playerAction === 'warn' ? 'Код (3.x)' : 'Причина'
                        }
                        value={playerReason}
                        onChange={setPlayerReason}
                        placeholder={
                          playerAction === 'give-rank' ? 'vip, premium...' :
                          playerAction === 'give-money' ? '1000' :
                          playerAction === 'give-tokens' ? '100' :
                          playerAction === 'ban' ? '1.1' :
                          playerAction === 'mute' ? '2.1' :
                          playerAction === 'warn' ? '3.1' : 'Причина...'
                        }
                      />
                    )}
                    <button onClick={playerActionSubmit} disabled={loading} className="btn-gradient px-6 py-2.5 text-sm disabled:opacity-50">
                      <span>{loading ? 'Выполняется...' : 'Выполнить'}</span>
                    </button>
                  </div>
                </div>
                <div className="glass p-6">
                  <h3 className="font-bold text-white mb-4 flex items-center gap-2"><Send size={16} className="text-orange-400" />Оповещение</h3>
                  <div className="flex gap-3">
                    <input value={broadcastMsg} onChange={e => setBroadcastMsg(e.target.value)} placeholder="Текст оповещения..." className="input-field flex-1" />
                    <button onClick={sendBroadcast} className="btn-gradient px-5 py-3 flex-shrink-0"><span>Отправить</span></button>
                  </div>
                </div>
              </div>
            )}

            {/* PAYMENTS */}
            {tab === 'payments' && (
              <div className="space-y-4">
                {pendingCmdsCount > 0 && (
                  <div className="glass p-4 border border-yellow-500/25 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <AlertCircle size={18} className="text-yellow-400 flex-shrink-0" />
                      <div>
                        <p className="text-white font-semibold">Очередь выдачи: {pendingCmdsCount} команд</p>
                        <p className="text-gray-500 text-xs">Сервер был офлайн при обработке платежей. Retry каждые 30 сек.</p>
                      </div>
                    </div>
                    <button onClick={retryPending} disabled={loading} className="btn-gradient text-sm px-4 py-2 flex-shrink-0">
                      <span>Выдать сейчас</span>
                    </button>
                  </div>
                )}
                {payments.length === 0 ? (
                  <div className="glass p-12 text-center text-gray-500">Нет платежей</div>
                ) : payments.map(p => (
                  <div key={p.id} className="glass p-4 flex items-center gap-4 flex-wrap">
                    <div className="flex-1 min-w-0">
                      <p className="text-white font-semibold">{p.player_name} — {p.item_name}</p>
                      <p className="text-gray-500 text-xs">{p.payment_method} · {new Date(p.created_at).toLocaleString('ru-RU')}</p>
                    </div>
                    <div className="flex items-center gap-2">
                      {p.commands_executed === 0 && p.status === 'completed' && (
                        <span className="text-xs text-yellow-400 bg-yellow-500/10 px-2 py-1 rounded-full">⏳ в очереди</span>
                      )}
                      <span className={`badge ${p.status === 'completed' ? 'badge-green' : p.status === 'pending' ? 'badge-orange' : 'badge-red'}`}>{p.status}</span>
                      <span className="text-green-400 font-bold">{parseFloat(p.amount).toFixed(2)} ₽</span>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* WIPES */}
            {tab === 'wipes' && (
              <div className="space-y-5">
                <div className="glass p-6">
                  <h3 className="font-bold text-white mb-5 flex items-center gap-2"><Plus size={16} />Добавить вайп</h3>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <InputField label="ID сервера" value={wipeForm.server_id} onChange={v => setWipeForm(p => ({ ...p, server_id: v }))} placeholder="anarchy..." />
                    <InputField label="Название сервера" value={wipeForm.server_name} onChange={v => setWipeForm(p => ({ ...p, server_name: v }))} placeholder="Анархия..." />
                    <InputField label="Дата вайпа" value={wipeForm.wipe_date} onChange={v => setWipeForm(p => ({ ...p, wipe_date: v }))} type="datetime-local" />
                    <div>
                      <label className="text-sm font-medium text-gray-400 mb-1.5 block">Тип вайпа</label>
                      <select value={wipeForm.wipe_type} onChange={e => setWipeForm(p => ({ ...p, wipe_type: e.target.value }))} className="input-field">
                        <option value="full">Полный</option>
                        <option value="partial">Частичный</option>
                        <option value="economy">Экономика</option>
                        <option value="map">Карта</option>
                      </select>
                    </div>
                    <div className="md:col-span-2">
                      <InputField label="Описание" value={wipeForm.description} onChange={v => setWipeForm(p => ({ ...p, description: v }))} placeholder="Что будет стёрто..." as="textarea" />
                    </div>
                  </div>
                  <div className="mt-4 flex items-center gap-4">
                    <button onClick={saveWipe} className="btn-gradient px-6 py-2.5 text-sm"><span>Добавить</span></button>
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input type="checkbox" checked={wipeForm.kick_now} onChange={e => setWipeForm(p => ({ ...p, kick_now: e.target.checked }))} className="w-4 h-4 rounded accent-red-500" />
                      <span className="text-sm text-red-400">⚡ Кикнуть всех игроков сейчас</span>
                    </label>
                  </div>
                </div>
                <div className="space-y-3">
                  {wipes.map(w => (
                    <div key={w.id} className="glass p-4 flex items-center gap-4">
                      <div className="flex-1">
                        <p className="text-white font-semibold">{w.server_name}</p>
                        <p className="text-gray-500 text-xs">{new Date(w.wipe_date).toLocaleString('ru-RU')} · {w.wipe_type}</p>
                      </div>
                      {w.isPast ? <span className="badge badge-gray">Прошёл</span> : <span className="text-orange-400 text-sm font-semibold">Осталось {w.daysLeft}д</span>}
                      <button onClick={() => deleteWipe(w.id)} className="p-2 text-gray-500 hover:text-red-400 hover:bg-red-500/10 rounded-lg transition-all"><Trash2 size={16} /></button>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* STAFF */}
            {tab === 'staff' && (
              <div className="space-y-5">
                <div className="glass p-6">
                  <h3 className="font-bold text-white mb-5 flex items-center gap-2"><Plus size={16} />Добавить участника</h3>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <InputField label="Никнейм (MC)" value={staffForm.name} onChange={v => setStaffForm(p => ({ ...p, name: v }))} placeholder="Никнейм..." />
                    <InputField label="Должность" value={staffForm.role} onChange={v => setStaffForm(p => ({ ...p, role: v }))} placeholder="Администратор..." />
                    <InputField label="URL аватара" value={staffForm.avatar} onChange={v => setStaffForm(p => ({ ...p, avatar: v }))} placeholder="Пусто = mc-heads.net..." />
                    <div>
                      <label className="text-sm font-medium text-gray-400 mb-1.5 block">Цвет роли</label>
                      <input type="color" value={staffForm.role_color} onChange={e => setStaffForm(p => ({ ...p, role_color: e.target.value }))} className="h-11 w-full rounded-xl cursor-pointer bg-dark-800 border border-white/10" />
                    </div>
                    <InputField label="Discord ID" value={staffForm.discord} onChange={v => setStaffForm(p => ({ ...p, discord: v }))} placeholder="Discord username..." />
                    <InputField label="Telegram" value={staffForm.telegram} onChange={v => setStaffForm(p => ({ ...p, telegram: v }))} placeholder="username без @..." />
                    <div className="md:col-span-2">
                      <InputField label="Описание" value={staffForm.description} onChange={v => setStaffForm(p => ({ ...p, description: v }))} placeholder="Краткое описание..." as="textarea" />
                    </div>
                  </div>
                  <button onClick={saveStaff} className="btn-gradient mt-4 px-6 py-2.5 text-sm"><span>Добавить</span></button>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                  {staffList.map(s => (
                    <div key={s.id} className="glass p-4 flex items-center gap-3">
                      <img src={s.avatar || `https://mc-heads.net/avatar/${s.name}/40`} alt="" className="w-10 h-10 rounded-xl flex-shrink-0" style={{ imageRendering: 'pixelated' }} />
                      <div className="flex-1 min-w-0">
                        <p className="text-white font-semibold truncate">{s.name}</p>
                        <p className="text-xs font-semibold" style={{ color: s.role_color }}>{s.role}</p>
                      </div>
                      <button onClick={() => removeStaff(s.id)} className="p-2 text-gray-500 hover:text-red-400 hover:bg-red-500/10 rounded-lg transition-all flex-shrink-0"><Trash2 size={15} /></button>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* CONSOLE */}
            {tab === 'console' && (
              <div className="glass p-6">
                <h3 className="font-bold text-white mb-5 flex items-center gap-2"><Terminal size={16} className="text-orange-400" />RCON Консоль</h3>
                <div className="space-y-4">
                  <div>
                    <label className="text-sm font-medium text-gray-400 mb-1.5 block">Сервер</label>
                    <select value={selectedServer} onChange={e => setSelectedServer(e.target.value)} className="input-field">
                      <option value="anarchy">Анархия (test-server)</option>
                      <option value="bungeecord">BungeeCord (прокси)</option>
                    </select>
                  </div>
                  <div className="flex gap-3">
                    <input value={cmdInput} onChange={e => setCmdInput(e.target.value)} onKeyDown={e => e.key === 'Enter' && execCmd()}
                      placeholder="Команда... (Enter = выполнить)" className="input-field flex-1 font-mono text-sm" />
                    <button onClick={execCmd} disabled={loading} className="btn-gradient px-5 py-3 flex-shrink-0 disabled:opacity-50"><span>{loading ? '...' : '▶'}</span></button>
                  </div>
                  {cmdResult && (
                    <div className="p-4 bg-dark-950 rounded-xl font-mono text-sm text-green-400 border border-green-500/20 max-h-60 overflow-y-auto whitespace-pre-wrap">{cmdResult}</div>
                  )}
                </div>
                <div className="mt-4 p-3 glass-orange rounded-xl text-xs text-gray-500">
                  ⚠️ Команды stop, restart, reload заблокированы в целях безопасности
                </div>
              </div>
            )}

            {/* SETTINGS — платёжные системы */}
            {tab === 'settings' && (
              <div className="space-y-6">
                <div className="glass p-6">
                  <h3 className="text-xl font-black text-white mb-2">Настройка платёжных систем</h3>
                  <p className="text-gray-500 text-sm mb-6">Все настройки находятся в файле <code className="text-orange-400">config.js</code> — раздел <code className="text-orange-400">payments</code>. Включите нужную систему и заполните реквизиты.</p>

                  {/* YooKassa */}
                  <div className="space-y-4">
                    <PaymentCard
                      icon="💳"
                      name="ЮKassa (Юкасса)"
                      badge="Рекомендуется"
                      badgeColor="green"
                      desc="Лучший вариант для РФ. Поддерживает банковские карты, СБП, ЮMoney. Деньги приходят быстро, комиссия 3.5%."
                      steps={[
                        'Зарегистрируйтесь на yookassa.ru',
                        'Подключите магазин, получите shopId и secretKey',
                        'В config.js: yookassa.enabled = true',
                        'Вставьте shopId и secretKey',
                        'Webhook URL для ЮКассы: https://ВАШ_ДОМЕН/api/payments/webhook/yookassa',
                      ]}
                      configKey="yookassa"
                      fields={['shopId — числовой ID магазина', 'secretKey — live_... ключ из личного кабинета']}
                    />

                    <PaymentCard
                      icon="₿"
                      name="CryptoBot (Telegram)"
                      badge="Крипта"
                      badgeColor="purple"
                      desc="Оплата через Telegram бота. USDT, TON, BTC, ETH и другие. Подходит для анонимных платежей и международных игроков."
                      steps={[
                        'Откройте @CryptoBot в Telegram',
                        '/pay → My Apps → Create App',
                        'Скопируйте API Token',
                        'В config.js: cryptobot.enabled = true',
                        'Вставьте token, поменяйте network на mainnet',
                        'Webhook URL: https://ВАШ_ДОМЕН/api/payments/webhook/cryptobot',
                      ]}
                      configKey="cryptobot"
                      fields={["token — токен из @CryptoBot", "network — 'mainnet' для боевого режима"]}
                    />

                    <PaymentCard
                      icon="🔥"
                      name="LAVA (lava.ru)"
                      badge=""
                      badgeColor=""
                      desc="Российская платёжка. Карты, СБП. Простая интеграция, хорошая поддержка. Альтернатива ЮКассе."
                      steps={[
                        'Зарегистрируйтесь на lava.ru',
                        'Создайте проект, получите shopId (UUID) и secretKey',
                        'В config.js: lava.enabled = true',
                        'Webhook URL: https://ВАШ_ДОМЕН/api/payments/webhook/lava',
                      ]}
                      configKey="lava"
                      fields={['shopId — UUID проекта', 'secretKey — секретный ключ']}
                    />

                    <PaymentCard
                      icon="🏦"
                      name="FreeKassa"
                      badge=""
                      badgeColor=""
                      desc="Поддерживает много методов: карты, QIWI, WebMoney, СБП. Комиссия зависит от метода."
                      steps={[
                        'Зарегистрируйтесь на freekassa.ru',
                        'Создайте магазин, получите merchantId и секретные слова',
                        'В config.js: freekassa.enabled = true',
                        'Webhook URL: https://ВАШ_ДОМЕН/api/payments/webhook/freekassa',
                      ]}
                      configKey="freekassa"
                      fields={['merchantId — ID магазина', 'secretWord1, secretWord2 — из настроек']}
                    />

                    <PaymentCard
                      icon="💰"
                      name="Robokassa"
                      badge=""
                      badgeColor=""
                      desc="Популярна для игровых проектов. Банковские карты, СБП, электронные кошельки."
                      steps={[
                        'Зарегистрируйтесь на robokassa.com',
                        'Создайте магазин, получите логин и пароли',
                        'В config.js: robokassa.enabled = true',
                        'Webhook URL: https://ВАШ_ДОМЕН/api/payments/webhook/robokassa',
                      ]}
                      configKey="robokassa"
                      fields={['merchantLogin — логин магазина', 'password1, password2 — из настроек']}
                    />
                  </div>
                </div>

                <div className="glass p-6 border border-orange-500/15">
                  <h3 className="font-bold text-white mb-3">Структура config.js (payments)</h3>
                  <pre className="text-xs text-green-400 bg-dark-950 p-4 rounded-xl overflow-x-auto">{`payments: {
  yookassa: {
    enabled: true,          // ← поставьте true
    shopId: '123456',       // ← ваш Shop ID
    secretKey: 'live_...',  // ← ваш Secret Key
  },
  cryptobot: {
    enabled: true,
    token: '1234:AABBcc...', // ← токен из @CryptoBot
    network: 'mainnet',      // ← mainnet для боевого
  },
  // остальные false если не используете
}`}</pre>
                </div>
              </div>
            )}

          </div>
        </div>
      </div>

      {/* Shop item modal */}
      <Modal isOpen={shopItemModal} onClose={() => { setShopItemModal(false); setEditingItem(null); setShopForm(EMPTY_SHOP_FORM); }} title={editingItem ? 'Редактировать товар' : 'Новый товар'} size="lg">
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="col-span-2">
              <InputField label="Название товара" value={shopForm.name} onChange={v => setShopForm(p => ({ ...p, name: v }))} placeholder="VIP ранг на 30 дней..." />
            </div>
            <div>
              <label className="text-sm font-medium text-gray-400 mb-1.5 block">Категория</label>
              <select value={shopForm.category_id} onChange={e => setShopForm(p => ({ ...p, category_id: e.target.value }))} className="input-field">
                <option value="">Без категории</option>
                {shopCats.map(c => <option key={c.id} value={c.id}>{c.icon} {c.name}</option>)}
              </select>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-400 mb-1.5 block">Тип товара</label>
              <select value={shopForm.item_type} onChange={e => setShopForm(p => ({ ...p, item_type: e.target.value }))} className="input-field">
                <option value="donate">Донат (ранг, привилегии)</option>
                <option value="currency">Валюта (токены)</option>
                <option value="case">Кейс</option>
              </select>
            </div>
            <InputField label="Цена (₽)" value={shopForm.price} onChange={v => setShopForm(p => ({ ...p, price: v }))} placeholder="199" type="number" />
            <InputField label="Цена до скидки (₽)" value={shopForm.original_price} onChange={v => setShopForm(p => ({ ...p, original_price: v }))} placeholder="299 (необязательно)" type="number" />
            <div className="col-span-2">
              <InputField label="Описание" value={shopForm.description} onChange={v => setShopForm(p => ({ ...p, description: v }))} placeholder="Описание товара..." as="textarea" />
            </div>
            <div className="col-span-2">
              <InputField label="URL изображения" value={shopForm.image} onChange={v => setShopForm(p => ({ ...p, image: v }))} placeholder="https://..." />
            </div>
            <div>
              <label className="text-sm font-medium text-gray-400 mb-1.5 block">Сервер</label>
              <select value={shopForm.server_id} onChange={e => setShopForm(p => ({ ...p, server_id: e.target.value }))} className="input-field">
                <option value="anarchy">Анархия</option>
                <option value="all">Все серверы</option>
              </select>
            </div>
            <div className="flex flex-col gap-3 justify-center pt-5">
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={shopForm.featured} onChange={e => setShopForm(p => ({ ...p, featured: e.target.checked }))} className="w-4 h-4 rounded accent-orange-500" />
                <span className="text-sm text-gray-400">⭐ Топ товар</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={shopForm.visible} onChange={e => setShopForm(p => ({ ...p, visible: e.target.checked }))} className="w-4 h-4 rounded accent-orange-500" />
                <span className="text-sm text-gray-400">👁 Видимый</span>
              </label>
            </div>
            <div className="col-span-2">
              <label className="text-sm font-medium text-gray-400 mb-1.5 block">
                Команды RCON <span className="text-gray-600">(одна строка = одна команда, {'{player}'} = ник)</span>
              </label>
              <textarea
                value={shopForm.commands}
                onChange={e => setShopForm(p => ({ ...p, commands: e.target.value }))}
                placeholder={shopForm.item_type === 'currency'
                  ? 'eco token give {player} 1000'
                  : 'grantpriv {player} vip'}
                rows={4}
                className="input-field resize-none font-mono text-sm"
              />
            </div>
          </div>
          <button onClick={saveShopItem} className="btn-gradient w-full py-3">
            <span>{editingItem ? 'Сохранить изменения' : 'Создать товар'}</span>
          </button>
        </div>
      </Modal>
    </div>
  );
}

// Компонент карточки платёжной системы
function PaymentCard({ icon, name, badge, badgeColor, desc, steps, configKey, fields }) {
  const badgeColors = {
    green: 'text-green-400 bg-green-500/15 border-green-500/30',
    purple: 'text-violet-400 bg-violet-500/15 border-violet-500/30',
  };
  return (
    <div className="p-5 bg-dark-800 rounded-xl border border-white/5">
      <div className="flex items-start gap-3 mb-3">
        <span className="text-3xl flex-shrink-0">{icon}</span>
        <div className="flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <h4 className="font-bold text-white">{name}</h4>
            {badge && (
              <span className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${badgeColors[badgeColor] || ''}`}>{badge}</span>
            )}
          </div>
          <p className="text-gray-400 text-sm mt-1">{desc}</p>
        </div>
      </div>
      <div className="grid md:grid-cols-2 gap-4 mt-3">
        <div>
          <p className="text-xs font-semibold text-gray-500 mb-2 uppercase tracking-wide">Инструкция</p>
          <ol className="space-y-1">
            {steps.map((s, i) => (
              <li key={i} className="text-sm text-gray-400 flex gap-2">
                <span className="text-orange-500 font-bold flex-shrink-0">{i + 1}.</span>{s}
              </li>
            ))}
          </ol>
        </div>
        <div>
          <p className="text-xs font-semibold text-gray-500 mb-2 uppercase tracking-wide">Поля в config.js → payments.{configKey}</p>
          <div className="space-y-1">
            {fields.map((f, i) => (
              <div key={i} className="text-xs text-green-400 font-mono bg-dark-900 px-3 py-1.5 rounded-lg">{f}</div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
