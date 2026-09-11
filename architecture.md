# MeshRoute — Architecture

## 1. System Overview

```
                     INTERNET
                         │
                         ▼
                  ┌─────────────┐
                  │   GATEWAY   │
                  │   PHONE D   │
                  └──────┬──────┘
                         │
                         ▼
                 MeshRoute Backend
                         │
                         ▼
                 Emergency Contact


 Phone A              Phone B              Phone C
 ┌───────┐            ┌───────┐            ┌───────┐
 │  SOS  │            │ Relay │            │ Relay │
 └───┬───┘            └───┬───┘            └───┬───┘
     │                    │                    │
     └──────► Store ─────► Store ─────────────► Store
                    Carry / Forward
```

### End-to-end conceptual path

```
User
 ↓
SOS UI
 ↓
GPS
 ↓
SOS Packet
 ↓
Encryption
 ↓
Local Persistent Queue
 ↓
Peer Discovery
 ↓
Relay
 ↓
Duplicate Detection
 ↓
TTL Check
 ↓
Forwarding
 ↓
Gateway Detection
 ↓
Backend API
 ↓
Emergency Notification
```

Any device can simultaneously act as **sender, receiver, relay, and/or
gateway** — there is no dedicated hardware role. A "gateway" is simply
whichever device currently has internet connectivity.

---

## 2. Packet Schema

Conceptual SOS packet (wire format may be optimized later, but these
properties are mandatory: unique identity, time, location, priority,
bounded propagation, encrypted content):

```json
{
  "message_id": "unique-id",
  "sender_id": "device-id",
  "timestamp": "unix-time",
  "location": {
    "latitude": 0.0,
    "longitude": 0.0,
    "accuracy": 0
  },
  "priority": "SOS",
  "ttl": 8,
  "hops": 0,
  "payload": "encrypted-emergency-data"
}
```

---

## 3. Routing Model (MVP)

Strategy: **opportunistic forwarding + duplicate suppression + TTL**. No
geographic routing or gateway prediction in v1.

```
onPacketReceived(packet):

    if packet.message_id already seen:
        discard duplicate
        return

    mark message_id as seen

    store packet locally

    deliver packet if this device is a destination/gateway

    if TTL expired:
        stop

    discover useful peers

    forward packet
```

### Store-Carry-Forward lifecycle

```
STORE     — receive packet and persist it locally
CARRY     — device moves while retaining the packet
DISCOVER  — another participating device appears
FORWARD   — packet is transferred to the useful peer
REPEAT    — the next device stores and carries it
GATEWAY   — eventually a device with internet access receives it
UPLOAD    — gateway sends it to the backend
```

### Duplicate suppression

Multiple relay paths can deliver the same packet to one device:

```
             Phone B
            ↗       ↘
Phone A ──►            Phone D
            ↘       ↗
             Phone C
```

Each device keeps a seen-set:

```
seenMessages = Set<MessageId>

if messageId in seenMessages:
    ignore
else:
    seenMessages.add(messageId)
    process(packet)
```

### TTL / expiry

```
TTL = 8 hops   (example)
```

Each relay increments hop count / decrements remaining TTL.

```
stop propagation when hops >= ttl
```

Time-based expiration is also required, independent of hop count, to
prevent stale SOS messages circulating indefinitely.

---

## 4. Security Model

```
SOS Data
   ↓
Encrypt
   ↓
Relay devices carry ciphertext
   ↓
Gateway
   ↓
Backend
   ↓
Authorized recipient
```

Relay devices carry ciphertext only and do not need to read emergency
contents. Security surface area includes: encrypted payload, minimum
necessary metadata exposed outside the payload, message expiration,
gateway authentication, device/session validation, anti-spam/rate
limiting, and (in a mature version) replay protection.

---

## 5. Android App Architecture

```
app/
│
├── ui/
│   ├── sos/
│   ├── mesh/
│   ├── contacts/
│   └── delivery/
│
├── domain/
│   ├── model/
│   ├── routing/
│   └── usecase/
│
├── data/
│   ├── database/
│   ├── repository/
│   └── queue/
│
├── mesh/
│   ├── transport/
│   ├── ble/
│   ├── wifiaware/
│   ├── router/
│   └── protocol/
│
├── security/
│
└── service/
    └── MeshForegroundService
```

