# MeshRoute — Rules

These rules apply to any AI coding assistant, agent, or human contributor
working on MeshRoute. They take precedence over general assumptions about
"what a chat app should look like."

---

## 1. What MeshRoute Is

- MeshRoute is an **Android-first, offline-first emergency communication
  app** for mountainous and connectivity-dead areas.
- Core flow: **SOS → nearby smartphone → relay → another smartphone →
  gateway → internet/backend → emergency contact.**
- It is a **store-carry-forward opportunistic mesh communication system.**
- The sender does **not** need active cellular/internet connectivity at the
  moment the SOS is created.

## 2. What MeshRoute Is Not

- It is **not** a generic offline chat app.
- Do **not** describe or build it as "WhatsApp without internet."
- Do **not** let chat features become the centerpiece. The hero feature is
  **reliable propagation of emergency messages toward an eventual
  gateway** — nothing else is the point of this project.

## 3. Build Priority Order

Always build and prove in this order. Do not reorder for convenience:

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

## 4. What NOT to Build (especially early)

Do not spend significant hackathon/early-development time on, before the
emergency relay system works end-to-end:

- Profiles
- Fancy chat UX
- Stickers
- Social features
- Media sharing
- Advanced map features

If networking is not proven, none of the above matters for the demo.

## 5. Reference Repository Rules

MeshRoute uses three GitHub projects as **implementation references only**,
not as a base to fork-and-merge:

| Repo | Role |
|---|---|
| **AlertNet** (`rio-ARC/Alert-Net`) | Primary Android/P2P infrastructure reference |
| **Knit** (`getknit/knit`) | Primary routing / store-carry-forward / transport-abstraction reference |
| **MeshLink** (`BariaHarshh/Meshlink--Offline-messaging`) | Primary SOS-application / Android UX reference |

Rules for using them:

- Do **not** assume any reference's architecture is automatically correct
  for MeshRoute.
- Do **not** blindly copy features unrelated to the MVP.
- Do **not** fork three repositories and merge them blindly — MeshRoute is
  its **own project**; the repos are references, not the product
  definition.
- Before changing any networking code, first understand the existing
  transport, packet format, persistence, routing, deduplication, and
  lifecycle architecture you're building on or drawing from.
- Review each reference's code, APIs, licenses, hardware assumptions, and
  current behavior directly before incorporating anything — do not
  incorporate from memory or from this document's summary alone.

### Reuse vs. Build

**Study/reuse patterns from references for:**
Android BLE discovery, Wi-Fi Direct/Aware connectivity, socket/data
transfer, peer lifecycle, foreground services, Room/local persistence,
coroutine-based networking, message deduplication, transport abstraction,
routing tests.

**Build specifically for MeshRoute (do not source from references):**
SOS packet schema, emergency UI, emergency contact configuration, gateway
upload, backend API, emergency notification, incident state,
delivery-status UI, hackathon demo, MeshRoute-specific security rules.

## 6. Claims Discipline (do not overstate capability)

- Do **not** claim guaranteed delivery when no relay/gateway path exists.
- Do **not** claim universal Android/iOS background behavior unless it has
  actually been tested.
- Do **not** claim police, ambulance, government, or satellite integration
  unless an actual integration exists.
- Do **not** describe MeshRoute as "WhatsApp without internet."
- Demonstrate that security is part of the architecture without pretending
  to have a production-grade national emergency system.
- Measure what actually works on the team's physical devices rather than
  making broad compatibility claims — mobile OSes impose real restrictions
  around background execution, Bluetooth scanning, Wi-Fi connectivity,
  battery consumption, permissions, radio availability, and device-specific
  behavior. Scope the MVP around tested Android devices.

## 7. Routing Rules for the MVP

- Use **opportunistic forwarding + duplicate suppression + TTL**. Nothing
  fancier for v1.
- Do **not** attempt sophisticated geographic routing or gateway
  prediction during the first implementation — that's a later-stage
  improvement, not MVP scope.
- Every packet needs a globally unique `message_id`.
- Every device maintains a seen-set of recently processed message IDs and
  must not re-deliver or unnecessarily re-relay an already-seen packet.
- Every packet must have a bounded lifetime via TTL/hop-limit **and**
  time-based expiration.

## 8. Security Constraints

- Relay devices should **not** need to read emergency contents — encrypt
  the payload before it touches the transport layer; relays carry
  ciphertext only.
- Minimum necessary data should be exposed outside the encrypted payload.
- Security areas to address (even minimally, for the hackathon): encrypted
  payload, minimum necessary data, message expiration, gateway
  authentication, device/session validation, anti-spam/rate limiting.
  Replay protection is acceptable to defer to a mature version, but should
  be flagged as a known gap, not silently omitted.

## 9. Platform Scope

- Android-first for the hackathon/MVP. Prefer a **small, reliable
  Android-first MVP over broad cross-platform support.**
- Transport choice (BLE / Wi-Fi Direct / Wi-Fi Aware) should be selected
  based on what can be demonstrated reliably on the team's actual physical
  devices — not on theoretical best choice.

## 10. Success Definition

The MVP is considered successful only if the team can demonstrate, on
physical hardware, all of the following in sequence: packet created →
packet stored → relay happened → duplicate suppression works → TTL works →
gateway received packet → backend accepted packet → notification was
triggered. Partial/simulated versions of these steps do not count as
success — see `masterprompt.md` §Final Validation.
