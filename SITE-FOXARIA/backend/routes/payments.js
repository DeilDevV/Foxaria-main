const express = require('express');
const router = express.Router();
const { v4: uuidv4 } = require('uuid');
const { query, queryOne } = require('../services/database');
const { authMiddleware, adminMiddleware } = require('../middleware/auth');
const { executeCommands, executeBungeeCommands } = require('../services/rcon');
const paymentsService = require('../services/payments');
const config = require('../../config');

function isProxySideCommand(cmd) {
  const t = String(cmd).trim().toLowerCase();
  return t.startsWith('grantpriv ') || t.startsWith('grantpkg ') || t.startsWith('gpriv ')
    || t.startsWith('storegrantproxy ');
}

// Доступные методы оплаты (определяются по config.js)
router.get('/methods', (req, res) => {
  const methods = [];
  if (config.payments.yookassa.enabled)
    methods.push({ id: 'yookassa', name: 'ЮKassa', desc: 'Банк. карты, СБП, ЮMoney', icon: '💳', popular: true });
  if (config.payments.freekassa.enabled)
    methods.push({ id: 'freekassa', name: 'FreeKassa', desc: 'Карты, QIWI, WebMoney', icon: '🏦' });
  if (config.payments.robokassa.enabled)
    methods.push({ id: 'robokassa', name: 'Robokassa', desc: 'Банковские карты, СБП', icon: '💰' });
  if (config.payments.lava.enabled)
    methods.push({ id: 'lava', name: 'LAVA', desc: 'Карты, СБП', icon: '🔥' });
  if (config.payments.cryptobot.enabled)
    methods.push({ id: 'cryptobot', name: 'CryptoBot', desc: 'USDT, TON, BTC', icon: '₿' });
  res.json(methods);
});

// GET /api/payments/pending-count
router.get('/pending-count', adminMiddleware, async (req, res) => {
  try {
    const row = await queryOne('SELECT COUNT(*) as count FROM foxaria_pending_commands WHERE attempts < 10');
    res.json({ count: row?.count || 0 });
  } catch { res.json({ count: 0 }); }
});

// POST /api/payments/retry-pending — вручную повторить выдачу
router.post('/retry-pending', adminMiddleware, async (req, res) => {
  await retryPendingCommands();
  const row = await queryOne('SELECT COUNT(*) as count FROM foxaria_pending_commands WHERE attempts < 10').catch(() => ({ count: 0 }));
  res.json({ success: true, remaining: row?.count || 0 });
});

// POST /api/payments/create
router.post('/create', authMiddleware, async (req, res) => {
  try {
    const { itemId, method } = req.body;
    if (!itemId || !method) return res.status(400).json({ error: 'Укажите товар и метод оплаты' });

    const item = await queryOne('SELECT * FROM foxaria_shop_items WHERE id = ? AND visible = 1', [itemId]);
    if (!item) return res.status(404).json({ error: 'Товар не найден' });

    const orderId = uuidv4();
    const amount = parseFloat(item.price);
    const description = `${config.site.name} — ${item.name} для ${req.user.username}`;
    const returnUrl = `${config.site.frontendUrl}/profile?payment=success`;

    await query(
      'INSERT INTO foxaria_payments (id, player_uuid, player_name, item_id, item_name, amount, payment_method, status) VALUES (?, ?, ?, ?, ?, ?, ?, "pending")',
      [orderId, req.user.uuid, req.user.username, item.id, item.name, amount, method]
    );

    let paymentResult;
    switch (method) {
      case 'yookassa':
        paymentResult = await paymentsService.createYookassaPayment({ amount, description, orderId, returnUrl });
        break;
      case 'freekassa':
        paymentResult = paymentsService.createFreeKassaPayment({ amount, orderId, currency: 'RUB' });
        break;
      case 'robokassa':
        paymentResult = paymentsService.createRobokassaPayment({ amount, orderId, description });
        break;
      case 'cryptobot':
        paymentResult = await paymentsService.createCryptobotInvoice({ amount, orderId, description });
        break;
      case 'lava':
        paymentResult = await paymentsService.createLavaPayment({ amount, orderId, description });
        break;
      default:
        return res.status(400).json({ error: 'Неизвестный метод оплаты' });
    }

    await query('UPDATE foxaria_payments SET provider_id = ? WHERE id = ?', [paymentResult.paymentId || orderId, orderId]);

    res.json({ orderId, confirmationUrl: paymentResult.confirmationUrl, amount, currency: 'RUB' });
  } catch (err) {
    console.error('Payment create error:', err);
    res.status(500).json({ error: 'Ошибка создания платежа: ' + err.message });
  }
});

// GET /api/payments/status/:orderId
router.get('/status/:orderId', authMiddleware, async (req, res) => {
  try {
    const payment = await queryOne(
      'SELECT * FROM foxaria_payments WHERE id = ? AND player_uuid = ?',
      [req.params.orderId, req.user.uuid]
    );
    if (!payment) return res.status(404).json({ error: 'Платёж не найден' });
    res.json({ status: payment.status, amount: payment.amount, itemName: payment.item_name });
  } catch { res.status(500).json({ error: 'Ошибка' }); }
});

