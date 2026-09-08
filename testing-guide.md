# MeshRoute — Testing Guide (Phase by Phase)

General rule throughout: **write the unit/automated test first if the logic
doesn't need a radio, then confirm on real hardware.** Automated tests are
faster to run and easier to debug; device tests catch timing/radio issues
automated tests can't.

Tools you'll use repeatedly:
- **JUnit** (+ Mockito/Turbine if using Kotlin coroutines/Flow) for pure
  logic tests — runs on your laptop, no phone needed.
- **Room's in-memory test database** (`Room.inMemoryDatabaseBuilder`) for
  DAO tests — no phone needed.
- **Android Instrumented Tests** (`androidTest/`) — runs on a phone/emulator,
  needed when a test touches real Android APIs (BLE, permissions, Room on
  device).
- **Logcat** (Android Studio → Logcat panel, filter by tag) — your main
  window into what's happening on-device.
- **`adb`** — installing builds, toggling airplane mode, pulling logs.
- **Postman / `curl`** — testing backend endpoints without a phone at all.

---

## Phase 1 — Run the Reference

**What you're proving:** the app builds, installs, launches, and two
phones can see each other.

- **Test type:** manual only (nothing to automate yet).
- **How:** see the step-by-step from the previous message — build in
  Android Studio, install on 2 phones via `adb`, grant permissions, enable
  radios, check Logcat + UI peers list.
- **Pass condition:** phone B's identifier appears in phone A's peer list
  (or vice versa) within ~30–60 seconds.

---

## Phase 2 — One-Hop Communication

**What you're proving:** A can send a raw test packet and B receives it.

- **Automated test:** unit-test your packet serialization/deserialization
  function in isolation (encode a test object → bytes → decode → assert
  equality). This catches format bugs before you ever touch a radio.
- **Manual test:**
  1. Launch app on A and B, confirm discovery (Phase 1 already proved
     this).
  2. Trigger "send test packet" from A.
  3. Watch Logcat on B, filtered to your transport/receive tag, for a
     line confirming the packet arrived with the expected content.
  4. If you have a debug UI element (a text field showing "last received
     packet"), confirm it updates on B.
- **Pass condition:** B logs/displays the exact payload A sent, once,
  within a few seconds.
- **Common failure:** connection drops mid-transfer — check Logcat for
  connection-state errors before assuming the packet logic is wrong.

---

## Phase 3 — Multi-Hop

**What you're proving:** C receives a packet that only A ever created,
relayed through B.

- **Automated test:** unit-test the relay function directly — feed it a
  fake incoming packet, assert it calls the "forward" method with the
  packet unmodified (or correctly modified, e.g. hop count +1). No radio
  needed for this part.
- **Manual test:**
  1. Launch app on A, B, and C. Confirm A↔B and B↔C discovery.
  2. Physically separate A and C so they're **out of direct range** — this
     is important, otherwise you can't tell if the packet went A→C
     directly or via B.
  3. Send from A.
  4. Check Logcat on C for the packet, and confirm the log shows it came
     via B (if you log the relay path) rather than direct.
- **Pass condition:** C receives the packet, and you can confirm it took
  the B-relay path (either via logs, or by physically confirming A and C
  are out of range of each other).

---

## Phase 4 — Persistence

**What you're proving:** a relaying device doesn't lose a packet if the
app or process restarts.

- **Automated test (fast, run first):** Room DAO test using
  `Room.inMemoryDatabaseBuilder(context, AppDb::class.java).build()`:
  1. Insert a test packet.
  2. Query it back, assert it matches.
  3. This alone doesn't test "survives restart," but confirms the DAO
     logic is correct before you test the harder scenario.
- **Instrumented test (still no second phone needed):** write an
  `androidTest` that inserts a packet into the **real on-disk** database
  (not in-memory), then in a second test method (new process context)
  queries for it and asserts presence — Android Studio can run these
  sequentially on one emulator/phone.
- **Manual test:**
  1. Send a packet from A to B (B is the relay under test).
  2. On B, force-stop the app (Settings → Apps → MeshRoute → Force stop),
     not just background it — this is the real test.
  3. Relaunch the app on B.
  4. Confirm the packet still shows in B's local queue/DB (via a debug
     screen, or by resuming a forward to C and confirming it still works).
- **Pass condition:** packet is still present and forwardable after a
  full force-stop + relaunch on the relay device.

---

## Phase 5 — Duplicate Suppression

**What you're proving:** the same `message_id` is processed exactly once,
even via multiple paths.

