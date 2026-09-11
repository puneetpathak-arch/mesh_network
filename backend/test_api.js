/**
 * MeshRoute Backend Automated Validation Suite (Phase 9)
 * Tests all 5 layers defined in testing-guide.md §Phase 9:
 * 1. Direct API Ingestion
 * 2. Duplicate Suppression & Idempotent ACK
 * 3. Malformed Packet Validation Error
 * 4. Stored Incident Retrieval with Decrypted Emergency Data
 * 5. Concurrent Submissions Handling
 */

const http = require('http');
const crypto = require('crypto');
const { server, decryptedSosStore } = require('./server');

const TEST_PORT = 3999;
const PASSPHRASE = 'MeshRoute-Emergency-Broadcast-Key-2026';
const KEY = crypto.createHash('sha256').update(PASSPHRASE).digest();

function encryptEmergencyPayload(plaintextObj) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', KEY, iv);
  const jsonStr = JSON.stringify(plaintextObj);
  let enc = cipher.update(jsonStr, 'utf8');
  enc = Buffer.concat([enc, cipher.final()]);
  const tag = cipher.getAuthTag();
  const combined = Buffer.concat([iv, enc, tag]);
  return combined.toString('base64');
}

function request(method, path, body = null) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const req = http.request({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: path,
      method: method,
      headers: data ? {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data)
      } : {}
    }, res => {
      let resData = '';
      res.on('data', chunk => { resData += chunk; });
      res.on('end', () => {
        resolve({
          statusCode: res.statusCode,
          body: runCatchingJson(resData)
        });
      });
    });
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

function runCatchingJson(str) {
  try { return JSON.parse(str); } catch { return str; }
}

function assert(condition, message) {
  if (!condition) {
    console.error(`❌ FAILED: ${message}`);
    process.exit(1);
  } else {
    console.log(`  ✓ ${message}`);
  }
}

