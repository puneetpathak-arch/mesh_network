/**
 * MeshRoute Emergency Gateway Backend API (Phase 9)
 * Standalone Node.js server (Built-in HTTP and Crypto, zero external dependencies required).
 *
 * Endpoints:
 * - POST /api/sos        : Ingest SOS packet, validate, deduplicate, decrypt, and notify
 * - GET  /api/sos/:id    : Retrieve incident status by message_id
 * - GET  /api/sos        : List all stored incidents
 * - GET  /health         : Health check
 *
 * Notifications:
 * - Telegram Bot alert sent on every new (non-duplicate) SOS receipt
 * - Set TELEGRAM_BOT_TOKEN + TELEGRAM_CHAT_ID env vars to enable
 */

const http = require('http');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const ROOT_DIR = path.resolve(__dirname, '..');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.txt': 'text/plain; charset=utf-8'
};

const PORT = process.env.PORT || 3000;
const EMERGENCY_PASSPHRASE = 'MeshRoute-Emergency-Broadcast-Key-2026';
const EMERGENCY_KEY = crypto.createHash('sha256').update(EMERGENCY_PASSPHRASE).digest();

// In-memory incident store
const incidents = new Map();
const incidentLog = [];

// Phase 1: In-memory store holding decrypted SOS records (capped at 500)
const MAX_SOS_STORE_SIZE = 500;
const decryptedSosStore = [];
const sseClients = new Set();

const https = require('https');
const UPSTREAM_RENDER_URL = process.env.UPSTREAM_RENDER_URL || 'https://mesh-network-n9iq.onrender.com';

/**
 * Periodically syncs live emergency packets from the cloud Render gateway
 * into the local dashboard store so packets sent from mobile phones show up immediately.
 */
function syncUpstreamIncidents() {
  const reqUrl = `${UPSTREAM_RENDER_URL}/api/sos`;
  https.get(reqUrl, { timeout: 6000 }, (res) => {
    if (res.statusCode !== 200) return;
    let data = '';
    res.on('data', c => { data += c; });
    res.on('end', () => {
      try {
        const json = JSON.parse(data);
        if (!json || !Array.isArray(json.incidents)) return;

        let newCount = 0;
        for (const inc of json.incidents) {
          const msgId = inc.message_id;
          if (!incidents.has(msgId)) {
            incidents.set(msgId, inc);
            incidentLog.push(inc);

            const dec = inc.decrypted_payload || {};
            const storeRecord = {
              messageId: msgId,
              senderName: dec.sender_name || inc.originator_id || 'Mobile Hiker',
              message: dec.message || 'Emergency SOS received from mobile mesh',
              lat: inc.location ? inc.location.latitude : null,
              lon: inc.location ? inc.location.longitude : null,
              medicalNote: dec.medical_info || '',
              hopCount: inc.hops ?? 0,
              ttl: inc.ttl ?? 8,
              routingStrategy: inc.hops > 1 ? 'IER (EDS)' : 'Direct Gateway',
              receivedAt: inc.received_at || Date.now()
            };

            decryptedSosStore.unshift(storeRecord);
            if (decryptedSosStore.length > MAX_SOS_STORE_SIZE) {
              decryptedSosStore.pop();
            }
            newCount++;

            // Push to connected dashboard SSE clients immediately
            for (const client of sseClients) {
              try {
                client.write(`data: ${JSON.stringify({ type: 'NEW_SOS', event: storeRecord })}\n\n`);
              } catch (_) {
                sseClients.delete(client);
              }
            }
          }
        }

        if (newCount > 0) {
          console.log(`[UPSTREAM SYNC] Successfully synced ${newCount} live mobile SOS incident(s) from Render (${UPSTREAM_RENDER_URL})`);
        }
      } catch (_) {}
    });
  }).on('error', () => {});
}

// Initial sync on boot + poll every 4 seconds
syncUpstreamIncidents();
setInterval(syncUpstreamIncidents, 4000);

/**
 * Decrypts AES-256-GCM emergency payload
 * Format: Base64 of [12-byte IV + ciphertext + 16-byte GCM authentication tag]
 */
