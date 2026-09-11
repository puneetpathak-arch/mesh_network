# MeshRoute — SOS Dashboard + Twilio Voice Alert
# Phasewise Build Prompt

## Context
MeshRoute backend (`backend/server.js`) ingests SOS packets, 
deduplicates by message_id, validates TTL, decrypts via AES-256-GCM, 
and currently just logs the result. Tests live in `backend/test_api.js`.

Two features to add, in order:
A) A live admin dashboard showing all received SOS packets on a map
B) A Twilio voice call to a responder when a packet lands

Build phasewise. Each phase must leave the system in a working, 
demoable state even if later phases run out of time. Stop after each 
phase and confirm before proceeding.

---

## PHASE 1 — In-Memory SOS Store
Task for agent:
- Add an in-memory array/store in server.js holding decrypted SOS 
  records: { messageId, senderName, lat, lon, medicalNote, hopCount, 
  ttl, receivedAt }
- After successful decryption + dedup/TTL checks in the existing 
  ingest handler, push the record into this store (in addition to 
  whatever it currently does)
- Cap the store at, e.g., last 500 records to avoid unbounded memory 
  growth
- No persistence needed (in-memory is fine for a hackathon demo — 
  restart clears it, that's acceptable)

Checkpoint: Existing tests still pass. Manually POST a test packet 
and confirm (via a temporary console.log or debugger) it lands in 
the store correctly.

---

## PHASE 2 — Dashboard Data Endpoint
Task for agent:
- Add a new GET endpoint, e.g. `/api/sos-events`, returning the 
  current in-memory store as JSON (array of records, most recent first)
- Add basic CORS headers if the dashboard will be served from a 
  different origin/port during dev
- Add a lightweight GET `/api/sos-events/stream` OR just have the 
  frontend poll `/api/sos-events` every 2-3 seconds — polling is far 
  simpler than WebSockets and totally fine for a hackathon demo, 
  recommend polling unless agent strongly prefers otherwise

Checkpoint: Hit the endpoint manually (curl/browser) after posting a 
test packet, confirm JSON comes back correctly.

---

## PHASE 3 — Dashboard Frontend (static HTML page)
Task for agent:
- Create a single static HTML file (e.g. `backend/public/dashboard.html`) 
  served by the existing Node server (no separate build step, no 
  framework — plain HTML/CSS/JS to keep this fast)
- Use Leaflet.js (via CDN script tag) for the map — free, no API key 
  needed, unlike Google Maps
- On load and every poll interval, fetch `/api/sos-events` and:
  - Drop a marker at each record's lat/lon
  - Marker popup shows senderName, medicalNote, hopCount, receivedAt
  - New markers should visually stand out briefly when they first 
    appear (e.g., a pulsing red icon or a brief highlight) so judges 
    notice new SOS events arriving live
- Also render a simple sidebar/list view below or beside the map: 
  most recent SOS events as a scrolling list, newest on top
- Dark theme to match the rest of the MeshRoute UI aesthetic 
  described in the app (dark Compose theme) — keep visual consistency
- No auth, no login — this is a demo dashboard only

Checkpoint: Open the dashboard in a browser, POST a couple of test 
packets with different lat/lon values, confirm markers appear on the 
map within a few seconds without a page refresh.

---

## PHASE 4 — Dashboard Polish Pass
Task for agent:
- Add a packet counter/header ("X SOS events received")
- Add a simple "Epidemic vs IER" tag on each record if that data is 
  available in the packet metadata (nice tie-in to your core routing 
  feature, but skip if not readily available — don't force it)
- Make sure the map auto-centers/zooms to fit all markers on load

Checkpoint: This phase is optional polish — skip entirely if short on 
time. Phases 1-3 alone already give you a demoable dashboard.

---

## PHASE 5 — Twilio Setup (human, not agent)
- [ ] Create Twilio account, get trial credit
- [ ] Get a Twilio phone number
- [ ] Verify the responder's real phone number in Twilio console
- [ ] Note ACCOUNT_SID, AUTH_TOKEN, TWILIO_FROM_NUMBER
- [ ] Add to `.env`, confirm `.env` is gitignored

Checkpoint: Credentials in hand, responder number verified.

---

## PHASE 6 — Twilio Config Wiring
Task for agent:
- Add `twilio` npm dependency
- Load env vars: TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, 
  TWILIO_FROM_NUMBER, RESPONDER_PHONE_NUMBER
- Warn (don't crash) if missing; initialize Twilio client conditionally
- No behavior change to packet handling yet

Checkpoint: Server starts cleanly with and without Twilio configured.

---

## PHASE 7 — TwiML Builder (standalone, testable)
Task for agent:
- Write pure function `buildEmergencyTwiml(record)` using the same 
  record shape from Phase 1's store
- <Say> with Polly.Joanna voice, say message, pause 1s, say 
  "Repeating", say message again
- Escape XML special characters in field values
- Omit missing/undefined fields rather than saying "undefined"
- Add unit tests: full payload, missing optional field, special 
  characters in message

Checkpoint: Tests pass. Manually print TwiML for a sample record and 
verify it's valid, natural-sounding XML.

---

## PHASE 8 — Wire Call Trigger into Ingestion
Task for agent:
- After Phase 1's store-push (same point in the handler), call 
  `sendEmergencyCallAlert(record)`
- Builds TwiML via Phase 7's function, calls Twilio calls.create() 
  with inline twiml param (no separate webhook endpoint needed), 
  `to: RESPONDER_PHONE_NUMBER`, `from: TWILIO_FROM_NUMBER`
- Try/catch, one retry on failure, log SID/status on success
- Call failure must NEVER block packet ingestion or dashboard 
  updates — those must keep working even if Twilio is down
- Skip entirely if Twilio not configured

Checkpoint: Existing tests still pass unmodified.

---

## PHASE 9 — Mocked Call Integration Test
Task for agent:
- Mock Twilio client in test_api.js
- Assert calls.create() fires once per valid new packet, with correct 
  `to` and TwiML content
- Assert no call on duplicate or TTL-failed packets
- Assert ingestion still succeeds even if calls.create() throws

Checkpoint: Full suite passes.

---

## PHASE 10 — Real End-to-End Test (human)
- Set real env vars, start server
- POST a real encrypted test packet
- Confirm: dashboard shows the marker within seconds AND responder's 
  phone rings with the TTS message, twice
- Note Twilio trial disclaimer — decide whether to upgrade account

Checkpoint: Both features fire together from one real packet.

---

## PHASE 11 — Full Pipeline Dress Rehearsal (human)
- Run the real app flow: SOS from device A → relay via B → gateway 
  device C → backend
- Confirm dashboard updates AND call fires from the real pipeline, 
  not just a direct POST
- Repeat at least twice, ideally in the actual demo room/network

Checkpoint: This is your dress rehearsal — if unreliable, stop adding 
features and debug this instead.

---

## Explicitly Out of Scope (any phase)
- Dashboard auth/login
- Dashboard persistence across server restart
- SMS
- Separate /voice webhook endpoint (inline TwiML only)
- IVR / responder acknowledgment
- Historical/filterable dashboard views — live feed only

## Hard Stop Rule
If any phase breaks existing packet ingestion, dedup, decryption, or 
existing tests — stop and fix before continuing. A working demo with 
fewer features beats a broken demo with more.