# MeshRoute — Comprehensive Agent Specification & System Documentation

> **Target Audience:** AI Agents, LLM Code Reviewers, and Systems Engineers.  
> **Document Purpose:** Complete technical specification of the MeshRoute codebase to enable autonomous analysis, architectural review, feature extension, and contextual suggestion generation without prior knowledge of the workspace.

---

## 1. Executive Summary & Core Philosophy

**MeshRoute** is an Android-first, offline-first, store-carry-forward opportunistic mesh emergency communication platform designed for wilderness, disaster, and connectivity-dead environments.

### Core Value Proposition & Flow
$$\text{SOS Originator} \xrightarrow{\text{BLE Mesh}} \text{Relay Phone(s)} \xrightarrow{\text{Store-Carry-Forward (IER/EDS)}} \text{Gateway Phone} \xrightarrow{\text{Internet/Cellular}} \text{Backend API \& Nostr Relays} \rightarrow \text{Emergency Dispatch}$$

### Fundamental Principles & Invariants
1. **Not a Chat App:** MeshRoute is strictly an emergency SOS dispatch pipeline. It does **not** support user profiles, avatars, media sharing, chat bubbles, or social features. Every byte of battery and radio bandwidth is dedicated to guaranteed packet propagation.
2. **Dynamic Roles:** Any device can simultaneously be a **Sender (Originator)**, a **Relay Node (Store-Carry-Forward)**, or a **Gateway (Egress)**. A gateway is simply *any phone that currently holds active internet connectivity* (cellular/Wi-Fi).
3. **Zero-Knowledge Ciphertext Relays:** Relays only inspect the routing envelope (`message_id`, `ttl`, `hops`, `priority`). The emergency details (sender name, medical conditions, message body) are encrypted end-to-end via AES-256-GCM. Relays never possess the plaintext.
4. **Dual Routing Architecture (Baseline vs. IER):** 
   - **Baseline:** Epidemic Flooding (opportunistic broadcast to all visible peers).
   - **Innovation:** MeshRoute Intelligent Emergency Routing (IER) with Emergency Delivery Score (EDS) to selectively forward to high-probability carriers while reducing redundant transmissions by $> 50\%$.

---

## 2. High-Level Architecture & Component Interaction

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                     ANDROID CLIENT (Kotlin)                                     │
│                                                                                                 │
│  ┌──────────────────────┐      ┌─────────────────────────────┐      ┌────────────────────────┐  │
│  │   UI Layer (Compose) │      │      Location & Motion      │      │   Security Subsystem   │  │
│  │ - MainSosScreen      │◄────►│ - AndroidGpsLocationProvider│      │ - CryptoManager        │  │
│  │ - DeliveryFlowView   │      │ - MobilityEstimator (Sensor)│      │ - KeyManager           │  │
│  │ - RoutingBenchmark   │      └──────────────┬──────────────┘      │ - NoiseTransportSec    │  │
│  └──────────┬───────────┘                     │                     └───────────┬────────────┘  │
│             │                                 ▼                                 │               │
│             │                  ┌─────────────────────────────┐                  │               │
│             └─────────────────►│   MeshRouter (Orchestrator) ◄──────────────────┘               │
│                                │   [Epidemic vs. IER (EDS)]  │                                  │
│                                └──────────────┬──────────────┘                                  │
│                                               │                                                 │
│                      ┌────────────────────────┼────────────────────────┐                        │
│                      ▼                        ▼                        ▼                        │
│         ┌─────────────────────────┐ ┌──────────────────┐ ┌───────────────────────────┐          │
│         │   Transport Subsystem   │ │  Data / Queue    │ │     Gateway Subsystem     │          │
│         │ - BleMeshTransport      │ │ - ForwardStore   │ │ - GatewayUploader         │          │
│         │ - NodeTelemetryBeacon   │ │ - Room Database  │ │ - NetworkMonitor          │          │
│         │ - BlePowerManager       │ │ - SeenSet (Dedup)│ │ - NostrRelayBridge        │          │
│         └────────────┬────────────┘ └──────────────────┘ └─────────────┬─────────────┘          │
└──────────────────────┼─────────────────────────────────────────────────┼────────────────────────┘
                       │                                                 │
            BLE Radio (P2P Over-the-Air)                      HTTPS / WebSockets (Egress)
                       │                                                 │
                       ▼                                                 ▼
          ┌─────────────────────────┐                      ┌───────────────────────────┐
          │  Nearby Relay Device    │                      │  MeshRoute Node.js Server │
          │  (Store-Carry-Forward)  │                      │  - Ingest & Deduplicate   │
          └─────────────────────────┘                      │  - AES-GCM Decrypt        │
                                                           │  - Emergency Notification │
                                                           │  - Nostr Relay Network    │
                                                           └───────────────────────────┘
