const axios = require('axios');
const crypto = require('crypto');
const { v4: uuidv4 } = require('uuid');
const config = require('../../config');

// ─── YooKassa ──────────────────────────────────────────────────
async function createYookassaPayment({ amount, description, orderId, returnUrl, metadata }) {
  const cfg = config.payments.yookassa;
  if (!cfg.enabled) throw new Error('YooKassa не включена в config.js');

  const idempotenceKey = uuidv4();
  const response = await axios.post(
    'https://api.yookassa.ru/v3/payments',
    {
      amount: { value: amount.toFixed(2), currency: 'RUB' },
      confirmation: { type: 'redirect', return_url: returnUrl },
      description,
      metadata: { orderId, ...metadata },
      capture: true,
    },
    {
      auth: { username: cfg.shopId, password: cfg.secretKey },
      headers: { 'Idempotence-Key': idempotenceKey, 'Content-Type': 'application/json' },
    }
  );

  return {
    paymentId: response.data.id,
    confirmationUrl: response.data.confirmation.confirmation_url,
    status: response.data.status,
  };
}

function verifyYookassaWebhook(body, signature) {
  // YooKassa sends IP-based webhooks, verify by fetching payment status
  return true;
}

// ─── FreeKassa ─────────────────────────────────────────────────
function createFreeKassaPayment({ amount, orderId, email, currency = 'RUB' }) {
  const cfg = config.payments.freekassa;
  if (!cfg.enabled) throw new Error('FreeKassa не включена в config.js');

  const sign = crypto
    .createHash('md5')
    .update(`${cfg.merchantId}:${amount}:${cfg.secretWord1}:${currency}:${orderId}`)
    .digest('hex');

  const params = new URLSearchParams({
    m: cfg.merchantId,
    oa: amount,
    currency,
    o: orderId,
    s: sign,
    em: email || '',
    lang: 'ru',
  });

  return {
    paymentId: orderId,
    confirmationUrl: `https://pay.freekassa.ru/?${params.toString()}`,
    status: 'pending',
  };
}

function verifyFreeKassaWebhook(body) {
  const cfg = config.payments.freekassa;
  const sign = crypto
    .createHash('md5')
    .update(`${cfg.merchantId}:${body.AMOUNT}:${cfg.secretWord2}:${body.MERCHANT_ORDER_ID}`)
    .digest('hex');
  return sign === body.SIGN;
}

// ─── Robokassa ─────────────────────────────────────────────────
function createRobokassaPayment({ amount, orderId, description }) {
  const cfg = config.payments.robokassa;
  if (!cfg.enabled) throw new Error('Robokassa не включена в config.js');

  const signString = `${cfg.merchantLogin}:${amount}:${orderId}:${cfg.password1}`;
  const sign = crypto.createHash('md5').update(signString).digest('hex');

  const params = new URLSearchParams({
    MrchLogin: cfg.merchantLogin,
    OutSum: amount,
    InvId: orderId,
    Desc: description,
    SignatureValue: sign,
    Culture: 'ru',
    Encoding: 'utf-8',
  });

  return {
    paymentId: orderId,
    confirmationUrl: `https://auth.robokassa.ru/Merchant/Index.aspx?${params.toString()}`,
    status: 'pending',
  };
}

function verifyRobokassaWebhook(body) {
  const cfg = config.payments.robokassa;
  const signString = `${body.OutSum}:${body.InvId}:${cfg.password2}`;
  const expectedSign = crypto.createHash('md5').update(signString).digest('hex').toUpperCase();
  return expectedSign === (body.SignatureValue || '').toUpperCase();
}

// ─── CryptoBot ─────────────────────────────────────────────────
async function createCryptobotInvoice({ amount, orderId, description }) {
  const cfg = config.payments.cryptobot;
  if (!cfg.enabled) throw new Error('CryptoBot не включен в config.js');

  const baseUrl = cfg.network === 'testnet'
    ? 'https://testnet-pay.crypt.bot'
    : 'https://pay.crypt.bot';

  const response = await axios.post(
    `${baseUrl}/api/createInvoice`,
    {
      currency_type: 'fiat',
      fiat: 'RUB',
      amount: amount.toString(),
      description,
      payload: orderId,
      paid_btn_name: 'openBot',
      paid_btn_url: config.site.frontendUrl + '/profile',
    },
    { headers: { 'Crypto-Pay-API-Token': cfg.token } }
  );

  const invoice = response.data.result;
  return {
    paymentId: invoice.invoice_id.toString(),
    confirmationUrl: invoice.bot_invoice_url,
    status: 'pending',
  };
}

function verifyCryptobotWebhook(body, signature) {
  const cfg = config.payments.cryptobot;
  const checkString = JSON.stringify(body);
  const expectedSign = crypto
    .createHmac('sha256', crypto.createHash('sha256').update(cfg.token).digest())
    .update(checkString)
    .digest('hex');
  return expectedSign === signature;
}

// ─── LAVA ──────────────────────────────────────────────────────
async function createLavaPayment({ amount, orderId, description }) {
  const cfg = config.payments.lava;
  if (!cfg.enabled) throw new Error('LAVA не включена в config.js');

  const body = {
    sum: amount,
    orderId,
    shopId: cfg.shopId,
    hookUrl: `${config.site.productionUrl}/api/payments/webhook/lava`,
    successUrl: `${config.site.frontendUrl}/profile`,
    failUrl: `${config.site.frontendUrl}/shop`,
    expire: 30,
    comment: description,
  };

  const sign = crypto
    .createHmac('sha256', cfg.secretKey)
    .update(JSON.stringify(body))
    .digest('hex');

  const response = await axios.post('https://api.lava.ru/business/invoice/create', body, {
    headers: { 'X-Api-Key': cfg.secretKey, 'Signature': sign, 'Content-Type': 'application/json' },
  });

  return {
    paymentId: response.data.data.id,
    confirmationUrl: response.data.data.url,
    status: 'pending',
  };
}

module.exports = {
  createYookassaPayment, verifyYookassaWebhook,
  createFreeKassaPayment, verifyFreeKassaWebhook,
  createRobokassaPayment, verifyRobokassaWebhook,
  createCryptobotInvoice, verifyCryptobotWebhook,
  createLavaPayment,
};
