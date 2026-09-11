#!/usr/bin/env python3
"""
MeshRoute Experiment Simulator (Phases 8-11)
Comparative Evaluation: Baseline Epidemic Flooding vs. MeshRoute Intelligent Emergency Routing (IER / EDS)

Measures:
- Delivery Probability (% reached gateway)
- Delivery Latency (hops & time steps)
- Total Transmissions (BLE packets sent)
- Duplicate Transmissions (received by already-seen nodes)
- Energy Cost Proxy (total transmissions + active radio time)
"""

import random
import math
from dataclasses import dataclass, field
from typing import List, Dict, Set, Optional

# --- Simulation Configuration & Data Models ---

@dataclass
class Node:
    id: str
    x: float
    y: float
    battery: float          # 0.0 to 1.0
    is_charging: bool
    mobility: float         # 0.0 (stationary), 0.5 (walking), 1.0 (vehicle)
    gateway_prob: float     # 0.0 to 1.0 (historic internet likelihood)
    is_gateway_now: bool = False
    is_originator: bool = False
    seen_packets: Set[str] = field(default_factory=set)
    queue: List[dict] = field(default_factory=list)

@dataclass
class SimulationMetrics:
    total_transmissions: int = 0
    duplicate_transmissions: int = 0
    packets_delivered: int = 0
    total_latency_steps: int = 0
    energy_proxy: float = 0.0

# --- Scoring Algorithm (Emergency Delivery Score - EDS) ---

def calculate_eds(
    peer: Node,
    rssi_dbm: float,
    packet_ttl: int,
    neighbor_count: int,
    weights=(0.20, 0.15, 0.20, 0.25, 0.10, 0.10)
) -> float:
    w_b, w_l, w_m, w_g, w_d, w_u = weights
    
    # 1. Battery Score (0-100)
    battery_score = peer.battery * 100.0
    if peer.is_charging:
        battery_score = min(100.0, battery_score + 15.0)
    elif peer.battery < 0.15:
        battery_score *= 0.35  # Heavily penalize dying nodes

    # 2. Link Score from RSSI (-100 dBm to -40 dBm -> 0 to 100)
    clamped_rssi = max(-100.0, min(-40.0, rssi_dbm))
    link_score = ((clamped_rssi + 100.0) / 60.0) * 100.0

    # 3. Mobility Score (0-100)
    mobility_score = peer.mobility * 100.0

    # 4. Gateway Likelihood Score (0-100)
    gateway_score = peer.gateway_prob * 100.0

    # 5. Density Score (high in sparse, lower in dense to suppress broadcast storm)
    density_score = 100.0 if neighbor_count <= 3 else (70.0 if neighbor_count <= 6 else 40.0)

    # 6. Urgency Score (increases as TTL decreases)
    ttl_deficit = max(0, 8 - packet_ttl)
    urgency_score = min(100.0, ttl_deficit * 14.0)

    eds = (
        w_b * battery_score +
        w_l * link_score +
        w_m * mobility_score +
        w_g * gateway_score +
        w_d * density_score +
        w_u * urgency_score
    )
    return max(0.0, min(100.0, eds))

# --- Network Simulator Class ---