// Webhooks ────────────────────────────────────────────────────────
router.post('/webhook/yookassa', express.json(), async (req, res) => {
  try {
    const { object, event } = req.body;
    if (event !== 'payment.succeeded') return res.json({ status: 'ok' });
    const orderId = object.metadata?.orderId;
    if (orderId) await processSuccessfulPayment(orderId);
    res.json({ status: 'ok' });
  } catch (err) {
    console.error('YooKassa webhook error:', err);
    res.status(500).json({ error: 'Webhook error' });
  }
});

router.post('/webhook/freekassa', async (req, res) => {
  try {
    if (!paymentsService.verifyFreeKassaWebhook(req.body)) return res.status(400).send('Signature error');
    await processSuccessfulPayment(req.body.MERCHANT_ORDER_ID);
    res.send('YES');
  } catch { res.status(500).send('Error'); }
});

router.post('/webhook/robokassa', async (req, res) => {
  try {
    if (!paymentsService.verifyRobokassaWebhook(req.body)) return res.status(400).send('bad sign');
    await processSuccessfulPayment(req.body.InvId);
    res.send(`OK${req.body.InvId}`);
  } catch { res.status(500).send('Error'); }
});

router.post('/webhook/cryptobot', async (req, res) => {
  try {
    const sig = req.headers['crypto-pay-api-signature'];
    if (!paymentsService.verifyCryptobotWebhook(req.body, sig)) return res.status(400).json({ error: 'Invalid signature' });
    if (req.body.update_type === 'invoice_paid') {
      const orderId = req.body.payload?.payload;
      if (orderId) await processSuccessfulPayment(orderId);
    }
    res.json({ status: 'ok' });
  } catch { res.status(500).json({ error: 'Error' }); }
});

// Обработка успешного платежа ─────────────────────────────────────
async function processSuccessfulPayment(orderId) {
  const payment = await queryOne('SELECT * FROM foxaria_payments WHERE id = ? AND status = "pending"', [orderId]);
  if (!payment) return;

  const item = await queryOne('SELECT * FROM foxaria_shop_items WHERE id = ?', [payment.item_id]);

  await query("UPDATE foxaria_payments SET status = 'completed', completed_at = NOW() WHERE id = ?", [orderId]);

  if (!item?.commands) {
    console.log(`✅ Payment completed (no commands): ${orderId} — ${payment.player_name}`);
    return;
  }

  const rawCommands = JSON.parse(item.commands);
  if (!rawCommands.length) return;

  const serverId = item.server_id === 'all' ? config.servers[0]?.id : item.server_id;
  if (!serverId) return;

  const resolvedCommands = rawCommands.map(cmd =>
    cmd.replace(/{player}/g, payment.player_name)
       .replace(/{amount}/g, payment.amount)
       .replace(/{item}/g, item.name)
  );

  const proxyCmds = resolvedCommands.filter(isProxySideCommand);
  const gameCmds = resolvedCommands.filter((c) => !isProxySideCommand(c));

  let ok = true;
  let lastErr = '';
  if (proxyCmds.length) {
    const r = await executeBungeeCommands(proxyCmds);
    if (!r.success) {
      ok = false;
      lastErr = r.error || 'bungee';
    }
  }
  if (ok && gameCmds.length) {
    const r2 = await executeCommands(serverId, gameCmds);
    if (!r2.success) {
      ok = false;
      lastErr = r2.error || 'game';
    }
  }

  if (ok) {
    await query('UPDATE foxaria_payments SET commands_executed = 1 WHERE id = ?', [orderId]);
    console.log(`✅ Payment completed: ${orderId} — ${payment.player_name} (${item.name})`);
  } else {
    // Оба пути недоступны — кладём в очередь, выдадим когда сервер поднимется
    await query(
      'INSERT INTO foxaria_pending_commands (payment_id, player_name, server_id, commands) VALUES (?, ?, ?, ?)',
      [orderId, payment.player_name, serverId, JSON.stringify(resolvedCommands)]
    );
    console.log(`⏳ Commands queued for offline delivery: ${orderId} — ${payment.player_name} (${lastErr})`);
  }
}

// Фоновый retry — каждые 30 секунд пытается выдать из очереди
async function retryPendingCommands() {
  try {
    const rows = await query(
      'SELECT * FROM foxaria_pending_commands WHERE attempts < 10 ORDER BY created_at LIMIT 20'
    );
    for (const row of rows) {
      const commands = JSON.parse(row.commands);
      const proxyCmds = commands.filter(isProxySideCommand);
      const gameCmds = commands.filter((c) => !isProxySideCommand(c));
      let result = { success: true };
      if (proxyCmds.length) result = await executeBungeeCommands(proxyCmds);
      if (result.success && gameCmds.length) result = await executeCommands(row.server_id, gameCmds);
      if (result.success) {
        await query('DELETE FROM foxaria_pending_commands WHERE id = ?', [row.id]);
        await query('UPDATE foxaria_payments SET commands_executed = 1 WHERE id = ?', [row.payment_id]);
        console.log(`✅ Queued delivery success: ${row.player_name} (payment ${row.payment_id})`);
      } else {
        await query(
          "UPDATE foxaria_pending_commands SET attempts = attempts + 1, last_attempt_at = NOW() WHERE id = ?",
          [row.id]
        );
      }
    }
  } catch (err) {
    // фоновый retry не должен крашить сервер
  }
}

setInterval(retryPendingCommands, 30000);

module.exports = router;
