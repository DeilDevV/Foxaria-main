import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../api/client';
import toast from 'react-hot-toast';
import Modal from '../components/Modal';
import Loading from '../components/Loading';
import { ShoppingBag, Star, CreditCard, ArrowRight, Check, Info } from 'lucide-react';

function ShopItem({ item, onBuy }) {
  const discount = item.original_price ? Math.round((1 - item.price / item.original_price) * 100) : 0;
  return (
    <div className="glass p-5 flex flex-col hover:border-orange-500/25 transition-all duration-300 group animate-slide-up">
      {/* Image */}
      <div className="h-40 rounded-xl overflow-hidden bg-dark-800 mb-4 relative flex-shrink-0">
        {item.image ? (
          <img src={item.image} alt={item.name} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-6xl">
            {item.item_type === 'donate' ? '👑' : item.item_type === 'case' ? '📦' : item.item_type === 'currency' ? '💰' : '🎁'}
          </div>
        )}
        {item.featured && (
          <div className="absolute top-2 right-2 flex items-center gap-1 px-2 py-1 bg-yellow-500/20 border border-yellow-500/40 rounded-full text-yellow-400 text-xs font-semibold">
            <Star size={10} fill="currentColor" />ТОП
          </div>
        )}
        {discount > 0 && (
          <div className="absolute top-2 left-2 px-2 py-1 bg-red-500/80 rounded-full text-white text-xs font-bold">
            -{discount}%
          </div>
        )}
      </div>

      <div className="flex-1 flex flex-col">
        <div className="flex items-center gap-2 mb-1">
          <span className="badge-purple text-xs">{item.category_name || 'Товар'}</span>
          {item.server_id !== 'all' && <span className="badge-gray text-xs">{item.server_id}</span>}
        </div>
        <h3 className="font-bold text-white text-lg leading-tight mb-2">{item.name}</h3>
        {item.description && <p className="text-gray-500 text-sm leading-relaxed flex-1 line-clamp-3">{item.description}</p>}

        <div className="mt-4 flex items-center justify-between">
          <div>
            {item.original_price && (
              <p className="text-gray-600 line-through text-sm">{parseFloat(item.original_price).toFixed(0)} ₽</p>
            )}
            <p className="text-2xl font-black gradient-text">{parseFloat(item.price).toFixed(0)} ₽</p>
          </div>
          <button onClick={() => onBuy(item)} className="btn-gradient px-5 py-2.5 text-sm">
            <span className="flex items-center gap-2"><ShoppingBag size={16} />Купить</span>
          </button>
        </div>
      </div>
    </div>
  );
}

const PAYMENT_METHODS = [
  { id: 'yookassa', name: 'ЮKassa', desc: 'Карты, СБП, ЮMoney', icon: '💳', popular: true },
  { id: 'freekassa', name: 'FreeKassa', desc: 'Карты, QIWI, WebMoney', icon: '🏦', popular: false },
  { id: 'robokassa', name: 'Robokassa', desc: 'Банковские карты, СБП', icon: '💰', popular: false },
  { id: 'cryptobot', name: 'CryptoBot', desc: 'USDT, TON, BTC', icon: '₿', popular: false },
  { id: 'lava', name: 'LAVA', desc: 'Карты, СБП', icon: '🔥', popular: false },
];

