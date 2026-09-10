/**
 * MeshRoute Emergency Gateway Backend API (Phase 9)
 * Standalone Node.js server (Built-in HTTP and Crypto, zero external dependencies required).
 *
 * Endpoints:
 * - POST /api/sos        : Ingest SOS packet, validate, deduplicate, decrypt, and notify
 * - GET  /api/sos/:id    : Retrieve incident status by message_id
 * - GET  /api/sos        : List all stored incidents
 * - GET  /health         : Health check
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

function sendJson(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type'
  });
  res.end(JSON.stringify(data));
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);

  // Handle CORS Preflight
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type'
    });
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

      // 4. Trigger Emergency Notification
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
  server.listen(PORT, '0.0.0.0', () => {
    console.log(`====================================================`);
    console.log(`🚀 MeshRoute Backend running on port ${PORT} (0.0.0.0)`);
    console.log(`   Local Check : http://localhost:${PORT}/health`);
    console.log(`   Phone Endpoint: http://10.173.133.62:${PORT}/api/sos`);
    console.log(`   AES-256-GCM emergency key active for authorized decryption`);
    console.log(`====================================================\n`);
  });
}

module.exports = { server, incidents, decryptPayload };
