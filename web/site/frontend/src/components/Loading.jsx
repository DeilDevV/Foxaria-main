import BrandMark from './BrandMark';

export default function Loading({ size = 'full' }) {
  if (size === 'sm') {
    return (
      <div className="flex items-center justify-center p-8">
        <div className="relative h-10 w-10">
          <div className="absolute inset-0 rounded-full border-2 border-transparent border-t-orange-500 animate-spin" />
          <div
            className="absolute inset-1 rounded-full border-2 border-transparent border-b-violet-500 animate-spin"
            style={{ animationDirection: 'reverse', animationDuration: '0.8s' }}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-dark-900">
      <div className="flex flex-col items-center gap-6">
        <div className="relative">
          <div className="absolute inset-0 m-auto h-24 w-24 rounded-full border border-orange-500/20 bg-orange-500/5 blur-xl" />
          <BrandMark />
        </div>
        <div>
          <p className="gradient-text text-center text-2xl font-bold tracking-wide">FOXARIA</p>
          <p className="mt-1 text-center text-sm text-gray-500">Загрузка...</p>
        </div>
      </div>
    </div>
  );
}
