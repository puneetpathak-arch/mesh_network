/**
 * MeshRoute Notification End-to-End Test
 *
 * Tests that a simulated SOS packet triggers a real Telegram notification.
 *
 * Usage:
 *   # Test locally (starts a local server):
 *   node backend/test_notify.js
 *
 *   # Test against the live Render deployment:
 *   node backend/test_notify.js --remote
 *
 * Required env vars for Telegram delivery:
 *   TELEGRAM_BOT_TOKEN=<your bot token>
 *   TELEGRAM_CHAT_ID=<your chat id>
 */

const http  = require('http');
const https = require('https');
const crypto = require('crypto');

const PASSPHRASE   = 'MeshRoute-Emergency-Broadcast-Key-2026';
const KEY          = crypto.createHash('sha256').update(PASSPHRASE).digest();
const REMOTE_URL   = 'https://mesh-network-n9iq.onrender.com';
const LOCAL_PORT   = 4567;
const USE_REMOTE   = process.argv.includes('--remote');

// ─── Helpers ─────────────────────────────────────────────────────────────────

function encryptPayload(obj) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', KEY, iv);
  let enc = cipher.update(JSON.stringify(obj), 'utf8');
  enc = Buffer.concat([enc, cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, enc, tag]).toString('base64');
}

function post(url, body) {
  return new Promise((resolve, reject) => {
    const data    = JSON.stringify(body);
    const parsed  = new URL(url);
    const lib     = parsed.protocol === 'https:' ? https : http;
    const options = {
      hostname: parsed.hostname,
      port:     parsed.port || (parsed.protocol === 'https:' ? 443 : 80),
      path:     parsed.pathname,
      method:   'POST',
      headers:  {
        'Content-Type':   'application/json',
        'Content-Length': Buffer.byteLength(data)
      }
    };

    const req = lib.request(options, res => {
      let raw = '';
      res.on('data', c => { raw += c; });
      res.on('end', () => {
        try { resolve({ statusCode: res.statusCode, body: JSON.parse(raw) }); }
        catch { resolve({ statusCode: res.statusCode, body: raw }); }
      });
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

// ─── Main ─────────────────────────────────────────────────────────────────────

async function run() {
  console.log('\n🧪 MeshRoute Notification End-to-End Test');
  console.log('==========================================');

  let server = null;
  let baseUrl;

  if (USE_REMOTE) {
    baseUrl = REMOTE_URL;
    console.log(`🌐 Mode: REMOTE → ${baseUrl}\n`);
  } else {
    // Start embedded local server
    const { server: srv } = require('./server');
    server = srv;
    await new Promise(resolve => server.listen(LOCAL_PORT, '127.0.0.1', resolve));
    baseUrl = `http://127.0.0.1:${LOCAL_PORT}`;
    console.log(`🏠 Mode: LOCAL  → ${baseUrl}\n`);
  }

  // Check env vars
  const hasToken   = !!process.env.TELEGRAM_BOT_TOKEN;
  const hasChatId  = !!process.env.TELEGRAM_CHAT_ID;
  if (!hasToken || !hasChatId) {
    console.warn('⚠️  WARNING: TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID not set in environment.');
    console.warn('   The SOS will be saved but NO Telegram message will be sent.\n');
  } else {
    console.log('✅ Telegram credentials detected in environment.\n');
  }

  // Build a realistic test SOS packet
  const messageId = `SOS-NOTIFY-TEST-${Date.now()}`;
  const ciphertext = encryptPayload({
    message:         'NOTIFICATION TEST — This is a simulated SOS from test_notify.js',
    sender_name:     'MeshRoute Dev',
    medical_info:    'None — this is a test',
    battery_percent: 88
  });

  const packet = {
    message_id:    messageId,
    sender_id:     'GATEWAY-TEST-01',
    originator_id: 'TEST-NODE-001',
    timestamp:     Date.now(),
    priority:      'SOS',
    ttl:           8,
    hops:          2,
    payload:       ciphertext,
    hop_path:      ['TEST-NODE-001', 'TEST-RELAY-002', 'GATEWAY-TEST-01'],
    location: {
      latitude:  28.6139,   // New Delhi (test coords)
      longitude: 77.2090,
      accuracy:  10.0
    }
  };

  console.log(`📤 Sending test SOS packet: ${messageId}`);
  console.log(`   POST ${baseUrl}/api/sos`);

  try {
    const res = await post(`${baseUrl}/api/sos`, packet);

    if (res.statusCode === 201 && res.body.status === 'ACCEPTED') {
      console.log(`\n✅ SOS ACCEPTED by backend`);
      console.log(`   Incident ID : ${res.body.incident_id}`);
      console.log(`   Message ID  : ${res.body.message_id}`);
      console.log(`   Decrypted   : ${res.body.decrypted}`);
    } else {
      console.error(`\n❌ Unexpected response: HTTP ${res.statusCode}`);
      console.error(JSON.stringify(res.body, null, 2));
      process.exit(1);
    }
  } catch (err) {
    console.error(`\n❌ Request failed: ${err.message}`);
    process.exit(1);
  }

  // Give the async notifier time to complete
  console.log('\n⏳ Waiting 4 seconds for Telegram delivery...');
  await new Promise(r => setTimeout(r, 4000));

  if (hasToken && hasChatId) {
    console.log('\n📱 Check your Telegram app — you should have received an SOS alert!');
    console.log('   If not, verify TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID are correct.');
  } else {
    console.log('\nℹ️  Set TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID to receive a real alert.');
    console.log('   Example:');
    console.log('   TELEGRAM_BOT_TOKEN=123:abc TELEGRAM_CHAT_ID=987654 node backend/test_notify.js');
  }

  console.log('\n✅ Test complete.\n');

  if (server) server.close();
}

run().catch(err => {
  console.error('Fatal error:', err);
  process.exit(1);
});