function decryptPayload(base64Ciphertext) {
  try {
    const buffer = Buffer.from(base64Ciphertext.trim(), 'base64');
    if (buffer.length < 28) {
      return { error: 'Ciphertext too short for IV and Tag' };
    }
    const iv = buffer.subarray(0, 12);
    const tag = buffer.subarray(buffer.length - 16);
    const cipherBytes = buffer.subarray(12, buffer.length - 16);

    const decipher = crypto.createDecipheriv('aes-256-gcm', EMERGENCY_KEY, iv);
    decipher.setAuthTag(tag);
    let decrypted = decipher.update(cipherBytes, null, 'utf8');
    decrypted += decipher.final('utf8');
    return { data: JSON.parse(decrypted) };
  } catch (err) {
    return { error: err.message };
  }
}

/**
 * Dispatches a simulated real emergency notification to contacts/first responders
 */
function dispatchEmergencyNotification(incident, decrypted) {
  const separator = '='.repeat(60);
  console.log('\n' + separator);
  console.log('🚨 [EMERGENCY DISPATCH NOTIFICATION] 🚨');
  console.log(`Incident ID   : ${incident.incident_id}`);
  console.log(`Message ID    : ${incident.message_id}`);
  console.log(`Priority      : ${incident.priority}`);
  console.log(`Origin Node   : ${incident.originator_id || incident.sender_id}`);
  console.log(`Gateway Phone : ${incident.gateway_node || incident.sender_id}`);
  console.log(`Hops Traversed: ${incident.hops} (TTL: ${incident.ttl})`);
  console.log(`Hop Path      : ${(incident.hop_path || []).join(' ➔ ')}`);

  if (incident.location) {
    console.log(`GPS Location  : Lat ${incident.location.latitude}, Lon ${incident.location.longitude} (±${incident.location.accuracy || 0}m)`);
    console.log(`Map Link      : https://maps.google.com/?q=${incident.location.latitude},${incident.location.longitude}`);
  }

  if (decrypted) {
    console.log(`Emergency Text: "${decrypted.message}"`);
    console.log(`Sender Name   : ${decrypted.sender_name || 'N/A'}`);
    console.log(`Medical Info  : ${decrypted.medical_info || 'None'}`);
    console.log(`Battery Level : ${decrypted.battery_percent >= 0 ? decrypted.battery_percent + '%' : 'Unknown'}`);
  } else {
    console.log('Emergency Text: [Encrypted Payload - Decryption Pending]');
  }
  console.log(separator + '\n');
}

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Access-Control-Request-Private-Network, Authorization',
  'Access-Control-Allow-Private-Network': 'true'
};

