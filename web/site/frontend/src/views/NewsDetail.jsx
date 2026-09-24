import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import api from '../api/client';
import { ArrowLeft, Eye, Calendar, User } from 'lucide-react';
import Loading from '../components/Loading';

export default function NewsDetail() {
  const { id } = useParams();
  const [news, setNews] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get(`/news/${id}`)
      .then(r => setNews(r.data))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <Loading size="sm" />;
  if (!news) return (
    <div className="min-h-screen pt-24 flex items-center justify-center">
      <div className="text-center">
        <p className="text-gray-400 text-xl font-semibold">Новость не найдена</p>
        <Link to="/news" className="text-orange-400 hover:underline mt-2 block">← Все новости</Link>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen pt-24 pb-16">
      <div className="max-w-3xl mx-auto px-4">
        <Link to="/news" className="inline-flex items-center gap-2 text-gray-400 hover:text-orange-400 mb-6 transition-colors group">
          <ArrowLeft size={16} className="group-hover:-translate-x-1 transition-transform" />
          Все новости
        </Link>

        <article className="glass overflow-hidden animate-slide-up">
          {news.image && (
            <div className="h-64 sm:h-80 overflow-hidden bg-dark-800">
              <img src={news.image} alt={news.title} className="w-full h-full object-cover" />
            </div>
          )}
          <div className="p-8">
            <div className="flex flex-wrap items-center gap-3 mb-4">
              <span className="badge-orange">{news.server === 'all' ? 'Все серверы' : news.server}</span>
              {news.tags && news.tags.split(',').map(t => (
                <span key={t} className="badge badge-purple">{t.trim()}</span>
              ))}
            </div>
            <h1 className="text-3xl font-black text-white mb-4 leading-tight">{news.title}</h1>
            <div className="flex flex-wrap items-center gap-4 text-sm text-gray-500 mb-8 pb-6 border-b border-white/5">
              {news.author && <span className="flex items-center gap-1.5"><User size={14} />{news.author}</span>}
              <span className="flex items-center gap-1.5"><Calendar size={14} />{new Date(news.created_at).toLocaleString('ru-RU')}</span>
              {news.views > 0 && <span className="flex items-center gap-1.5 ml-auto"><Eye size={14} />{news.views} просмотров</span>}
            </div>
            <div
              className="prose prose-invert max-w-none text-gray-300 leading-relaxed"
              style={{ whiteSpace: 'pre-wrap', lineHeight: '1.8' }}
            >
              {news.content}
            </div>
          </div>
        </article>

        <div className="mt-8 text-center">
          <Link to="/news" className="btn-outline px-8 py-3">← Все новости</Link>
        </div>
      </div>
    </div>
  );
}