class MeshNetworkSimulator:
    def __init__(self, num_nodes: int, area_size: float, tx_range: float, strategy: str = "epidemic", seed: int = 42):
        self.num_nodes = num_nodes
        self.area_size = area_size
        self.tx_range = tx_range
        self.strategy = strategy  # "epidemic" or "eds"
        random.seed(seed)
        self.nodes: Dict[str, Node] = {}
        self.metrics = SimulationMetrics()
        self._init_topology()

    def _init_topology(self):
        # Originator at (0, area_size/2)
        origin_node = Node(
            id="NODE-ORIGIN",
            x=10.0,
            y=self.area_size / 2.0,
            battery=0.85,
            is_charging=False,
            mobility=0.2,
            gateway_prob=0.0,
            is_originator=True
        )
        self.nodes[origin_node.id] = origin_node

        # Gateway at (area_size - 10, area_size/2)
        gateway_node = Node(
            id="NODE-GATEWAY",
            x=self.area_size - 10.0,
            y=self.area_size / 2.0,
            battery=0.95,
            is_charging=True,
            mobility=0.0,
            gateway_prob=1.0,
            is_gateway_now=True
        )
        self.nodes[gateway_node.id] = gateway_node

        # Intermediate relay nodes distributed in between
        for i in range(1, self.num_nodes - 1):
            nid = f"NODE-{i:02d}"
            # Random battery distribution: some dying, some normal, some high
            bat = random.choices([0.10, 0.45, 0.85], weights=[0.25, 0.45, 0.30])[0]
            mob = random.choices([0.1, 0.6, 1.0], weights=[0.50, 0.35, 0.15])[0]
            gw_p = random.uniform(0.05, 0.80)
            
            # Place between origin and gateway with random distribution
            x = random.uniform(20.0, self.area_size - 20.0)
            y = random.uniform(10.0, self.area_size - 10.0)
            self.nodes[nid] = Node(
                id=nid,
                x=x,
                y=y,
                battery=bat,
                is_charging=(bat > 0.9 and random.random() < 0.2),
                mobility=mob,
                gateway_prob=gw_p
            )

    def get_neighbors(self, node: Node) -> List[tuple[Node, float, float]]:
        """Returns list of (neighbor_node, distance, rssi) within tx_range"""
        neighbors = []
        for other in self.nodes.values():
            if other.id == node.id:
                continue
            dist = math.hypot(node.x - other.x, node.y - other.y)
            if dist <= self.tx_range:
                # RSSI model: -40 dBm at 1m, decays with log distance to -95 dBm at max range
                norm_d = max(1.0, dist) / self.tx_range
                rssi = -40.0 - (55.0 * norm_d) + random.uniform(-3.0, 3.0)
                neighbors.append((other, dist, rssi))
        return neighbors

    def run_simulation(self, max_steps: int = 25) -> SimulationMetrics:
        packet = {
            "id": "SOS-EXP-001",
            "originator": "NODE-ORIGIN",
            "ttl": 8,
            "hops": 0,
            "hop_path": ["NODE-ORIGIN"]
        }

        # Inject into origin node
        self.nodes["NODE-ORIGIN"].seen_packets.add(packet["id"])
        self.nodes["NODE-ORIGIN"].queue.append(packet)

        delivered = False
        step = 0

        while step < max_steps and not delivered:
            step += 1
            # Active senders in this time step
            transmissions_this_step = []

            for nid, node in list(self.nodes.items()):
                if not node.queue:
                    continue

                curr_pkt = node.queue.pop(0)
                neighbors = self.get_neighbors(node)

                if self.strategy == "epidemic":
                    # Epidemic: forward to all neighbors not in hop path
                    for neighbor, dist, rssi in neighbors:
                        if neighbor.id not in curr_pkt["hop_path"]:
                            transmissions_this_step.append((node, neighbor, curr_pkt))

                elif self.strategy == "eds":
                    # EDS IER: evaluate score for each candidate neighbor
                    n_count = len(neighbors)
                    adaptive_threshold = max(20.0, 52.0 - (8 - curr_pkt["ttl"]) * 5.0)

                    scored_candidates = []
                    for neighbor, dist, rssi in neighbors:
                        if neighbor.id in curr_pkt["hop_path"]:
                            continue
                        score = calculate_eds(neighbor, rssi, curr_pkt["ttl"], n_count)
                        scored_candidates.append((neighbor, score))

                    # Top-K Adaptive Selection:
                    # In sparse/medium networks or critical TTL, maintain at least 2 forward paths (if available)
                    scored_candidates.sort(key=lambda x: x[1], reverse=True)
                    selected = [c[0] for c in scored_candidates if c[1] >= adaptive_threshold]

                    min_desired_paths = 2 if (n_count <= 4 or curr_pkt["ttl"] <= 3) else 1
                    if len(selected) < min_desired_paths and scored_candidates:
                        for cand, score in scored_candidates:
                            if cand not in selected and score >= 25.0:
                                selected.append(cand)
                            if len(selected) >= min_desired_paths:
                                break

                    # Critical TTL Fallback: If still nothing selected, take top available
                    if not selected and scored_candidates:
                        selected = [scored_candidates[0][0]]

                    for target in selected:
                        transmissions_this_step.append((node, target, curr_pkt))

            # Execute transmissions
            for sender, receiver, pkt in transmissions_this_step:
                self.metrics.total_transmissions += 1
                self.metrics.energy_proxy += (1.0 + (0.5 * (1.0 - sender.battery)))

                # Check if receiver already seen (duplicate transmission)
                if pkt["id"] in receiver.seen_packets:
                    self.metrics.duplicate_transmissions += 1
                    continue

                # Mark seen by receiver
                receiver.seen_packets.add(pkt["id"])

                # Check if reached gateway
                if receiver.is_gateway_now:
                    delivered = True
                    self.metrics.packets_delivered = 1
                    self.metrics.total_latency_steps = step
                    return self.metrics

                # Decrement TTL and enqueue for relay
                if pkt["ttl"] - 1 > 0:
                    relayed_pkt = {
                        "id": pkt["id"],
                        "originator": pkt["originator"],
                        "ttl": pkt["ttl"] - 1,
                        "hops": pkt["hops"] + 1,
                        "hop_path": pkt["hop_path"] + [receiver.id]
                    }
                    receiver.queue.append(relayed_pkt)

            # Node mobility update: moving nodes alter coordinates
            for node in self.nodes.values():
                if node.mobility > 0.2:
                    node.x += random.uniform(-5.0, 5.0) * node.mobility
                    node.y += random.uniform(-5.0, 5.0) * node.mobility
                    node.x = max(5.0, min(self.area_size - 5.0, node.x))
                    node.y = max(5.0, min(self.area_size - 5.0, node.y))

        return self.metrics

