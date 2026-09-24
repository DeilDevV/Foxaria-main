const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const rateLimit = require('express-rate-limit');
const path = require('path');
const config = require('../config');
const { initDatabase, isConnected } = require('./services/database');

const app = express();
app.set('trust proxy', 1);

// Security headers
app.use(helmet({ contentSecurityPolicy: false }));

// CORS
app.use(cors({
  origin: [config.site.frontendUrl, config.site.productionUrl],
  credentials: true,
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'],
}));

app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));
app.use(morgan('dev'));

// Rate limiting
const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 200,
  message: { error: 'Слишком много запросов, попробуйте позже' },
});
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 20,
  message: { error: 'Слишком много попыток входа' },
});

app.use('/api/', apiLimiter);
app.use('/api/auth/login', authLimiter);

// Static files for uploaded images
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));

// Serve built frontend (production)
const frontendDist = path.join(__dirname, '../frontend/dist');
const fs = require('fs');
if (fs.existsSync(frontendDist)) {
  app.use(express.static(frontendDist));
}

// Routes
app.use('/api/auth',     require('./routes/auth'));
app.use('/api/profile',  require('./routes/profile'));
app.use('/api/guild',    require('./routes/guild'));
app.use('/api/bans',     require('./routes/bans'));
app.use('/api/news',     require('./routes/news'));
app.use('/api/shop',     require('./routes/shop'));
app.use('/api/admin',    require('./routes/admin'));
app.use('/api/payments', require('./routes/payments'));
app.use('/api/servers',  require('./routes/servers'));
app.use('/api/private',  require('./routes/private'));
app.use('/api/wipes',    require('./routes/wipes'));
app.use('/api/staff',    require('./routes/staff'));

// Health check
app.get('/api/health', (req, res) => {
  const db = isConnected();
  res.status(db ? 200 : 503).json({
    status: db ? 'ok' : 'no_db',
    db: db ? 'connected' : 'disconnected',
    server: config.site.name,
    version: '1.0.0',
    time: new Date(),
  });
});

// SPA fallback — все неизвестные пути отдают index.html (если фронтенд собран)
app.get('*', (req, res, next) => {
  if (req.path.startsWith('/api')) return next();
  const index = path.join(frontendDist, 'index.html');
  if (fs.existsSync(index)) return res.sendFile(index);
  res.json({ message: `Foxaria API работает. Откройте http://localhost:5173 (dev) или соберите фронтенд: npm run build в папке frontend.` });
});

// Global error handler
app.use((err, req, res, next) => {
  console.error('Server error:', err);
  res.status(err.status || 500).json({
    error: err.message || 'Внутренняя ошибка сервера',
  });
});

async function start() {
  // Сервер стартует сразу — БД подключается асинхронно с авторетраями
  initDatabase().catch(() => {});

  app.listen(config.site.port, () => {
    console.log(`\n🚀 Foxaria API запущен: http://localhost:${config.site.port}`);
    console.log(`📊 Health check: http://localhost:${config.site.port}/api/health`);
    console.log(`   (БД подключается в фоне — сайт уже доступен)\n`);
  });
}

start().catch(console.error);
