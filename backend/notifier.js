/**
 * MeshRoute SOS Notifier
 * Sends a Telegram alert whenever a new SOS incident is received.
 *
 * Required env vars:
 *   TELEGRAM_BOT_TOKEN  — from @BotFather on Telegram
 *   TELEGRAM_CHAT_ID    — from @userinfobot on Telegram
 *
 * Design:
 *  - Zero external dependencies (uses Node built-in https)
 *  - Non-blocking: always resolves, never throws
 *  - Retries once after 2 seconds on failure
 *  - Skips gracefully if env vars are not configured
 */

const https = require('https');

const TELEGRAM_BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN || '';
const TELEGRAM_CHAT_ID   = process.env.TELEGRAM_CHAT_ID   || '';

// Secondary dedup: in-memory set of notified message_ids with expiry (10 min)
// Guards against re-notification after Render cold restarts within a short window.
const _notifiedIds = new Map(); // message_id -> timestamp
const DEDUP_WINDOW_MS = 10 * 60 * 1000; // 10 minutes

function _isRecentlyNotified(messageId) {
  const ts = _notifiedIds.get(messageId);
  if (!ts) return false;
  if (Date.now() - ts > DEDUP_WINDOW_MS) {
    _notifiedIds.delete(messageId);
    return false;
  }
  return true;
}

function _markNotified(messageId) {
  _notifiedIds.set(messageId, Date.now());
  // Cleanup old entries to avoid memory leak on long-running instances
  if (_notifiedIds.size > 1000) {
    const cutoff = Date.now() - DEDUP_WINDOW_MS;
    for (const [id, ts] of _notifiedIds) {
      if (ts < cutoff) _notifiedIds.delete(id);
    }
  }
}

/**
 * Formats a rich Telegram message for a new SOS incident.
 */
function _formatMessage(incident, decrypted) {
  const ts = new Date(incident.received_at).toISOString().replace('T', ' ').slice(0, 16) + ' UTC';
  const hopPath = (incident.hop_path || []).join(' ➔ ');

  let lines = [
    `🚨 *MESHROUTE SOS ALERT* 🚨`,
    ``,
    `📋 *Incident:* \`${incident.incident_id}\``,
    `🆔 *Message ID:* \`${incident.message_id}\``,
    `⚡ *Priority:* ${incident.priority}`,
    `📱 *Origin Device:* ${incident.originator_id || incident.gateway_node}`,
    `🔀 *Hops:* ${incident.hops} (TTL: ${incident.ttl})`,
    `🛤️ *Path:* ${hopPath || 'N/A'}`,
  ];

  if (incident.location) {
    const { latitude, longitude, accuracy } = incident.location;
    lines.push(`📍 *Location:* ${latitude}, ${longitude} (±${accuracy || 0}m)`);
    lines.push(`🗺️ *Map:* https://maps.google.com/?q=${latitude},${longitude}`);
  } else {
    lines.push(`📍 *Location:* Not available`);
  }

  if (decrypted) {
    if (decrypted.message)       lines.push(`💬 *Message:* "${decrypted.message}"`);
    if (decrypted.sender_name)   lines.push(`👤 *Sender:* ${decrypted.sender_name}`);
    if (decrypted.medical_info)  lines.push(`🏥 *Medical:* ${decrypted.medical_info}`);
    if (decrypted.battery_percent >= 0) lines.push(`🔋 *Battery:* ${decrypted.battery_percent}%`);
  } else {
    lines.push(`🔒 *Payload:* Encrypted (decryption failed)`);
  }

  lines.push(`🕐 *Received:* ${ts}`);

  return lines.join('\n');
}

/**
 * Makes a single HTTPS POST to the Telegram Bot API.
 * Returns a Promise<{ ok, statusCode, body }>.
 */
function _telegramPost(text) {
  return new Promise((resolve) => {
    const payload = JSON.stringify({
      chat_id: TELEGRAM_CHAT_ID,
      text,
      parse_mode: 'Markdown',
      disable_web_page_preview: false
    });

    const options = {
      hostname: 'api.telegram.org',
      path: `/bot${TELEGRAM_BOT_TOKEN}/sendMessage`,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload)
      }
    };

    const req = https.request(options, (res) => {
      let data = '';
      res.on('data', chunk => { data += chunk; });
      res.on('end', () => {
        try {
          resolve({ ok: res.statusCode === 200, statusCode: res.statusCode, body: JSON.parse(data) });
        } catch {
          resolve({ ok: false, statusCode: res.statusCode, body: data });
        }
      });
    });

    req.on('error', (err) => resolve({ ok: false, statusCode: 0, body: err.message }));
    req.setTimeout(8000, () => { req.destroy(); resolve({ ok: false, statusCode: 0, body: 'Request timeout' }); });
    req.write(payload);
    req.end();
  });
}

/**
 * Public API: fire-and-forget SOS alert via Telegram.
 * Non-blocking — the caller does NOT need to await this.
 * All errors are caught and logged; the incident is always saved regardless.
 *
 * @param {object} incident - The full incident record
 * @param {object|null} decrypted - Decrypted payload (or null)
 */
async function sendTelegramAlert(incident, decrypted) {
  // Guard: env vars not configured
  if (!TELEGRAM_BOT_TOKEN || !TELEGRAM_CHAT_ID) {
    console.warn('[NOTIFIER] TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID not set — skipping Telegram alert.');
    return;
  }

  // Secondary dedup guard (in addition to the Map in server.js)
  if (_isRecentlyNotified(incident.message_id)) {
    console.log(`[NOTIFIER] Dedup: alert for ${incident.message_id} already sent recently. Skipping.`);
    return;
  }

  const text = _formatMessage(incident, decrypted);

  // Attempt 1
  let result = await _telegramPost(text);

  if (result.ok) {
    _markNotified(incident.message_id);
    console.log(`[NOTIFIER] ✅ Telegram alert sent for incident ${incident.incident_id}`);
    return;
  }

  // Retry once after 2 seconds
  console.warn(`[NOTIFIER] ⚠️ First Telegram attempt failed (${result.statusCode}: ${JSON.stringify(result.body)}). Retrying in 2s...`);
  await new Promise(r => setTimeout(r, 2000));
  result = await _telegramPost(text);

  if (result.ok) {
    _markNotified(incident.message_id);
    console.log(`[NOTIFIER] ✅ Telegram alert sent (retry) for incident ${incident.incident_id}`);
  } else {
    console.error(`[NOTIFIER] ❌ Telegram alert FAILED after retry for ${incident.incident_id}: ${JSON.stringify(result.body)}`);
    // Incident is already saved — notification failure does NOT affect SOS processing
  }
}

module.exports = { sendTelegramAlert };