export default function Shop() {
  const { user } = useAuth();
  const [categories, setCategories] = useState([]);
  const [items, setItems] = useState([]);
  const [featured, setFeatured] = useState([]);
  const [activeCategory, setActiveCategory] = useState(null);
  const [loading, setLoading] = useState(true);
  const [buyModal, setBuyModal] = useState(false);
  const [selectedItem, setSelectedItem] = useState(null);
  const [selectedMethod, setSelectedMethod] = useState('yookassa');
  const [paying, setPaying] = useState(false);
  const [availableMethods, setAvailableMethods] = useState([]);

  useEffect(() => {
    Promise.all([
      api.get('/shop/categories'),
      api.get('/shop/items?featured=true'),
      api.get('/payments/methods'),
    ]).then(([cats, feat, methods]) => {
      setCategories(cats.data);
      setFeatured(feat.data);
      setAvailableMethods(methods.data);
      if (methods.data.length > 0) setSelectedMethod(methods.data[0].id);
    }).catch(() => {}).finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const params = activeCategory ? `?category=${activeCategory}` : '';
    api.get(`/shop/items${params}`).then(r => setItems(r.data)).catch(() => {});
  }, [activeCategory]);

  function openBuy(item) {
    if (!user) { toast.error('Войдите в аккаунт для покупки'); return; }
    setSelectedItem(item);
    setBuyModal(true);
  }

  async function handleBuy() {
    if (!selectedItem || !selectedMethod) return;
    setPaying(true);
    try {
      const res = await api.post('/payments/create', { itemId: selectedItem.id, method: selectedMethod });
      toast.success('Перенаправление к оплате...');
      window.location.href = res.data.confirmationUrl;
    } catch (err) {
      toast.error(err.response?.data?.error || 'Ошибка создания платежа');
    } finally {
      setPaying(false);
    }
  }

  return (
    <div className="min-h-screen pt-24 pb-16">
      <div className="page-container">
        {/* Header */}
        <div className="text-center mb-10 animate-slide-up">
          <div className="inline-flex p-4 bg-orange-500/10 border border-orange-500/20 rounded-2xl mb-4">
            <ShoppingBag size={36} className="text-orange-400" />
          </div>
          <h1 className="section-title gradient-text">Магазин</h1>
          <p className="text-gray-500">Поддержи проект и получи уникальные привилегии</p>
          {!user && (
            <div className="inline-flex items-center gap-2 mt-4 px-4 py-2 glass-orange rounded-xl text-sm text-orange-400">
              <Info size={15} />
              <span>Для покупки необходимо <Link to="/login" className="underline font-semibold">войти в аккаунт</Link></span>
            </div>
          )}
        </div>

        {loading ? <Loading size="sm" /> : (
          <>
            {/* Featured */}
            {featured.length > 0 && (
              <div className="mb-10">
                <h2 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
                  <Star size={18} className="text-yellow-400" fill="currentColor" />Популярное
                </h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 stagger">
                  {featured.slice(0, 4).map(item => <ShopItem key={item.id} item={item} onBuy={openBuy} />)}
                </div>
              </div>
            )}

            {/* Categories filter */}
            {categories.length > 0 && (
              <div className="flex gap-2 overflow-x-auto no-scrollbar mb-6 pb-1">
                <button
                  onClick={() => setActiveCategory(null)}
                  className={`flex-shrink-0 px-5 py-2.5 rounded-xl text-sm font-semibold transition-all ${!activeCategory ? 'bg-orange-500/15 text-orange-400 border border-orange-500/25' : 'text-gray-400 hover:text-white hover:bg-white/5'}`}
                >
                  🎁 Все
                </button>
                {categories.map(c => (
                  <button
                    key={c.id}
                    onClick={() => setActiveCategory(c.id)}
                    className={`flex-shrink-0 px-5 py-2.5 rounded-xl text-sm font-semibold transition-all ${activeCategory === c.id ? 'bg-orange-500/15 text-orange-400 border border-orange-500/25' : 'text-gray-400 hover:text-white hover:bg-white/5'}`}
                  >
                    {c.icon} {c.name}
                  </button>
                ))}
              </div>
            )}

            {/* Items grid */}
            {items.length === 0 ? (
              <div className="glass p-16 text-center">
                <ShoppingBag size={56} className="text-gray-700 mx-auto mb-4" />
                <p className="text-gray-400 text-xl font-semibold">Нет товаров</p>
                <p className="text-gray-600 text-sm mt-1">В этой категории пока нет товаров</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5 stagger">
                {items.map(item => <ShopItem key={item.id} item={item} onBuy={openBuy} />)}
              </div>
            )}

            {/* Info block */}
            <div className="mt-10 glass p-6 border border-orange-500/15">
              <h3 className="font-bold text-white mb-3 flex items-center gap-2"><Info size={18} className="text-orange-400" />Информация о покупках</h3>
              <ul className="space-y-2 text-gray-400 text-sm">
                <li className="flex items-start gap-2"><Check size={14} className="text-green-400 mt-0.5 flex-shrink-0" />Привилегии выдаются автоматически после оплаты</li>
                <li className="flex items-start gap-2"><Check size={14} className="text-green-400 mt-0.5 flex-shrink-0" />Если вы были офлайн — привилегия выдастся при входе на сервер</li>
                <li className="flex items-start gap-2"><Check size={14} className="text-green-400 mt-0.5 flex-shrink-0" />При проблемах с оплатой — обращайтесь в Discord поддержку</li>
                <li className="flex items-start gap-2"><Check size={14} className="text-green-400 mt-0.5 flex-shrink-0" />Все покупки сохраняются в вашем личном кабинете</li>
              </ul>
            </div>
          </>
        )}
      </div>

      {/* Buy Modal */}
      <Modal isOpen={buyModal} onClose={() => setBuyModal(false)} title="Оформление покупки" size="lg">
        {selectedItem && (
          <div className="space-y-6">
            {/* Item summary */}
            <div className="flex items-center gap-4 p-4 glass-orange rounded-xl">
              <div className="w-16 h-16 rounded-xl overflow-hidden bg-dark-800 flex-shrink-0">
                {selectedItem.image ? (
                  <img src={selectedItem.image} alt={selectedItem.name} className="w-full h-full object-cover" />
                ) : (
                  <div className="w-full h-full flex items-center justify-center text-2xl">🎁</div>
                )}
              </div>
              <div className="flex-1 min-w-0">
                <p className="font-bold text-white text-lg">{selectedItem.name}</p>
                {selectedItem.description && <p className="text-gray-500 text-sm line-clamp-1 mt-0.5">{selectedItem.description}</p>}
              </div>
              <p className="text-2xl font-black gradient-text flex-shrink-0">{parseFloat(selectedItem.price).toFixed(0)} ₽</p>
            </div>

            {/* Nickname confirm */}
            <div className="p-4 bg-dark-800 rounded-xl text-sm">
              <p className="text-gray-500 mb-1">Будет выдано игроку:</p>
              <div className="flex items-center gap-2">
                <img src={user?.avatar} alt="" className="w-7 h-7 rounded-lg" style={{ imageRendering: 'pixelated' }} />
                <p className="text-white font-bold">{user?.username}</p>
              </div>
            </div>

            {/* Payment methods */}
            <div>
              <p className="text-gray-400 text-sm font-semibold mb-3">Выберите способ оплаты:</p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {availableMethods.map(method => (
                  <label key={method.id} className={`flex items-center gap-3 p-4 rounded-xl border cursor-pointer transition-all ${
                    selectedMethod === method.id
                      ? 'border-orange-500/50 bg-orange-500/10'
                      : 'border-white/8 hover:border-white/15 hover:bg-white/3'
                  }`}>
                    <input type="radio" value={method.id} checked={selectedMethod === method.id} onChange={() => setSelectedMethod(method.id)} className="sr-only" />
                    <span className="text-2xl">{method.icon}</span>
                    <div className="flex-1 min-w-0">
                      <p className="text-white font-semibold text-sm flex items-center gap-2">
                        {method.name}
                        {method.popular && <span className="badge badge-orange text-xs">Популярно</span>}
                      </p>
                      <p className="text-gray-500 text-xs truncate">{method.desc}</p>
                    </div>
                    <div className={`w-5 h-5 rounded-full border-2 flex-shrink-0 flex items-center justify-center ${
                      selectedMethod === method.id ? 'border-orange-500 bg-orange-500' : 'border-gray-600'
                    }`}>
                      {selectedMethod === method.id && <Check size={12} />}
                    </div>
                  </label>
                ))}
              </div>
              {availableMethods.length === 0 && (
                <p className="text-gray-500 text-sm text-center py-4">Нет доступных методов оплаты. Настройте их в config.js</p>
              )}
            </div>

            <button
              onClick={handleBuy}
              disabled={paying || !selectedMethod || availableMethods.length === 0}
              className="btn-gradient w-full py-4 text-base flex items-center justify-center gap-3 disabled:opacity-50"
            >
              {paying ? (
                <div className="w-5 h-5 rounded-full border-2 border-white/30 border-t-white animate-spin" />
              ) : (
                <>
                  <CreditCard size={18} />
                  <span>Оплатить {parseFloat(selectedItem.price).toFixed(0)} ₽</span>
                  <ArrowRight size={16} />
                </>
              )}
            </button>

            <p className="text-gray-600 text-xs text-center">
              Нажимая «Оплатить», вы соглашаетесь с условиями возврата и политикой сервиса
            </p>
          </div>
        )}
      </Modal>
    </div>
  );
}