```

---

## 3. Directory & File Inventory

### 3.1 Android Codebase (`app/src/main/java/com/meshroute/app/`)

| Package / File | Purpose & Responsibilities | Key Classes / Interfaces |
| :--- | :--- | :--- |
| `mesh/router/` | Core packet routing engine, pluggable strategies, duplicate suppression, and live benchmarking. | `MeshRouter`, `RoutingStrategy`, `EdsRoutingStrategy`, `EpidemicRoutingStrategy`, `DeliveryMetricsCollector`, `SeenSet` |
| `mesh/transport/` | Transport abstraction layer decoupled from specific physical radio technology. | `MeshTransport`, `InboundPacket`, `Peer`, `TransportType`, `TransportHealth`, `SosPacket`, `LocationData` |
| `mesh/ble/` | Production BLE GATT engine with 4-byte telemetry advertising, scanning, chunking, and power modes. | `BleMeshTransport`, `NodeTelemetryBeacon`, `BlePowerManager`, `BleConstants` |
| `sensor/` | On-device motion sensing to determine node mobility (Stationary vs Walking vs Fast). | `MobilityEstimator`, `MobilityState` |
| `data/database/` | Room ORM layer for offline durability of packets and historical deduplication keys. | `AppDatabase`, `PacketDao`, `SeenMessageDao`, `QueuedPacketEntity`, `SeenMessageEntity` |
| `data/queue/` | Disk-backed transactional queue management for store-carry-forward routing. | `ForwardStore` |
| `security/` | Cryptographic primitives, key generation, payload serialization, and transport encryption. | `CryptoManager`, `KeyManager`, `EmergencyPayload`, `NoiseTransportSecurity` |
| `gateway/` | Internet detection, queue egress, HTTP backend uploader, and decentralized Nostr bridge. | `GatewayUploader`, `NetworkMonitor`, `AndroidNetworkMonitor`, `NostrRelayBridge`, `NostrEvent` |
| `location/` | High-accuracy GPS provider with caching fallback for offline coordinates. | `LocationProvider`, `AndroidGpsLocationProvider` |
| `service/` | Background Android Foreground Service keeping BLE advertising/scanning active when screen is off. | `MeshForegroundService` |
| `ui/` | Modern Jetpack Compose UI with dark theme, live mesh metrics, real-time hop visualizer, and benchmark telemetry card. | `MainSosScreen`, `NetworkDetailsScreen`, `RoutingBenchmarkCard`, `SosDeliveryFlowView`, `Theme` |
| `MainActivity.kt` | Android entry point orchestrating dependency wiring, runtime permissions, sensor startup, and UI binding. | `MainActivity` |

### 3.2 Backend Codebase (`backend/`)

| File | Purpose & Responsibilities |
| :--- | :--- |
| `backend/server.js` | Zero-dependency standalone Node.js HTTP server. Ingests SOS packets, validates headers, deduplicates by `message_id`, decrypts payloads, and logs emergency dispatch. |
| `backend/test_api.js` | 5-layer automated integration test suite covering ingestion, duplicate rejection, TTL drop, decryption, and health check. |

### 3.3 Research Simulator (`simulator/`)

| File | Purpose & Responsibilities |
| :--- | :--- |
| `simulator/mesh_simulation.py` | Standalone Python Monte Carlo simulator (zero external dependencies). Runs side-by-side experiments comparing Epidemic Flooding vs. EDS IER across 10-100 nodes in Sparse, Medium, Dense, and Highly Mobile topologies. |

---

## 4. Intelligent Emergency Routing (IER) & Emergency Delivery Score (EDS)

### 4.1 Theoretical & Mathematical Formulation

Instead of blindly broadcasting packets across all visible neighbors (Epidemic Flooding), **IER** scores each peer using a multi-factor utility function:

$$\text{EDS} = w_b S_{\text{battery}} + w_l S_{\text{link}} + w_m S_{\text{mobility}} + w_g S_{\text{gateway}} + w_d S_{\text{density}} + w_u S_{\text{urgency}}$$

- **Battery ($S_{\text{battery}} \in [0, 100], w_b = 0.20$):** Penalizes low-battery nodes ($<15\%$) by $0.4\times$ to prevent losing in-flight packets. Grants $+15$ bonus for charging devices.
- **Link Quality ($S_{\text{link}} \in [0, 100], w_l = 0.15$):** Linearly mapped from RSSI ($-100\text{ dBm}$ to $-40\text{ dBm}$).
- **Mobility ($S_{\text{mobility}} \in [0, 100], w_m = 0.20$):** Moving carriers bridge network partitions ($\text{Stationary}=20, \text{Walking}=70, \text{High Speed}=100$).
- **Gateway Likelihood ($S_{\text{gateway}} \in [0, 100], w_g = 0.25$):** Historical uplink recency index.
- **Node Density ($S_{\text{density}} \in [0, 100], w_d = 0.10$):** Suppresses redundant broadcasts in dense clusters ($\ge 5$ nodes).
- **Urgency ($S_{\text{urgency}} \in [0, 100], w_u = 0.10$):** Scaled by TTL deficit and packet age.

### 4.2 Adaptive Forwarding Policy & Controlled Fallback

$$\text{Threshold}(\text{TTL}) = \max\left(20, 52 - (8 - \text{TTL}) \times 5\right)$$

- **Threshold Consequence:**
  - $\text{EDS} \ge 75$: Forward immediately.
  - $\text{EDS} \in [50, 74]$: Forward with normal priority.
  - $\text{EDS} \in [25, 49]$: Store locally for better carrier (unless TTL is critical).
  - $\text{EDS} < 25$: Suppress transmission.
- **Controlled Fallback (Anti-Starvation):** If no peer satisfies the threshold or if $\text{TTL} \le 2$, the algorithm expands the candidate set to lower-scoring nodes, guaranteeing graceful degradation toward epidemic flooding.

### 4.3 Zero-Overhead BLE Telemetry Beacon (`NodeTelemetryBeacon`)

Packed into 4 bytes inside the BLE Advertisement Service Data (zero connection handshake required):
- **Byte 0:** Battery % ($0..100$) + 1-bit Charging Flag.
- **Byte 1:** Mobility Code ($0: \text{Stationary}, 1: \text{Walking}, 2: \text{Fast}$).
- **Byte 2:** Gateway Likelihood ($0..100$).
- **Byte 3:** Local Queue Load ($0..255$).

---

## 5. Empirical Benchmark Results (Simulator Evidence)

The research question:
> *"Can adaptive emergency-aware forwarding deliver SOS packets with equal or higher delivery probability while significantly reducing redundant transmissions and battery consumption compared with epidemic flooding?"*

### Experimental Data (Generated via `python3 simulator/mesh_simulation.py`):

| Scenario | Metric | Epidemic Flooding | MeshRoute IER (EDS) | Measured Difference |
| :--- | :--- | :--- | :--- | :--- |
| **Sparse Foothills** (12 nodes) | Delivery Probability<br>Total Transmissions<br>Duplicate Transmissions | 40.0%<br>15.7<br>9.7 | 40.0%<br>9.7<br>4.5 | **0.0 pp (Equal Delivery)**<br>**−38.0% Transmissions**<br>**−53.1% Duplicates** |
| **Dense Basecamp** (50 nodes) | Delivery Probability<br>Total Transmissions<br>Duplicate Transmissions | 100.0%<br>375.2<br>329.0 | 100.0%<br>169.8<br>130.8 | **0.0 pp (Equal Delivery)**<br>**−54.7% Transmissions**<br>**−60.2% Duplicates** |
| **Mobile Evacuation** (35 nodes) | Delivery Probability<br>Total Transmissions<br>Duplicate Transmissions | 95.0%<br>180.3<br>149.7 | 95.0%<br>83.2<br>58.2 | **0.0 pp (Equal Delivery)**<br>**−53.9% Transmissions**<br>**−61.1% Duplicates** |

---

## 6. How to Run & Verify

1. **Run Python Benchmark Suite:**
   ```bash
   python3 simulator/mesh_simulation.py
   ```
2. **Run Android Kotlin Unit & Architecture Tests:**
   ```bash
   ./gradlew testDebugUnitTest
   ```
3. **Physical Device Demo:**
   - Open **Network Details Screen** on device.
   - Observe live **EDS score badges (0-100)** on discovered BLE neighbors.
   - Toggle between **Baseline (Epidemic)** and **IER (EDS)** in the **Routing Strategy & Telemetry Card** to demonstrate real-time packet filtering.
