# MeshRoute — Master Build Prompt

## How to use this file

Paste this entire document (or the relevant phase section) into an AI coding
assistant (Claude, Cursor, ChatGPT, Claude Code, etc.) as the system/task
prompt when working on MeshRoute. Work through the phases **in order**.
Do not let the assistant skip ahead to UI polish or advanced features before
the networking core (Phases 1–6) is proven on real devices.

Always keep `rules.md` and `architecture.md` in context alongside this file.

---

## 0. Project Identity (paste first, always)

> You are helping build **MeshRoute** — an Android-first, offline-first
> emergency communication app for mountainous and connectivity-dead areas.
>
> Core concept: **SOS → nearby smartphone → relay → another smartphone →
> gateway → internet/backend → emergency contact.**
>
> This is a **store-carry-forward opportunistic mesh system**, not a chat
> app. The sender does not need connectivity at the moment the SOS is
> created. Participating phones act as temporary relay nodes that store,
> carry, and forward the packet until a device with internet (the gateway)
> uploads it to the backend, which notifies the emergency contact.
>
> Reference repositories (study, do not blindly copy):
> - **AlertNet** — https://github.com/rio-ARC/Alert-Net (Android/BLE/Wi-Fi Direct mesh base)
> - **Knit** — https://github.com/getknit/knit (routing, TTL, dedup, transport abstraction, store-and-forward)
> - **MeshLink** — https://github.com/BariaHarshh/Meshlink--Offline-messaging (SOS UX, Room, Nearby Connections)
>
> Follow the priorities and constraints in `rules.md`. Follow the structure
> in `architecture.md`. Do not build "WhatsApp with Bluetooth."

---

## Phase 1 — Run a Reference Implementation

**Goal:** Get one existing mesh networking codebase running on real Android
hardware before writing any MeshRoute-specific code.

Instructions to the assistant:
1. Start with **AlertNet**. Clone it, resolve build issues, and get it
   running on at least two physical Android devices.
2. Study (do not yet modify) these areas: `mesh/`, `transport/`, `service/`,
   `security/`, `db/`, `model/`, `ui/`.
3. Then read through **Knit**'s architecture for `MeshTransport`,
   `MeshRouter`, `MeshManager`, `SeenSet`, `ForwardSync`, `ForwardStore`,
   its wire protocol, and its Bluetooth/Wi-Fi Aware transports.
4. Then inspect **MeshLink** for how SOS UI, mesh networking, local
   database, encryption, and message queuing are combined at the
   application layer.
5. Do not modify everything at once. Produce a short written summary of
   what each reference does well and what MeshRoute should keep, adapt, or
   discard.

**Exit criteria:** One reference app runs and can be demonstrated
phone-to-phone. A written comparison of the three references exists.

---

## Phase 2 — Prove One-Hop Communication

