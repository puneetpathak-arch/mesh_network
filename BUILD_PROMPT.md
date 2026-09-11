# MeshRoute — SOS Dashboard
# Phasewise Build Prompt

## Context
MeshRoute backend (`backend/server.js`) ingests SOS packets, 
deduplicates by message_id, validates TTL, decrypts via AES-256-GCM, 
and currently stores records for the live map dashboard. Tests live in `backend/test_api.js`.

Core Feature:
A) A live admin dashboard showing all received SOS packets on a map with real-time feed

---

## PHASE 1 — In-Memory SOS Store (Complete)
- In-memory array/store in server.js holding decrypted SOS records:
  `{ messageId, senderName, lat, lon, medicalNote, hopCount, ttl, receivedAt }`
- Pushed after successful decryption + dedup/TTL checks
- Capped at last 500 records to prevent unbounded memory growth

---

## PHASE 2 — Dashboard Data Endpoint (Complete)
- `GET /api/sos-events` returning in-memory store as JSON (array of records, newest first)
- CORS headers enabled
- `GET /api/sos-events/stream` Server-Sent Events live push stream

---

## PHASE 3 — Dashboard Frontend (Complete)
- Static HTML dashboard (`backend/public/dashboard.html`) served at `/dashboard`
- Leaflet.js with Dark Matter map tiles (free, zero API key)
- Pulsing animated radar markers on emergency coordinates
- Interactive popups and scrolling sidebar incident feed

---

## PHASE 4 — Dashboard Polish Pass (Complete)
- Live packet counter header ("X SOS events received")
- "IER vs Epidemic" routing strategy badges
- Auto-centering & zoom-to-fit all markers on update