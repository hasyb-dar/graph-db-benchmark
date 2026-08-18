# Graph Database Cloud Benchmark

Benchmarking CognoDB Cloud against managed graph database platforms using the same dataset, workloads, and client environment.

## 1. Objective

This project evaluates graph database performance using a reproducible benchmark suite.

The benchmark measures:

- Data loading throughput
- 1-hop traversal latency
- 2-hop traversal latency
- 3-hop traversal latency
- Point lookup latency
- Indexed property lookup latency
- Aggregation latency
- Concurrent read/write throughput
- p50 and p95 latency

The goal is an honest comparison rather than selecting a predetermined winner.

---

## 2. Dataset

### SNAP soc-Pokec

Source:

SNAP — Stanford Network Analysis Project

Dataset:

soc-Pokec social network

Dataset file:

`soc-pokec-relationships.txt.gz`

Benchmark sample:

- Nodes: 49,683
- Relationships loaded: 100,000
- Node label: `Person`
- Relationship type: `FRIEND`

Only the first 100,000 valid relationships were loaded so that the dataset remains small enough for free/entry-level database tiers.

---

## 3. CognoDB Cloud Environment

Database:

CognoDB Cloud

Connection:

Neo4j Bolt driver using `bolt+s://`

Authentication:

Environment variables

Credentials are NOT stored in this repository.

Environment variables:

```text
COGNODB_URI
COGNODB_USER
COGNODB_PASSWORD

## Dataset

This benchmark uses the SNAP soc-Pokec social network relationship dataset.

For reproducibility, download the dataset from the official SNAP dataset page and place the required file at:

data/soc-pokec-relationships.txt.gz

The benchmark loads the first 100,000 valid relationships.

Dataset used in this run:
- Nodes: 49,683
- Relationships: 100,000