async function runTests() {
  console.log('🧪 Starting Phase 9 Backend Test Suite...\n');

  await new Promise(resolve => server.listen(TEST_PORT, resolve));

  try {
    // -------------------------------------------------------------
    // Test 1: Health Check
    // -------------------------------------------------------------
    console.log('[Test 1] Health Check Endpoint');
    const health = await request('GET', '/health');
    assert(health.statusCode === 200, 'GET /health returns 200 OK');
    assert(health.body.status === 'OK', 'Response contains status OK');

    // -------------------------------------------------------------
    // Test 2: Valid SOS Ingestion (Layer 1)
    // -------------------------------------------------------------
    console.log('\n[Test 2] Layer 1: Valid SOS Packet Submission');
    const emergencyPayload = {
      message: 'Trapped in ravine, 2 injured, medical supplies urgent',
      sender_name: 'Test Hiker A',
      medical_info: 'Fractured rib',
      battery_percent: 42
    };
    const ciphertext = encryptEmergencyPayload(emergencyPayload);

    const testPacket = {
      message_id: 'SOS-TEST-' + Date.now(),
      sender_id: 'MR-GATEWAY-01',
      originator_id: 'MR-NODE-A',
      timestamp: Date.now(),
      location: {
        latitude: 36.1069,
        longitude: -112.1129,
        accuracy: 4.0
      },
      priority: 'SOS',
      ttl: 8,
      hops: 2,
      payload: ciphertext,
      hop_path: ['MR-NODE-A', 'MR-RELAY-B', 'MR-GATEWAY-01']
    };

    const res1 = await request('POST', '/api/sos', testPacket);
    assert(res1.statusCode === 201, 'POST /api/sos returns 201 Created');
    assert(res1.body.status === 'ACCEPTED', 'Response status is ACCEPTED');
    assert(res1.body.ack === true, 'Response acknowledges receipt to gateway');
    assert(res1.body.incident_id !== undefined, 'Incident ID is generated');
    const incidentId = res1.body.incident_id;

    // Phase 1 Store Verification
    const storeRecord = decryptedSosStore.find(r => r.messageId === testPacket.message_id);
    assert(storeRecord !== undefined, 'Phase 1: SOS lands in decryptedSosStore correctly');
    assert(storeRecord.senderName === 'Test Hiker A', 'Phase 1: storeRecord.senderName matches');
    assert(storeRecord.message === 'Trapped in ravine, 2 injured, medical supplies urgent', 'Phase 1: storeRecord.message matches');
    assert(storeRecord.lat === 36.1069 && storeRecord.lon === -112.1129, 'Phase 1: storeRecord coordinates match');
    assert(storeRecord.medicalNote === 'Fractured rib', 'Phase 1: storeRecord.medicalNote matches');
    assert(storeRecord.hopCount === 2, 'Phase 1: storeRecord.hopCount matches');
    assert(storeRecord.ttl === 8, 'Phase 1: storeRecord.ttl matches');
    assert(typeof storeRecord.receivedAt === 'number', 'Phase 1: storeRecord.receivedAt timestamp is valid');

    // -------------------------------------------------------------
    // Test 3: Duplicate Suppression (Layer 2)
    // -------------------------------------------------------------
    console.log('\n[Test 3] Layer 2: Duplicate Suppression & Idempotency');
    const resDuplicate = await request('POST', '/api/sos', testPacket);
    assert(resDuplicate.statusCode === 200, 'POST duplicate returns 200 OK');
    assert(resDuplicate.body.status === 'ACKNOWLEDGED_EXISTING', 'Duplicate status is ACKNOWLEDGED_EXISTING');
    assert(resDuplicate.body.duplicate === true, 'Flagged duplicate is true');
    assert(resDuplicate.body.incident_id === incidentId, 'Matches original incident ID');

    // -------------------------------------------------------------
    // Test 4: Malformed Packet Rejection (Layer 3)
    // -------------------------------------------------------------
    console.log('\n[Test 4] Layer 3: Malformed Packet Validation');
    const badPacket = { message_id: 'BAD-123' }; // missing required fields
    const resBad = await request('POST', '/api/sos', badPacket);
    assert(resBad.statusCode === 400, 'Malformed packet returns 400 Bad Request');
    assert(resBad.body.error !== undefined, 'Clear validation error returned');

    // -------------------------------------------------------------
    // Test 5: Incident Retrieval & Decryption Verification (Layer 4)
    // -------------------------------------------------------------
    console.log('\n[Test 5] Layer 4: Incident Query & Authorized Decryption Verification');
    const resQuery = await request('GET', `/api/sos/${testPacket.message_id}`);
    assert(resQuery.statusCode === 200, 'GET /api/sos/:id returns 200 OK');
    const incident = resQuery.body.incident;
    assert(incident.message_id === testPacket.message_id, 'Message ID matches');
    assert(incident.location.latitude === 36.1069, 'GPS coordinates match');
    assert(incident.decrypted_payload !== null, 'Emergency payload was decrypted successfully');
    assert(incident.decrypted_payload.message === emergencyPayload.message, 'Decrypted message matches original');
    assert(incident.decrypted_payload.medical_info === 'Fractured rib', 'Decrypted medical info matches');

    // -------------------------------------------------------------
    // Test 6: Concurrent Race Condition (Layer 5)
    // -------------------------------------------------------------
    console.log('\n[Test 6] Layer 5: Concurrent Requests Handling');
    const racePacket = {
      ...testPacket,
      message_id: 'SOS-RACE-' + Date.now()
    };
    const [raceRes1, raceRes2] = await Promise.all([
      request('POST', '/api/sos', racePacket),
      request('POST', '/api/sos', racePacket)
    ]);
    const codes = [raceRes1.statusCode, raceRes2.statusCode].sort();
    assert(codes[0] === 200 && codes[1] === 201, 'One request created incident (201) and concurrent duplicate acknowledged (200)');

    // -------------------------------------------------------------
    // Test 7: Phase 2 Dashboard Data Endpoint (GET /api/sos-events)
    // -------------------------------------------------------------
    console.log('\n[Test 7] Phase 2: Dashboard Data Endpoint (/api/sos-events)');
    const resEvents = await request('GET', '/api/sos-events');
    assert(resEvents.statusCode === 200, 'GET /api/sos-events returns 200 OK');
    assert(Array.isArray(resEvents.body.events), 'Response contains array of events');
    assert(resEvents.body.count === resEvents.body.events.length, 'Event count matches array length');
    assert(resEvents.body.events.length >= 2, 'Contains ingested SOS events');
    const firstEvent = resEvents.body.events[0];
    assert(firstEvent.messageId !== undefined, 'Event record has messageId');
    assert(firstEvent.senderName !== undefined, 'Event record has senderName');
    assert(firstEvent.lat !== undefined && firstEvent.lon !== undefined, 'Event record has coordinates');
    assert(firstEvent.medicalNote !== undefined, 'Event record has medicalNote');
    assert(firstEvent.hopCount !== undefined, 'Event record has hopCount');
    assert(firstEvent.ttl !== undefined, 'Event record has ttl');
    assert(firstEvent.receivedAt !== undefined, 'Event record has receivedAt');
    assert(resEvents.body.events[0].receivedAt >= resEvents.body.events[1].receivedAt, 'Events are ordered most recent first');

    console.log('\n🎉 ALL PHASE 9 BACKEND TESTS PASSED!\n');
  } finally {
    server.close();
  }
}

runTests().catch(err => {
  console.error('Test execution error:', err);
  process.exit(1);
});