- **Automated test (do this first, it's the most valuable test in this
  phase):**
  ```
  seenSet = SeenMessageStore()
  packet = fakePacket(id = "abc123")

  onPacketReceived(packet)   // first time
  onPacketReceived(packet)   // duplicate

  assert(processCount == 1)
  ```
  Also test the edge case of two *different* message IDs both being
  processed normally.
- **Manual test (radios can introduce race conditions unit tests won't
  catch):**
  1. Set up 4 phones: A, B, C, D.
  2. Topology: A can reach B and C; B and C can both reach D; A cannot
     reach D directly.
  3. Send one SOS from A.
  4. On D, confirm the SOS is processed/displayed **once**, not twice,
     even though it arrives via both B and C.
  5. Check D's Logcat — you should see one "processed" log line and one
     "duplicate discarded" log line.
- **Pass condition:** D shows the alert once; logs confirm the duplicate
  was explicitly discarded, not silently missed.

---

## Phase 6 — TTL / Hop Limit

**What you're proving:** packets stop propagating once TTL/hops is
exceeded, and expired packets don't get forwarded or delivered.

- **Automated test:**
  ```
  packet = fakePacket(ttl = 1, hops = 0)
  relay(packet)                 // hops becomes 1
  result = relay(packet again)  // should NOT forward — hops >= ttl
  assert(result.forwarded == false)
  ```
  Also unit-test time-based expiry: construct a packet with a timestamp
  older than your expiry window, assert it's rejected/not forwarded.
- **Manual test:**
  1. Set up a 3–4 phone chain: A→B→C→D.
  2. Send an SOS from A with a deliberately low TTL (e.g. `ttl = 2`).
  3. Confirm B and C process it, but **D does not** (it's beyond the hop
     limit).
  4. Repeat with a normal TTL (e.g. `ttl = 8`) to confirm it *does* reach D
     — you need both the "it stops" and "it doesn't stop too early" cases
     tested.
- **Pass condition:** low-TTL packet visibly stops at the correct hop;
  normal-TTL packet reaches its full intended chain.

---

## Phase 7 — Real SOS Packet (GPS + Encryption)

**What you're proving:** the full packet schema is populated correctly,
and relay devices only ever see ciphertext.

- **Automated test:**
  1. Encrypt/decrypt round-trip: encrypt a sample payload, decrypt it,
     assert it matches the original. Test this in complete isolation from
     networking.
  2. Test that a malformed/tampered ciphertext fails to decrypt (don't
     let it silently return garbage as if it were valid).
  3. Unit-test packet construction: given a mock location and timestamp,
     assert the resulting packet matches the schema in `architecture.md`.
- **Manual test:**
  1. On phone A, enable GPS, create a real SOS.
  2. Inspect what phone B actually receives — **log the raw payload B
     gets over the wire** and confirm it is ciphertext, not readable
     plaintext (this specifically validates your security constraint from
     `rules.md` §Security Constraints).
  3. On a device/component that holds the decryption key (e.g. the
     backend, once Phase 9 exists — until then, a debug-only decrypt
     button), decrypt the payload and confirm the GPS coordinates and
     message content are correct and match what A actually captured.
- **Pass condition:** B/C/relay devices only ever log/see ciphertext; the
  authorized decryptor recovers the exact original GPS + message.

---

## Phase 8 — Gateway

**What you're proving:** a device with internet detects connectivity and
uploads queued packets; the mesh role continues alongside this.

- **Set up a stub server first** (this is cheap — a local Express/Flask
  server, or `ngrok http 3000` if you need a public URL for a real phone
  to reach it) that just logs incoming requests and returns `200 OK`.
- **Automated test:** unit-test the "should I upload now" decision logic
  in isolation — mock `isInternetAvailable() = true/false`, assert the
  upload function is/isn't called accordingly. Mock the HTTP client so
  this test doesn't need a real network call.
