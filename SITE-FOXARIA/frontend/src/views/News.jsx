import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/client';
import { Newspaper, Eye, Pin } from 'lucide-react';
import Loading from '../components/Loading';

export default function News() {
  const [news, setNews] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    api.get(`/news?page=${page}&limit=9`)
      .then(r => { setNews(r.data.news || []); setTotal(r.data.total || 0); })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [page]);

  return (
    <div className="min-h-screen pt-24 pb-16">
      <div className="page-container">
        <div className="text-center mb-10 animate-slide-up">
          <div className="inline-flex p-4 bg-orange-500/10 border border-orange-500/20 rounded-2xl mb-4">
            <Newspaper size={36} className="text-orange-400" />
          </div>
          <h1 className="section-title gradient-text">Новости</h1>
          <p className="text-gray-500">Будьте в курсе событий на сервере Foxaria</p>
        </div>

        {loading ? <Loading size="sm" /> : news.length === 0 ? (
          <div className="glass p-16 text-center">
            <Newspaper size={56} className="text-gray-700 mx-auto mb-4" />
            <p className="text-xl font-bold text-gray-400">Новостей пока нет</p>
          </div>
        ) : (
          <>
            {/* Featured/pinned */}
            {news.filter(n => n.pinned).map(n => (
              <Link key={n.id} to={`/news/${n.id}`} className="block glass mb-6 overflow-hidden hover:border-orange-500/25 transition-all group animate-slide-up">
                <div className="md:flex">
                  {n.image && (
                    <div className="md:w-80 h-48 md:h-auto flex-shrink-0 overflow-hidden bg-dark-800">
                      <img src={n.image} alt={n.title} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                    </div>
                  )}
                  <div className="p-8 flex flex-col justify-center">
                    <div className="flex items-center gap-2 mb-3">
                      <span className="badge-orange"><Pin size={10} />Закреплено</span>
                      <span className="text-gray-600 text-xs">{new Date(n.created_at).toLocaleDateString('ru-RU')}</span>
                    </div>
                    <h2 className="text-2xl font-black text-white group-hover:gradient-text transition-all mb-3">{n.title}</h2>
                    {n.preview && <p className="text-gray-400 leading-relaxed line-clamp-2">{n.preview}</p>}
                    <div className="flex items-center gap-3 mt-4 text-sm text-gray-500">
                      {n.author && <span>Автор: <span className="text-gray-400">{n.author}</span></span>}
                      {n.views > 0 && <span className="flex items-center gap-1"><Eye size={13} />{n.views}</span>}
                    </div>
                  </div>
                </div>
              </Link>
            ))}

            {/* Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5 stagger">
              {news.filter(n => !n.pinned).map(n => (
                <Link key={n.id} to={`/news/${n.id}`} className="glass overflow-hidden hover:border-orange-500/25 transition-all group block animate-slide-up">
                  {n.image && (
                    <div className="h-44 overflow-hidden bg-dark-800">
                      <img src={n.image} alt={n.title} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                    </div>
                  )}
                  <div className="p-5">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="badge-orange text-xs">{n.server === 'all' ? 'Все серверы' : n.server}</span>
                      <span className="text-gray-600 text-xs">{new Date(n.created_at).toLocaleDateString('ru-RU')}</span>
                    </div>
                    <h3 className="font-bold text-white group-hover:gradient-text transition-all line-clamp-2 text-lg">{n.title}</h3>
                    {n.preview && <p className="text-gray-500 text-sm mt-2 line-clamp-2">{n.preview}</p>}
                    <div className="flex items-center gap-3 mt-4 text-xs text-gray-600">
                      {n.author && <span>{n.author}</span>}
                      {n.views > 0 && <span className="flex items-center gap-1 ml-auto"><Eye size={11} />{n.views}</span>}
                    </div>
                  </div>
                </Link>
              ))}
            </div>

            {/* Pagination */}
            {total > 9 && (
              <div className="flex justify-center gap-3 mt-10">
                <button onClick={() => setPage(p => Math.max(1, p - 1))} disabled={page === 1} className="btn-outline px-5 py-2 text-sm disabled:opacity-40">← Назад</button>
                <span className="flex items-center text-gray-500 text-sm">{page} / {Math.ceil(total / 9)}</span>
                <button onClick={() => setPage(p => p + 1)} disabled={page >= Math.ceil(total / 9)} className="btn-outline px-5 py-2 text-sm disabled:opacity-40">Вперёд →</button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
