import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="min-h-screen pt-28 px-4">
      <div className="mx-auto max-w-2xl rounded-[32px] border border-white/10 bg-gradient-card p-10 text-center shadow-2xl shadow-black/30">
        <p className="mb-3 text-sm font-semibold uppercase tracking-[0.3em] text-orange-400">404</p>
        <h1 className="text-4xl font-black text-white">Страница не найдена</h1>
        <p className="mt-4 text-gray-400">Похоже, этот маршрут больше не существует или был перемещен.</p>
        <Link href="/" className="btn-gradient mt-8 inline-flex px-6 py-3">
          <span>Вернуться на главную</span>
        </Link>
      </div>
    </div>
  );
}