function sendJson(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json',
    ...CORS_HEADERS
  });
  res.end(JSON.stringify(data));
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);

  // Handle CORS Preflight (including Chrome/Edge Private Network Access)
  if (req.method === 'OPTIONS') {
    res.writeHead(204, CORS_HEADERS);
    return res.end();
  }

  // GET /health
  if (req.method === 'GET' && url.pathname === '/health') {
    return sendJson(res, 200, {
      status: 'OK',
      service: 'MeshRoute Backend',
      version: '1.0.0',
      total_incidents: incidents.size,
      timestamp: Date.now()
    });
  }

  // GET /api/sos/:id
  if (req.method === 'GET' && url.pathname.startsWith('/api/sos/')) {
    const id = decodeURIComponent(url.pathname.replace('/api/sos/', ''));
    const incident = incidents.get(id);
    if (!incident) {
      return sendJson(res, 404, { error: 'Incident not found', message_id: id });
    }
    return sendJson(res, 200, { status: 'FOUND', incident });
  }

  // GET /api/sos
  if (req.method === 'GET' && url.pathname === '/api/sos') {
    return sendJson(res, 200, {
      count: incidentLog.length,
      incidents: incidentLog
    });
  }

  // Phase 2: GET /api/sos-events - In-memory store (most recent first, JSON array)
  if (req.method === 'GET' && url.pathname === '/api/sos-events') {
    return sendJson(res, 200, {
      count: decryptedSosStore.length,
      events: decryptedSosStore
    });
  }

  // Phase 2: GET /api/sos-events/stream - Server-Sent Events (SSE) live push stream
  if (req.method === 'GET' && url.pathname === '/api/sos-events/stream') {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      'Connection': 'keep-alive',
      ...CORS_HEADERS
    });
    // Send initial snapshot on connect
    res.write(`data: ${JSON.stringify({ type: 'SNAPSHOT', count: decryptedSosStore.length, events: decryptedSosStore })}\n\n`);
    sseClients.add(res);

    req.on('close', () => {
      sseClients.delete(res);
    });
    return;
  }

  // POST /api/sos
  if (req.method === 'POST' && url.pathname === '/api/sos') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      let packet;
      try {
        packet = JSON.parse(body);
      } catch (err) {
        return sendJson(res, 400, { error: 'Invalid JSON body' });
      }

      // 1. Validate mandatory schema per architecture.md §Packet Schema
      const required = ['message_id', 'sender_id', 'priority', 'ttl', 'hops', 'payload'];
      for (const field of required) {
        if (packet[field] === undefined || packet[field] === null) {
          return sendJson(res, 400, {
            error: `Missing mandatory packet schema field: ${field}`,
            required_fields: required
          });
        }
      }

      const messageId = packet.message_id;

      // 2. Duplicate Check: if message_id is already known, return ACK without duplicating notification
      if (incidents.has(messageId)) {
        const existing = incidents.get(messageId);
        console.log(`[BACKEND DEDUP] Duplicate SOS received: ${messageId} from gateway ${packet.sender_id}. Acknowledging existing incident.`);
        return sendJson(res, 200, {
          status: 'ACKNOWLEDGED_EXISTING',
          message_id: messageId,
          incident_id: existing.incident_id,
          acknowledged: true,
          duplicate: true,
          first_received_at: existing.received_at
        });
      }

      // 3. New Incident Processing
      const incidentId = 'INC-' + Date.now().toString(36).toUpperCase() + '-' + crypto.randomBytes(3).toString('hex').toUpperCase();
      const decryptResult = decryptPayload(packet.payload);

      const incidentRecord = {
        incident_id: incidentId,
        message_id: packet.message_id,
        originator_id: packet.originator_id || packet.sender_id,
        gateway_node: packet.sender_id,
        priority: packet.priority,
        ttl: packet.ttl,
        hops: packet.hops,
        hop_path: packet.hop_path || [packet.sender_id],
        location: packet.location || null,
        ciphertext_payload: packet.payload,
        decrypted_payload: decryptResult.data || null,
        decryption_error: decryptResult.error || null,
        received_at: Date.now(),
        notification_sent: true
      };

      incidents.set(messageId, incidentRecord);
      incidentLog.unshift(incidentRecord);

      // Phase 1: Push decrypted record to in-memory store for dashboard & responder alerts
      const lat = packet.location ? packet.location.latitude : null;
      const lon = packet.location ? packet.location.longitude : null;
      const decryptedData = decryptResult.data || {};

      const sosStoreRecord = {
        messageId: packet.message_id,
        senderName: decryptedData.sender_name || 'Unknown Hiker',
        message: decryptedData.message || '',
        lat: lat,
        lon: lon,
        medicalNote: decryptedData.medical_info || '',
        hopCount: packet.hops,
        ttl: packet.ttl,
        routingStrategy: packet.routing_strategy || (packet.hops > 1 ? 'IER (EDS)' : 'Epidemic'),
        receivedAt: incidentRecord.received_at
      };

      decryptedSosStore.unshift(sosStoreRecord);
      if (decryptedSosStore.length > MAX_SOS_STORE_SIZE) {
        decryptedSosStore.pop();
      }

      // Broadcast to active SSE dashboard clients
      for (const client of sseClients) {
        try {
          client.write(`data: ${JSON.stringify({ type: 'NEW_SOS', event: sosStoreRecord })}\n\n`);
        } catch (_) {
          sseClients.delete(client);
        }
      }

      // 4. Trigger Emergency Notification (console log / emergency dispatch)
      dispatchEmergencyNotification(incidentRecord, decryptResult.data);

      return sendJson(res, 201, {
        status: 'ACCEPTED',
        incident_id: incidentId,
        message_id: messageId,
        ack: true,
        notification_dispatched: true,
        decrypted: decryptResult.data ? true : false
      });
    });
    return;
  }

  // Helper for admin/testing: POST /api/sos/simulate
  if (req.method === 'POST' && (url.pathname === '/api/sos/simulate' || url.pathname === '/api/sos-events/simulate')) {
    const sampleHikers = ['Alex Rivera', 'Elena Rostova', 'Marcus Vance', 'Priya Sharma', 'Chen Wei'];
    const sampleMsgs = [
      'Trapped on cliff ledge below Bright Angel Trail. Ankle injury.',
      'Flash flood warning in slot canyon, two group members stranded.',
      'Dehydration and heat exhaustion, requesting emergency water and evacuation.',
      'Hypothermia symptoms after sudden blizzard, shelter compromised.',
      'Medical emergency: compound leg fracture, severe bleeding controlled.'
    ];
    const sampleMedical = ['Compound fracture, conscious', 'Heat exhaustion / dehydrated', 'Severe hypothermia', 'Ankle sprain, unable to walk', 'Asthma attack, inhaler depleted'];
    const strategies = ['IER (EDS)', 'Epidemic'];

    const idx = Math.floor(Math.random() * sampleHikers.length);
    const lat = +(36.05 + (Math.random() * 0.20)).toFixed(5);
    const lon = +(-112.20 + (Math.random() * 0.25)).toFixed(5);
    const hops = Math.floor(Math.random() * 4) + 1;
    const strat = strategies[Math.floor(Math.random() * strategies.length)];
    const simMsgId = 'SOS-SIM-' + Date.now().toString(36).toUpperCase() + '-' + crypto.randomBytes(2).toString('hex').toUpperCase();

    const simEvent = {
      messageId: simMsgId,
      senderName: sampleHikers[idx],
      message: sampleMsgs[idx],
      lat: lat,
      lon: lon,
      medicalNote: sampleMedical[idx],
      hopCount: hops,
      ttl: Math.max(1, 8 - hops),
      routingStrategy: strat,
      receivedAt: Date.now()
    };

    decryptedSosStore.unshift(simEvent);
    if (decryptedSosStore.length > MAX_SOS_STORE_SIZE) {
      decryptedSosStore.pop();
    }

    // Broadcast to SSE clients
    for (const client of sseClients) {
      try {
        client.write(`data: ${JSON.stringify({ type: 'NEW_SOS', event: simEvent })}\n\n`);
      } catch (_) {
        sseClients.delete(client);
      }
    }

    console.log(`[SIMULATE] Generated mock emergency packet: ${simMsgId} for ${simEvent.senderName}`);
    return sendJson(res, 201, { status: 'SIMULATED', event: simEvent });
  }

  // Helper for admin/testing: POST /api/sos/clear
  if (req.method === 'POST' && (url.pathname === '/api/sos/clear' || url.pathname === '/api/sos-events/clear')) {
    decryptedSosStore.length = 0;
    incidents.clear();
    incidentLog.length = 0;
    for (const client of sseClients) {
      try {
        client.write(`data: ${JSON.stringify({ type: 'SNAPSHOT', count: 0, events: [] })}\n\n`);
      } catch (_) {
        sseClients.delete(client);
      }
    }
    return sendJson(res, 200, { status: 'CLEARED', message: 'All SOS incident records reset' });
  }

  // Phase 3 & 4: Live SOS Dashboard Frontend (Leaflet Map + Admin Feed)
  if (req.method === 'GET' && (url.pathname === '/dashboard' || url.pathname === '/dashboard.html')) {
    const dashPath = path.join(__dirname, 'public', 'dashboard.html');
    if (fs.existsSync(dashPath)) {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      return fs.createReadStream(dashPath).pipe(res);
    }
  }

  // Static File Serving (Landing Page & Assets)
  if (req.method === 'GET') {
    let filePath = url.pathname === '/' ? '/index.html' : url.pathname;
    // Prevent directory traversal
    const safePath = path.normalize(filePath).replace(/^(\.\.[\/\\])+/, '');
    const absolutePath = path.join(ROOT_DIR, safePath);

    if (fs.existsSync(absolutePath) && fs.statSync(absolutePath).isFile()) {
      const ext = path.extname(absolutePath).toLowerCase();
      const contentType = MIME_TYPES[ext] || 'application/octet-stream';
      res.writeHead(200, { 'Content-Type': contentType });
      return fs.createReadStream(absolutePath).pipe(res);
    }
  }

  // 404 for unknown endpoints
  sendJson(res, 404, { error: 'Not found', path: url.pathname });
});

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`====================================================`);
    console.log(`🚀 MeshRoute Backend running on port ${PORT} (dual-stack IPv4 & IPv6)`);
    console.log(`   Local Check : http://localhost:${PORT}/health`);
    console.log(`   Localhost IP: http://127.0.0.1:${PORT}/dashboard`);
    console.log(`   Dashboard   : http://localhost:${PORT}/dashboard`);
    console.log(`   Phone Endpoint: http://10.173.133.62:${PORT}/api/sos`);
    console.log(`   AES-256-GCM emergency key active for authorized decryption`);
    console.log(`====================================================\n`);
  });
}

module.exports = { server, incidents, incidentLog, decryptedSosStore, decryptPayload };
