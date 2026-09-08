# MeshRoute — Agent Task Brief (single-file reference)

> This file is a condensed stand-in for `masterprompt.md`, `rules.md`,
> `architecture.md`, and `testing-guide.md`. Paste this whole file into a
> new agent session instead of attaching all four. If the agent needs
> deeper detail on a specific point, tell it to ask rather than guess.

---

## 1. What MeshRoute Is

Android-first, offline-first emergency communication app for
connectivity-dead / mountainous areas.

**Core flow:** SOS → nearby smartphone → relay → another smartphone →
gateway → internet/backend → emergency contact.

It is a **store-carry-forward opportunistic mesh** system. The sender does
not need connectivity when creating the SOS. Any phone can act as sender,
relay, or gateway — gateway is just "a phone that currently has internet,"
not a special role.

**It is NOT a chat app.** Don't build profiles, stickers, media sharing, or
chat UX. The only thing that matters is reliable propagation of an
emergency packet toward a gateway.

Reference repos (study patterns, don't fork-merge blindly):
- AlertNet — `rio-ARC/Alert-Net` → Android BLE/Wi-Fi Direct/foreground
  service base
- Knit — `getknit/knit` → routing, TTL, dedup, transport abstraction,
  store-and-forward
- MeshLink — `BariaHarshh/Meshlink--Offline-messaging` → SOS UX, Room,
  Nearby Connections, encryption integration

---

## 2. Current State

> **Fill this in and keep it updated — this is the single most important
> section for the agent.**

- **Current phase:** 6 (TTL / hop limit)
- **Phases 1–5 status:** done (manual device testing on physical phones)
- **Testing mode:** manual only, on Android Studio + physical phones (no
  automated/unit tests being written)
- **Devices available for testing:** _(fill in — e.g. "4 phones: A, B, C, D")_
- **Known issues / open bugs:** _(fill in)_

---

## 3. Build Order (do not reorder)

1. SOS creation
2. GPS capture
3. Encrypted SOS packet
4. Peer discovery
5. Phone-to-phone transfer
6. Local persistent queue
7. Store-carry-forward
8. Duplicate suppression
9. TTL / expiry
10. Gateway detection
11. Backend upload
12. Emergency notification
13. Mesh/delivery status UI

Full phase-by-phase build instructions and exit criteria live in
`masterprompt.md` — ask for a specific phase's detail if needed rather than
guessing scope.

---

## 4. Hard Rules

- Don't build chat features, profiles, stickers, or media sharing.
- Don't claim guaranteed delivery, cross-platform support, or third-party
  (police/ambulance/satellite) integration unless actually implemented and
  tested.
- Don't describe this as "WhatsApp without internet."
- Relay devices must only ever carry **ciphertext** — encrypt payload
  before it touches the transport layer.
- Every packet needs: unique `message_id`, TTL/hop-limit, and time-based
  expiration (bounded lifetime, not just hop count).
- Every device needs a seen-set to prevent reprocessing/re-delivering
  duplicate `message_id`s.
- Routing strategy for MVP = opportunistic forward + dedup + TTL only. No
  geographic routing or gateway prediction yet.
- Android-first. Pick whichever transport (BLE / Wi-Fi Direct / Wi-Fi
  Aware) is actually reliable on the test devices, not the theoretical
  best.

Full rule list with rationale lives in `rules.md`.

---

## 5. Packet Schema (reference)

```json
{
  "message_id": "unique-id",
  "sender_id": "device-id",
  "timestamp": "unix-time",
  "location": { "latitude": 0.0, "longitude": 0.0, "accuracy": 0 },
  "priority": "SOS",
  "ttl": 8,
  "hops": 0,
  "payload": "encrypted-emergency-data"
}
```

Routing pseudocode:
```
onPacketReceived(packet):
    if packet.message_id already seen: discard; return
    mark seen
    store locally
    deliver if this device is a destination/gateway
    if TTL expired: stop
    else: discover peers, forward
```

Full architecture (module layout, backend API, gateway behavior, security
model) lives in `architecture.md`.

---

## 6. Testing Approach (manual, phone + Android Studio)

No unit tests being written — all testing is manual, on physical Android
devices via Android Studio.

**Standard loop:**
1. Connect phone via USB, enable USB debugging, authorize on-device.
2. Select phone in Android Studio's device dropdown → Run.
3. Repeat per phone (multiple phones = multiple `adb -s <id> logcat`
   terminals, since one Android Studio Logcat panel shows one device at a
   time).
4. Trigger the action under test from the UI/debug menu.
5. Watch each phone's Logcat, filtered by app/tag, for expected log lines.
6. Confirm against the phase's pass condition (below).

**Per-phase manual pass conditions (Phase 6 onward):**

| Phase | Setup | Pass condition |
|---|---|---|
| 6 — TTL | 3–4 phones in a line (A–B–C–D), low TTL first, then normal TTL | Low TTL: propagation stops at correct hop, D never receives. Normal TTL: reaches D. |
| 7 — Real SOS packet | 2 phones, GPS on | Relay phones only ever log ciphertext; authorized decrypt recovers correct GPS + message. |
| 8 — Gateway | 1 gateway phone (toggle airplane mode) + stub server (local/ngrok) | No upload while offline; queued packets upload once connectivity returns; gateway still relays mesh traffic throughout. |
| 9 — Backend + notification | Real deployed backend, curl/Postman first, then real gateway phone | API validates/dedups/stores correctly via curl; real SOS triggers a real notification end-to-end. |
| 10 — UI polish | Full 3–5 phone "Killer Demo" | Full chain (SOS→relay→gateway→backend→notification) works, UI shows each checkpoint live, runs cleanly **twice in a row**. |

Regression note: after each phase passes, spot-check the *immediately
preceding* phase (2 min) — later phases can silently break earlier
behavior (e.g. gateway code breaking dedup state).

Full per-phase testing detail (including earlier phases 1–5, and what
automated tests would look like if you ever add them back) lives in
`testing-guide.md`.

---

## 7. When the Agent Needs More Detail

This brief is intentionally condensed. If a task requires specifics not
covered here — exact module/package layout, full backend endpoint list,
exact security requirement list, or phase 1–5 testing detail — ask rather
than inferring, or request the relevant full file (`masterprompt.md`,
`rules.md`, `architecture.md`, `testing-guide.md`).