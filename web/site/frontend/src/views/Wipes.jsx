import { useState, useEffect } from 'react';
import api from '../api/client';
import { Calendar, Clock, Server, AlertTriangle } from 'lucide-react';
import Loading from '../components/Loading';

const wipeTypeLabels = {
  full: { label: 'Полный вайп', color: 'red', emoji: '💥' },
  partial: { label: 'Частичный вайп', color: 'orange', emoji: '⚠️' },
  economy: { label: 'Вайп экономики', color: 'yellow', emoji: '💰' },
  map: { label: 'Вайп карты', color: 'blue', emoji: '🗺️' },
};

function Countdown({ targetDate }) {
  const [remaining, setRemaining] = useState('');

  useEffect(() => {
    function update() {
      const diff = new Date(targetDate) - new Date();
      if (diff <= 0) { setRemaining('Вайп прошёл'); return; }
      const days = Math.floor(diff / 86400000);
      const hours = Math.floor((diff % 86400000) / 3600000);
      const minutes = Math.floor((diff % 3600000) / 60000);
      const seconds = Math.floor((diff % 60000) / 1000);
      if (days > 0) setRemaining(`${days}д ${hours}ч ${minutes}м`);
      else setRemaining(`${hours}ч ${minutes}м ${seconds}с`);
    }
    update();
    const interval = setInterval(update, 1000);
    return () => clearInterval(interval);
  }, [targetDate]);

  return <span className="font-mono text-orange-400 font-bold">{remaining}</span>;
}

export default function Wipes() {
  const [wipes, setWipes] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get('/wipes').then(r => setWipes(r.data)).catch(() => {}).finally(() => setLoading(false));
  }, []);

  const upcoming = wipes.filter(w => !w.isPast && !w.completed);
  const past = wipes.filter(w => w.isPast || w.completed);

  return (
    <div className="min-h-screen pt-24 pb-16">
      <div className="page-container">
        <div className="text-center mb-10 animate-slide-up">
          <div className="inline-flex p-4 bg-red-500/10 border border-red-500/20 rounded-2xl mb-4">
            <Calendar size={36} className="text-red-400" />
          </div>
          <h1 className="section-title gradient-text">Расписание вайпов</h1>
          <p className="text-gray-500">Планируйте игру заранее — знайте когда ждать вайп</p>
        </div>

        {loading ? <Loading size="sm" /> : (
          <div className="space-y-8">
            {/* Upcoming */}
            <div>
              <h2 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
                <Clock size={20} className="text-orange-400" />Предстоящие вайпы
              </h2>
              {upcoming.length === 0 ? (
                <div className="glass p-8 text-center">
                  <p className="text-gray-400">Нет запланированных вайпов</p>
                  <p className="text-gray-600 text-sm mt-1">Следите за новостями</p>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-5 stagger">
                  {upcoming.map(wipe => {
                    const typeInfo = wipeTypeLabels[wipe.wipe_type] || wipeTypeLabels.full;
                    return (
                      <div key={wipe.id} className={`glass p-6 border-2 ${
                        wipe.daysLeft <= 3 ? 'border-red-500/40 bg-red-500/5' :
                        wipe.daysLeft <= 7 ? 'border-orange-500/30 bg-orange-500/5' :
                        'border-white/8'
                      } animate-slide-up`}>
                        <div className="flex items-start justify-between mb-4">
                          <div className="flex items-center gap-3">
                            <span className="text-3xl">{typeInfo.emoji}</span>
                            <div>
                              <p className="font-bold text-white text-lg">{wipe.server_name}</p>
                              <p className={`text-sm font-semibold ${
                                typeInfo.color === 'red' ? 'text-red-400' :
                                typeInfo.color === 'orange' ? 'text-orange-400' :
                                typeInfo.color === 'yellow' ? 'text-yellow-400' : 'text-blue-400'
                              }`}>{typeInfo.label}</p>
                            </div>
                          </div>
                          {wipe.daysLeft <= 3 && (
                            <div className="flex items-center gap-1 px-3 py-1 bg-red-500/15 border border-red-500/30 rounded-full text-red-400 text-xs font-semibold animate-pulse">
                              <AlertTriangle size={11} />Скоро!
                            </div>
                          )}
                        </div>

                        <div className="p-4 bg-dark-800 rounded-xl mb-4 text-center">
                          <p className="text-gray-500 text-xs mb-1">Осталось</p>
                          <p className="text-xl"><Countdown targetDate={wipe.wipe_date} /></p>
                        </div>

                        <div className="flex items-center gap-2 text-sm text-gray-500">
                          <Calendar size={14} className="text-orange-400" />
                          <span>{new Date(wipe.wipe_date).toLocaleString('ru-RU', { day: '2-digit', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' })}</span>
                        </div>
                        {wipe.description && (
                          <p className="text-gray-400 text-sm mt-3 leading-relaxed">{wipe.description}</p>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Past */}
            {past.length > 0 && (
              <div>
                <h2 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
                  <Clock size={20} className="text-gray-500" />История вайпов
                </h2>
                <div className="space-y-3 opacity-60">
                  {past.slice(0, 10).map(wipe => {
                    const typeInfo = wipeTypeLabels[wipe.wipe_type] || wipeTypeLabels.full;
                    return (
                      <div key={wipe.id} className="glass p-4 flex items-center gap-4">
                        <span className="text-2xl">{typeInfo.emoji}</span>
                        <div className="flex-1 min-w-0">
                          <p className="text-white font-semibold">{wipe.server_name}</p>
                          <p className="text-gray-500 text-sm">{typeInfo.label}</p>
                        </div>
                        <p className="text-gray-500 text-sm text-right flex-shrink-0">
                          {new Date(wipe.wipe_date).toLocaleDateString('ru-RU')}
                        </p>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
