import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const nextConfig = {
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: 'http://127.0.0.1:3001/api/:path*',
      },
      {
        source: '/uploads/:path*',
        destination: 'http://127.0.0.1:3001/uploads/:path*',
      },
    ];
  },
  webpack: (config) => {
    config.resolve.alias['react-router-dom'] = path.join(__dirname, 'src', 'lib', 'router-compat.jsx');
    return config;
  },
};

export default nextConfig;