- **Manual test:**
  1. Put the gateway phone in airplane mode, queue up 1–2 packets via
     mesh relay from another phone.
  2. Confirm nothing is uploaded yet (check your stub server's logs —
     should be empty).
  3. Turn off airplane mode / enable Wi-Fi on the gateway phone.
  4. Watch the stub server logs — confirm the queued packet(s) arrive
     shortly after connectivity returns.
  5. Confirm the gateway phone still responds to mesh discovery/relay
     requests from other phones during this whole test (it shouldn't stop
     being a mesh node just because it's also a gateway).
- **Pass condition:** upload only happens once connectivity is available,
  all queued packets are eventually sent, and mesh participation continues
  throughout.

---

## Phase 9 — Backend + Notification

**What you're proving:** the API correctly validates, dedups, stores, and
triggers a real notification — test this **without phones first**.

- **Layer 1 — direct API tests (fastest, do these first):**
  - Use `curl` or Postman to `POST /api/sos` with a valid fake packet.
    Confirm `200`/`201` and a sensible response body.
  - `POST` the same `message_id` twice — confirm the second call returns
    an "acknowledge existing incident" response, not a duplicate incident.
  - `POST` a malformed packet (missing fields, bad types) — confirm it's
    rejected with a clear validation error, not a 500 crash.
  - `GET /api/sos/{id}` — confirm it returns the stored incident.
- **Layer 2 — automated backend tests:** write these as real test files
  in your backend framework (e.g. pytest for Flask, Jest for Express)
  covering the same cases as Layer 1, so they run in CI, not just
  manually in Postman.
- **Layer 3 — concurrency test:** fire the same `message_id` at the API
  twice **simultaneously** (two parallel requests, not sequential) —
  confirms your dedup logic handles a race condition, which is realistic
  since two relay paths might reach the gateway at nearly the same time.
- **Layer 4 — notification test:** trigger a real SOS via the API and
  confirm the actual notification channel fires (check the test inbox/push
  device/SMS log you're using for the demo). Do this a few times to make
  sure it's not flaky before demo day.
- **Layer 5 — full device chain (do this last):** real gateway phone →
  real deployed backend → real notification. Only attempt this once
  Layers 1–4 pass, so you're not debugging backend logic and phone/network
  flakiness at the same time.
- **Pass condition:** all 5 layers pass, in order.

---

## Phase 10 — UI Polish

**What you're proving:** the interface accurately reflects real system
state, and the full demo works end-to-end.

- **Automated:** if your mesh-status screen reads from a ViewModel/state
  object, unit-test that the state object maps correctly to display values
  (e.g. `hops=2` → displays "Hop 2 ✓"). UI logic bugs here are easy to
  miss visually but easy to catch with a test.
- **Manual — component by component:**
  1. SOS button: confirm it's not spammable (double-tap shouldn't create
     two incidents) and gives immediate feedback.
  2. Mesh status screen: manually trigger each state (no peers, 1 peer,
     relay in progress, gateway found) and confirm the UI updates
     correctly and promptly for each.
  3. Delivery status: confirm it updates in near-real-time as hops/gateway
     events actually occur, not just on a fixed timer.
- **Manual — full "Killer Demo" run-through:** this is the final
  integration test, exactly as defined in `masterprompt.md`:
  1. 3–5 physical phones, Phone A in airplane mode + cellular off.
  2. Create SOS on A.
  3. Watch it visibly relay through B, C (or however many relay phones
     you have).
  4. Confirm gateway phone (internet on) uploads it.
  5. Confirm the emergency contact actually receives the notification.
  6. Confirm the UI shows each checkpoint (`SOS CREATED ✓`, `PACKET
     STORED ✓`, `HOP 1 ✓`, `HOP 2 ✓`, `GATEWAY FOUND ✓`, `UPLOADED ✓`,
     `EMERGENCY CONTACT NOTIFIED ✓`) in the right order, in real time —
     not faked or hardcoded.
- **Pass condition:** the full demo runs cleanly, twice in a row, without
  manual intervention or resets between steps. Run it twice specifically
  because a demo that only works once often means something is
  state-dependent (e.g. seen-sets not resetting, stale queued packets)
  rather than actually reliable.

---

## Quick Reference — Test Type by Phase

| Phase | Unit/automated test? | Device test needed? | Backend/stub needed? |
|---|---|---|---|
| 1 | No | Yes (2 phones) | No |
| 2 | Yes (serialization) | Yes (2 phones) | No |
| 3 | Yes (relay logic) | Yes (3 phones) | No |
| 4 | Yes (Room DAO) | Yes (1–2 phones, force-stop) | No |
| 5 | Yes (dedup logic) | Yes (4 phones) | No |
| 6 | Yes (TTL logic) | Yes (3–4 phones) | No |
| 7 | Yes (encryption round-trip) | Yes (2 phones) | No |
| 8 | Yes (connectivity decision) | Yes (1 gateway phone + 1 sender) | Yes (stub server) |
| 9 | Yes (API + concurrency) | Yes (last step only) | Yes (real deployed backend) |
| 10 | Yes (state-to-UI mapping) | Yes (full 3–5 phone demo, run twice) | Yes (real backend) |