Notes:
- `mesh/transport/` should abstract the radio (BLE, Wi-Fi Direct, Wi-Fi
  Aware) so `mesh/router/` logic stays radio-independent — this pattern is
  drawn from Knit's transport-abstraction approach.
- `service/MeshForegroundService` keeps discovery/relay alive in the
  background within Android's foreground-service constraints.
- Exact package structure may shift depending on which reference
  implementation a given module is adapted from — this is a starting
  point, not a contract.

---

## 6. Backend Architecture

```
Gateway Phone
     │
     │ HTTPS
     ▼
MeshRoute API
     │
     ├── Validate
     ├── Authenticate
     ├── Deduplicate
     ├── Store incident
     └── Trigger notification
             │
             ▼
       Emergency Contact
```

Minimal endpoints:

```
POST /api/sos
GET  /api/sos/{id}
```

Backend processing flow:

```
receive SOS
   ↓
validate packet
   ↓
check message_id
   ↓
if duplicate → acknowledge existing incident
   ↓
otherwise store incident
   ↓
send notification
   ↓
return gateway acknowledgement
```

---

## 7. Gateway Behavior

A gateway is any MeshRoute device that currently has internet
connectivity. It must continue participating in the mesh as a normal node
while also:

```
Mesh packet queue
      │
      ▼
Internet available?
   │          │
  NO         YES
   │          │
 Keep         ▼
 queued    Upload
             │
             ▼
         Backend ACK
             │
             ▼
        Mark delivered
```

Responsibilities, in order: continue participating in the mesh → receive
queued packets → detect internet availability → upload unsent packets →
receive backend acknowledgement → mark packets delivered/sent.

---

## 8. Mesh Status UI (data model, not just visuals)

The UI layer should be able to render, at minimum:

- Nearby peers
- Mesh status
- Number of hops
- SOS transmission state
- Queued packets
- Gateway status
- Delivery progress

Example target state display:

```
SOS TRANSMISSION
Hop 1 ✓
Hop 2 ✓
Gateway ✓
Emergency contact notified
```

---

## 9. Repository-to-Module Mapping

| MeshRoute module | Primary reference | Reference role |
|---|---|---|
| `mesh/transport/`, `mesh/ble/`, `service/` | AlertNet | Android BLE/Wi-Fi Direct mesh base, foreground service behavior |
| `mesh/router/`, `data/queue/`, TTL/dedup logic | Knit | `MeshTransport`, `MeshRouter`, `MeshManager`, `SeenSet`, `ForwardSync`, `ForwardStore`, wire protocol, transport abstraction |
| `ui/sos/`, `data/database/`, `security/` | MeshLink | SOS UX, Room persistence, Nearby Connections, encryption integration patterns |

```
AlertNet   → Primary Android/networking reference
Knit       → Routing + store-forward + transport reference
MeshLink   → SOS + Android architecture + UI reference
                    ↓
               MeshRoute (own product)
```

See `rules.md` §5 for the rules governing how these references were consulted.

---

## 10. Known Limitations & Future Work

The following architectural constraints are acknowledged as deliberate MVP trade-offs and prioritized for production hardening:

1. **OEM Background-Kill Risk:** Aggressive proprietary battery managers (Samsung OneUI, Xiaomi MIUI, OnePlus OxygenOS) may terminate background BLE scans/advertisements. Mitigated for MVP via `MeshForegroundService` (`connectedDevice` type with sticky persistent notification) and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; full OEM-specific whitelist guidance is future work.
2. **Unauthenticated Telemetry Beacons:** Local neighbor discovery beacons currently exchange unauthenticated battery, mobility, and link metrics. Sybil and blackhole attack mitigation via cryptographic beacon signatures is slated for Phase 2.
3. **Single Backend Instance (No HA):** The emergency ingestion gateway runs on a single Node.js runtime. Sufficient for demonstration and hackathon validation; multi-region geo-distributed clustering with load-balanced egress is planned for production.
4. **EDS Factor Calibration:** Emergency Delivery Score weights ($w_b = 0.35, w_m = 0.30, w_l = 0.15, w_p = 0.20$) are empirically assigned based on DTN simulation baselines. Formal weight calibration, ablation studies, and ML-driven parameter optimization are reserved for future research.
5. **Relay Data Retention & Storage Security:** Store-and-forward SQLite database drops records upon TTL expiration, but lacks hardware-backed at-rest database encryption on intermediate relay phones. Flagged as a known gap for Android Keystore integration in mature releases.