**Goal:** `Phone A → Phone B` — a minimal test packet is sent and received
over the chosen transport (BLE / Wi-Fi Direct / Wi-Fi Aware, pick whichever
is reliable on the team's physical devices).

Instructions to the assistant:
- Implement the smallest possible transport abstraction (inspired by
  Knit's `MeshTransport`) so the radio choice is not hard-wired everywhere.
- Send a simple JSON/byte test packet from A to B and confirm receipt in
  logs/UI.
- Do not add TTL, dedup, persistence, or SOS semantics yet — this phase is
  purely "can two phones talk."

**Exit criteria:** Reliable, repeatable one-hop transfer on two physical
devices.

---

## Phase 3 — Prove Multi-Hop

**Goal:** `Phone A → Phone B → Phone C` — confirm that C receives a packet
that originated at A, relayed through B.

Instructions to the assistant:
- Extend the Phase 2 transport with a minimal relay/forward step on B.
- No smart routing yet — pure "forward everything you receive."
- Log the hop path so it can be shown in a demo later.

**Exit criteria:** C visibly receives content that only A ever created.

---

## Phase 4 — Add Persistence

**Goal:** A relaying device must survive a temporary disconnection without
losing the packet.

Instructions to the assistant:
- Add local storage (Room, per `architecture.md` §Data Layer) so that when
  B receives a packet it is persisted before any forwarding attempt.
- Kill and restart the app/process on B and confirm the packet is still
  queued and forwardable.

**Exit criteria:** Packet survives an app/process restart on the relay
device.

---

## Phase 5 — Add Duplicate Suppression

**Goal:** The same message must not be processed twice, even if it arrives
via multiple paths.

Instructions to the assistant:
- Implement a `seenMessages: Set<MessageId>` (or persisted equivalent) on
  each device.
- Test topology:
  ```
  A → B → D
  A → C → D
  ```
- Confirm D processes the message exactly once even though it receives it
  from both B and C.

**Exit criteria:** Duplicate-path test passes; D shows the SOS once, not
twice.

---

## Phase 6 — Add TTL / Hop Limit

**Goal:** Packets must not circulate forever.

Instructions to the assistant:
- Add a TTL / hop-limit field to the packet (see `architecture.md` §Packet
  Schema) and decrement/increment it at each relay.
- Add time-based expiration as a secondary safeguard.
- Verify that propagation stops once `hops >= ttl`, and that expired
  packets are not forwarded or delivered.

**Exit criteria:** A packet with a low TTL demonstrably stops propagating
at the correct hop.

---

## Phase 7 — Convert the Test Packet into a Real SOS Packet

**Goal:** Turn the generic test packet from Phases 2–6 into the actual
MeshRoute SOS packet.

Instructions to the assistant:
- Implement the full packet schema from `architecture.md` §Packet Schema:
  `message_id`, `sender_id`, `timestamp`, `location` (lat/lng/accuracy),
  `priority`, `ttl`, `hops`, and an **encrypted** payload.
- Wire up GPS capture at SOS-creation time.
- Encrypt the emergency payload before it ever touches the transport layer
  — relay devices should not be able to read SOS contents (see
  `rules.md` §Security Constraints).

**Exit criteria:** A real SOS packet with GPS + encrypted payload survives
the full Phase 2–6 pipeline.

---

## Phase 8 — Gateway

**Goal:** A device with internet connectivity uploads a queued SOS.

Instructions to the assistant:
- Implement gateway detection: continuously (or periodically) check for
  internet availability on the device.
- When available, drain the local packet queue and upload each unsent SOS
  to the backend (Phase 9).
- Mark packets as delivered/sent locally once the backend acknowledges
  receipt.
- The gateway device must **keep participating in the mesh** — it is not a
  special hardware role, just a phone that currently has connectivity.

**Exit criteria:** A phone with internet visibly uploads a packet that
arrived via mesh relay from an offline phone.

---

## Phase 9 — Backend + Notification

**Goal:** `Gateway → API → Notification` — one real, reliable notification
channel for the demo.

Instructions to the assistant:
- Build a minimal backend with (at least) `POST /api/sos` and
  `GET /api/sos/{id}` per `architecture.md` §Backend Architecture.
- Backend flow: receive → validate → check `message_id` for dedup → store
  incident (or acknowledge existing one) → trigger notification → return
  gateway acknowledgement.
- Pick **one** notification channel to make bulletproof for the demo (push,
  email, or SMS) rather than implementing all three unreliably.

**Exit criteria:** An SOS submitted by the gateway results in a real
notification (e.g., an email or push) to a test "emergency contact."

---

## Phase 10 — UI Polish (Only After Networking Works)

**Goal:** Make the system legible to a human — but only once Phases 1–9 are
solid.

Instructions to the assistant, in priority order:
1. SOS creation screen (the big button + confirmation).
2. Mesh status UI — nearby peers, hop count, transmission state, queued
   packets, gateway status, delivery progress (see example status card in
   `architecture.md`).
3. Gateway status indicator.
4. Delivery-status / incident tracking screen.
5. Emergency contact configuration screen.

**Explicitly do not** spend Phase 10 (or any earlier phase) time on chat
UX, stickers, profiles, media sharing, or advanced map features — see
`rules.md` §What NOT To Build.

**Exit criteria:** The "Killer Demo" (below) can be run end-to-end with a
legible UI.

---

## Final Validation — The Killer Demo

Instructions to the assistant: once Phase 10 is done, validate against this
exact scenario using 3–5 physical Android phones:

```
Phone A — Internet OFF, Cellular OFF
      │ SOS
      ▼
Phone B — relay
      │
      ▼
Phone C — relay
      │
      ▼
Phone D — Internet ON
      │ gateway upload
      ▼
Backend
      │
      ▼
Emergency Contact notified
```

The UI should visibly show, in order:
```
SOS CREATED ✓
PACKET STORED ✓
HOP 1 ✓
HOP 2 ✓
GATEWAY FOUND ✓
UPLOADED ✓
EMERGENCY CONTACT NOTIFIED ✓
```

If any step cannot be demonstrated reliably on physical hardware, treat
that as unfinished — do not fake or hardcode demo steps.