# --- Benchmark Runner ---

def run_benchmark_suite(runs_per_scenario: int = 25):
    scenarios = [
        {"name": "Scenario 1: Sparse Line / Foothills", "nodes": 12, "area": 120.0, "tx_range": 35.0},
        {"name": "Scenario 2: Medium Transit Corridor", "nodes": 25, "area": 140.0, "tx_range": 38.0},
        {"name": "Scenario 3: Dense Cluster / Basecamp", "nodes": 50, "area": 150.0, "tx_range": 42.0},
        {"name": "Scenario 4: Highly Mobile Evacuation", "nodes": 35, "area": 130.0, "tx_range": 36.0},
    ]

    print("=" * 84)
    print(" 🚀 MeshRoute Research Benchmark: Epidemic Flooding vs. Intelligent EDS Routing")
    print("=" * 84)

    for sc in scenarios:
        print(f"\n▶ {sc['name']} ({sc['nodes']} nodes, area {sc['area']}m, range {sc['tx_range']}m, {runs_per_scenario} trials)")
        
        # Run Epidemic
        ep_deliv, ep_lat, ep_tx, ep_dup, ep_nrg = [], [], [], [], []
        for i in range(runs_per_scenario):
            sim = MeshNetworkSimulator(sc["nodes"], sc["area"], sc["tx_range"], strategy="epidemic", seed=1000 + i)
            m = sim.run_simulation()
            ep_deliv.append(m.packets_delivered)
            if m.packets_delivered: ep_lat.append(m.total_latency_steps)
            ep_tx.append(m.total_transmissions)
            ep_dup.append(m.duplicate_transmissions)
            ep_nrg.append(m.energy_proxy)

        # Run EDS
        eds_deliv, eds_lat, eds_tx, eds_dup, eds_nrg = [], [], [], [], []
        for i in range(runs_per_scenario):
            sim = MeshNetworkSimulator(sc["nodes"], sc["area"], sc["tx_range"], strategy="eds", seed=1000 + i)
            m = sim.run_simulation()
            eds_deliv.append(m.packets_delivered)
            if m.packets_delivered: eds_lat.append(m.total_latency_steps)
            eds_tx.append(m.total_transmissions)
            eds_dup.append(m.duplicate_transmissions)
            eds_nrg.append(m.energy_proxy)

        p_ep_deliv = (sum(ep_deliv) / runs_per_scenario) * 100.0
        p_eds_deliv = (sum(eds_deliv) / runs_per_scenario) * 100.0
        avg_ep_tx = sum(ep_tx) / runs_per_scenario
        avg_eds_tx = sum(eds_tx) / runs_per_scenario
        avg_ep_dup = sum(ep_dup) / runs_per_scenario
        avg_eds_dup = sum(eds_dup) / runs_per_scenario
        avg_ep_lat = (sum(ep_lat) / len(ep_lat)) if ep_lat else 0
        avg_eds_lat = (sum(eds_lat) / len(eds_lat)) if eds_lat else 0
        
        tx_diff = ((avg_eds_tx - avg_ep_tx) / max(1.0, avg_ep_tx)) * 100.0
        dup_diff = ((avg_eds_dup - avg_ep_dup) / max(1.0, avg_ep_dup)) * 100.0

        header = f"| {'Metric':<28} | {'Epidemic Flooding':<18} | {'MeshRoute IER (EDS)':<20} | {'Improvement':<12} |"
        sep = f"|{'-'*30}|{'-'*20}|{'-'*22}|{'-'*14}|"
        print(header)
        print(sep)
        print(f"| {'Delivery Probability':<28} | {p_ep_deliv:>16.1f}% | {p_eds_deliv:>18.1f}% | {('+' if p_eds_deliv >= p_ep_deliv else '')}{p_eds_deliv - p_ep_deliv:>+9.1f} pp |")
        print(f"| {'Average Latency (steps)':<28} | {avg_ep_lat:>18.1f} | {avg_eds_lat:>20.1f} | {((avg_eds_lat - avg_ep_lat)/max(1, avg_ep_lat))*100:>+11.1f}% |")
        print(f"| {'Total Transmissions':<28} | {avg_ep_tx:>18.1f} | {avg_eds_tx:>20.1f} | {tx_diff:>+11.1f}% |")
        print(f"| {'Duplicate Transmissions':<28} | {avg_ep_dup:>18.1f} | {avg_eds_dup:>20.1f} | {dup_diff:>+11.1f}% |")

    print("\n" + "=" * 84)
    print(" ✅ Benchmark Suite Completed. Experimental evidence ready for presentation.")
    print("=" * 84)

if __name__ == "__main__":
    run_benchmark_suite(runs_per_scenario=20)
