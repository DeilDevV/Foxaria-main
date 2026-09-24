import '../src/index.css';
import Providers from './providers';
import SiteShell from '@/components/SiteShell';

export const metadata = {
  title: 'FOXARIA',
  description: 'Minecraft-анархия с новостями, магазином, бан-листом и личным кабинетом.',
};

export default function RootLayout({ children }) {
  return (
    <html lang="ru">
      <body>
        <Providers>
          <SiteShell>{children}</SiteShell>
        </Providers>
      </body>
    </html>
  );
}
