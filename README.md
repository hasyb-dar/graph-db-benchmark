# Graph Database Cloud Benchmark

A reproducible benchmark for evaluating **CognoDB Cloud** against managed graph database platforms using the same dataset, workloads, and client environment.

The benchmark is designed to provide an **honest, repeatable comparison** of graph database performance rather than selecting a predetermined winner.

---

## 1. Objective

This project evaluates graph database performance across common graph workloads.

The benchmark measures:

* Data loading throughput
* 1-hop traversal latency
* 2-hop traversal latency
* 3-hop traversal latency
* Point lookup latency
* Indexed property lookup latency
* Aggregation latency
* Concurrent read/write throughput
* p50 latency
* p95 latency

---

## 2. Benchmark Architecture

```text
                    ┌──────────────────────┐
                    │  Benchmark Runner    │
                    │       (Java)         │
                    └──────────┬───────────┘
                               │
                 ┌─────────────┼─────────────┐
                 │             │             │
                 ▼             ▼             ▼
          ┌────────────┐ ┌────────────┐ ┌────────────┐
          │  CognoDB   │ │   Neo4j    │ │  ArangoDB  │
          │   Cloud    │ │   Aura     │ │   Cloud    │
          └────────────┘ └────────────┘ └────────────┘
                               │
                               ▼
                         ┌────────────┐
                         │ Memgraph / │
                         │ FalkorDB   │
                         └────────────┘
```

Each database is accessed through its corresponding Java client/driver.

The benchmark runner executes the same logical workloads against each platform.

---

## 3. Dataset

### SNAP soc-Pokec

The benchmark uses the **SNAP soc-Pokec social network dataset**.

Source:

https://snap.stanford.edu/data/

Dataset:

`soc-Pokec`

Input file:

```text
soc-pokec-relationships.txt.gz
```

The original dataset is substantially larger than required for free/entry-level cloud database tiers.

Therefore, this benchmark uses a controlled subset.

### Dataset used in this benchmark

* **Nodes:** 49,683
* **Relationships:** 100,000
* **Node label:** `Person`
* **Relationship type:** `FRIEND`

Only the first **100,000 valid relationships** are loaded.

This keeps the benchmark small enough to run consistently on free or entry-level database tiers while still providing a meaningful graph workload.

---

## 4. Dataset Reproduction

Download the dataset from the official SNAP website:

https://snap.stanford.edu/data/

Place the downloaded file at:

```text
data/soc-pokec-relationships.txt.gz
```

Expected project structure:

```text
graph-database-cloud-benchmark/
│
├── data/
│   └── soc-pokec-relationships.txt.gz
│
├── src/
│   └── ...
│
├── pom.xml
│
└── README.md
```

The benchmark loader reads the dataset and loads the first 100,000 valid relationships.

---

## 5. Cloud Database Configuration

Database credentials are supplied through environment variables.

**Credentials are NOT stored in this repository.**

### CognoDB Cloud

Set:

```text
COGNODB_URI
COGNODB_USER
COGNODB_PASSWORD
```

Example:

```text
COGNODB_URI=bolt+s://<your-cognodb-host>
COGNODB_USER=<your-username>
COGNODB_PASSWORD=<your-password>
```

### Neo4j Aura

Set:

```text
NEO4J_URI
NEO4J_USER
NEO4J_PASSWORD
```

Example:

```text
NEO4J_URI=neo4j+s://<your-neo4j-host>
NEO4J_USER=neo4j
NEO4J_PASSWORD=<your-password>
```

### ArangoDB

If the ArangoDB benchmark is enabled, configure:

```text
ARANGO_URI
ARANGO_USER
ARANGO_PASSWORD
ARANGO_DATABASE
```

Example:

```text
ARANGO_URI=<your-arangodb-host>
ARANGO_USER=<your-username>
ARANGO_PASSWORD=<your-password>
ARANGO_DATABASE=<your-database>
```

### Memgraph

If the Memgraph benchmark is enabled, configure the corresponding connection variables required by the client implementation.

### FalkorDB

If the FalkorDB benchmark is enabled, configure the corresponding connection variables required by the client implementation.

---

## 6. Security

Never commit database credentials to Git.

Do **not** add passwords directly to Java source code.

Do not commit files containing:

```text
COGNODB_PASSWORD=...
NEO4J_PASSWORD=...
ARANGO_PASSWORD=...
```

If using a local `.env` file, add it to `.gitignore`:

```text
.env
```

For production or CI environments, credentials should be supplied through environment variables or a secure secrets manager.

---

## 7. Requirements

The benchmark requires:

* Java 17 or compatible Java version
* Maven
* Internet access
* Access to the configured cloud databases
* Appropriate database credentials
* SNAP soc-Pokec dataset

Verify Java:

```bash
java -version
```

Verify Maven:

```bash
mvn -version
```

---

## 8. Build the Project

Clone the repository:

```bash
git clone <repository-url>
```

Enter the project directory:

```bash
cd graph-database-cloud-benchmark
```

Build the project:

```bash
mvn clean package
```

---

## 9. Running the Benchmark

Before running the benchmark, configure the required environment variables.

### Linux / macOS

Example:

```bash
export COGNODB_URI="bolt+s://<your-host>"
export COGNODB_USER="<your-user>"
export COGNODB_PASSWORD="<your-password>"
```

For Neo4j:

```bash
export NEO4J_URI="neo4j+s://<your-host>"
export NEO4J_USER="neo4j"
export NEO4J_PASSWORD="<your-password>"
```

### Windows PowerShell

```powershell
$env:COGNODB_URI="bolt+s://<your-host>"
$env:COGNODB_USER="<your-user>"
$env:COGNODB_PASSWORD="<your-password>"
```

For Neo4j:

```powershell
$env:NEO4J_URI="neo4j+s://<your-host>"
$env:NEO4J_USER="neo4j"
$env:NEO4J_PASSWORD="<your-password>"
```

---

## 10. Connectivity Test

Before running the full benchmark, each database connection should be verified.

The benchmark performs a simple connectivity test such as:

```cypher
RETURN 1 AS test
```

A successful connection should produce output similar to:

```text
======================================
       Neo4j
======================================

Connected to Neo4j successfully.

Test query result:
[{test=1}]
```

If authentication fails, verify:

1. The URI is correct.
2. The username is correct.
3. The password is correct.
4. The environment variables are actually set.
5. The database is running.
6. The database accepts the configured protocol.
7. The credentials belong to the database specified by the URI.

---

## 11. Benchmark Workloads

The benchmark executes equivalent logical workloads against each database.

### 11.1 Data Loading

Measures the time required to load:

```text
49,683 nodes
100,000 relationships
```

The result is reported as loading throughput and can be expressed as relationships per second.

---

### 11.2 1-Hop Traversal

Measures the latency of retrieving directly connected nodes.

Conceptually:

```text
Person
  │
  └── FRIEND
        │
        ▼
      Person
```

---

### 11.3 2-Hop Traversal

Measures traversal through two relationship levels.

```text
Person
  │
  ▼
Person
  │
  ▼
Person
```

---

### 11.4 3-Hop Traversal

Measures traversal through three relationship levels.

```text
Person
  │
  ▼
Person
  │
  ▼
Person
  │
  ▼
Person
```

---

### 11.5 Point Lookup

Measures the latency of retrieving a node using a known identifier.

Example:

```cypher
MATCH (p:Person {id: $id})
RETURN p
```

---

### 11.6 Indexed Property Lookup

Measures lookup performance using an indexed property.

Example:

```cypher
MATCH (p:Person {id: $id})
RETURN p
```

The benchmark ensures that equivalent indexes are created where supported.

---

### 11.7 Aggregation

Measures aggregation performance over the graph.

Examples include:

* Node counts
* Relationship counts
* Degree calculations
* Grouped aggregations

---

### 11.8 Concurrent Read/Write Workload

Measures database behavior under concurrent operations.

The benchmark executes multiple operations concurrently and records:

* Throughput
* p50 latency
* p95 latency

This workload evaluates database behavior under contention rather than single-query performance only.

---

## 12. Latency Metrics

The benchmark reports percentile-based latency.

### p50

p50 represents the median latency.

50% of requests complete at or below this latency.

### p95

p95 represents the latency at which 95% of requests have completed.

Only 5% of requests take longer than the reported p95 value.

For example:

```text
p50 = 8 ms
p95 = 25 ms
```

means that approximately 50% of requests completed within 8 ms and 95% completed within 25 ms.

---

# 13. Benchmark Methodology

To make the comparison meaningful, the following principles are used.

### Same Dataset

Every database receives the same dataset subset:

```text
49,683 nodes
100,000 relationships
```

### Same Logical Workloads

The benchmark executes equivalent graph operations on every platform.

### Same Client Environment

The database clients run from the same benchmark machine/environment.

### Warm-up

Warm-up operations may be executed before measurements to reduce the effect of initial connection and initialization overhead.

### Multiple Iterations

Individual workloads are executed for **100 iterations**.

The concurrent read/write workload uses:

```text
10 clients
1,000 operations
```

### Percentile Reporting

Latency is reported using p50 and p95 rather than only average latency.

---

# 14. Important Comparison Considerations

This benchmark is intended as a practical cloud comparison.

Performance can be affected by:

* Cloud region
* Database tier
* CPU allocation
* Memory allocation
* Storage configuration
* Query planner behavior
* Index configuration
* Connection pooling
* Concurrent workload
* Dataset size
* Database version
* Network latency

Therefore, benchmark results should be interpreted in the context of the tested environments.

The results should not be interpreted as a universal ranking of the database technologies.

---

# 15. Benchmark Results

The following results were obtained from the benchmark runs.

All latency values are reported in milliseconds.

## 15.1 Individual Workloads

| Platform   | Workload                | Iterations | Average (ms) | p50 (ms) | p95 (ms) |
| ---------- | ----------------------- | ---------: | -----------: | -------: | -------: |
| Memgraph   | 1-Hop Traversal         |        100 |      331.947 |  331.817 |  334.363 |
| Memgraph   | 2-Hop Traversal         |        100 |      333.730 |  330.206 |  333.829 |
| Memgraph   | 3-Hop Traversal         |        100 |      333.550 |  331.597 |  339.766 |
| Memgraph   | Point Lookup            |        100 |      329.967 |  329.898 |  332.287 |
| Memgraph   | Indexed Property Lookup |        100 |      333.106 |  329.946 |  333.259 |
| Memgraph   | Aggregation             |        100 |      336.770 |  336.284 |  339.981 |
| FalkorDB   | 1-Hop Traversal         |        100 |       45.539 |   37.996 |   42.296 |
| FalkorDB   | 2-Hop Traversal         |        100 |       55.608 |   37.880 |  283.521 |
| FalkorDB   | 3-Hop Traversal         |        100 |       38.025 |   38.081 |   39.491 |
| FalkorDB   | Point Lookup            |        100 |       37.622 |   37.636 |   39.226 |
| FalkorDB   | Indexed Property Lookup |        100 |       37.584 |   37.532 |   40.238 |
| FalkorDB   | Aggregation             |        100 |       37.451 |   37.472 |   38.502 |
| ArangoDB   | 1-Hop Traversal         |        100 |      296.188 |  288.205 |  355.018 |
| ArangoDB   | 2-Hop Traversal         |        100 |      302.708 |  288.647 |  360.878 |
| ArangoDB   | 3-Hop Traversal         |        100 |      323.146 |  294.656 |  416.666 |
| ArangoDB   | Point Lookup            |        100 |      289.017 |  286.329 |  292.101 |
| ArangoDB   | Indexed Property Lookup |        100 |      297.681 |  286.940 |  357.635 |
| ArangoDB   | Aggregation             |        100 |      291.766 |  287.535 |  319.060 |
| Neo4j Aura | 1-Hop Traversal         |        100 |      225.590 |  213.259 |  223.671 |
| Neo4j Aura | 2-Hop Traversal         |        100 |      217.272 |  212.937 |  219.449 |
| Neo4j Aura | 3-Hop Traversal         |        100 |      219.950 |  213.167 |  223.413 |
| Neo4j Aura | Point Lookup            |        100 |      219.134 |  212.172 |  219.807 |
| Neo4j Aura | Indexed Property Lookup |        100 |      205.846 |  212.582 |  217.646 |
| Neo4j Aura | Aggregation             |        100 |      211.519 |  212.065 |  216.340 |

---

## 15.2 Concurrent Read/Write Workload

The concurrent workload used **10 clients** and **1,000 operations**.

| Platform   | Clients | Operations | p50 (ms) | p95 (ms) | Throughput (ops/sec) |
| ---------- | ------: | ---------: | -------: | -------: | -------------------: |
| Memgraph   |      10 |      1,000 |      357 |      727 |               21.132 |
| FalkorDB   |      10 |      1,000 |       39 |       42 |              237.373 |
| ArangoDB   |      10 |      1,000 |      296 |      315 |               32.471 |
| Neo4j Aura |      10 |      1,000 |      186 |      215 |               49.319 |

---

## 15.3 Results Summary

Based on these benchmark runs:

* **FalkorDB** achieved the lowest p50 latency for the tested individual workloads.
* **FalkorDB** also achieved the highest concurrent read/write throughput.
* **Neo4j Aura** showed relatively consistent latency across traversal, lookup, and aggregation workloads.
* **ArangoDB** showed higher latency than FalkorDB and Neo4j Aura for the tested workloads.
* **Memgraph** showed the highest latency among the tested platforms for most individual workloads.
* FalkorDB achieved **237.373 ops/sec** in the concurrent workload.
* Neo4j Aura achieved **49.319 ops/sec**.
* ArangoDB achieved **32.471 ops/sec**.
* Memgraph achieved **21.132 ops/sec**.

---

## 15.4 Best Observed Results

| Metric                      | Best Platform |          Result |
| --------------------------- | ------------- | --------------: |
| 1-Hop Traversal p50         | FalkorDB      |       37.996 ms |
| 2-Hop Traversal p50         | FalkorDB      |       37.880 ms |
| 3-Hop Traversal p50         | FalkorDB      |       38.081 ms |
| Point Lookup p50            | FalkorDB      |       37.636 ms |
| Indexed Property Lookup p50 | FalkorDB      |       37.532 ms |
| Aggregation p50             | FalkorDB      |       37.472 ms |
| Concurrent Throughput       | FalkorDB      | 237.373 ops/sec |
| Lowest Concurrent p95       | FalkorDB      |           42 ms |

---

## 15.5 CognoDB Results

CognoDB Cloud is one of the primary targets of this benchmark.

However, the current benchmark results file does not contain a completed CognoDB Cloud benchmark run.

Therefore, **no CognoDB performance numbers are invented or estimated**.

Once the CognoDB benchmark is successfully executed using the same:

* Dataset
* Workloads
* Iteration counts
* Client environment
* Benchmark methodology

its results can be added to the comparison tables.

---

# 16. Project Structure

A typical project structure is:

```text
graph-database-cloud-benchmark/
│
├── data/
│   └── soc-pokec-relationships.txt.gz
│
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── benchmark/
│                   ├── benchmark/
│                   ├── client/
│                   └── config/
│
├── pom.xml
├── .gitignore
└── README.md
```

---

# 17. Environment Variables

The benchmark uses environment variables rather than hard-coded credentials.

```text
COGNODB_URI
COGNODB_USER
COGNODB_PASSWORD

NEO4J_URI
NEO4J_USER
NEO4J_PASSWORD

ARANGO_URI
ARANGO_USER
ARANGO_PASSWORD
ARANGO_DATABASE
```

Additional environment variables can be configured for Memgraph and FalkorDB according to their client implementations.

Only configure the variables for the databases being benchmarked.

---

# 18. Reproducibility

To reproduce the benchmark:

1. Clone the repository.
2. Download the SNAP soc-Pokec dataset.
3. Place the dataset under `data/`.
4. Configure the database environment variables.
5. Build the project using Maven.
6. Verify database connectivity.
7. Load the dataset.
8. Run the benchmark workloads.
9. Record p50, p95, throughput, and loading metrics.
10. Repeat the benchmark under the same conditions when comparing results.

---

# 19. Fairness and Limitations

This benchmark does not claim that one graph database is universally faster than another.

It measures performance under the specific:

* Dataset
* Cloud configuration
* Database tier
* Client environment
* Workload
* Network conditions
* Query/index configuration

used in the experiment.

The purpose is to provide transparent and reproducible measurements.

Differences in cloud regions, database tiers, network conditions, query implementations, indexing strategies, and database versions may affect the results.

---

# 20. Conclusion

This project provides a common benchmark framework for evaluating managed graph databases using the SNAP soc-Pokec dataset.

The benchmark focuses on practical graph workloads including:

* Traversals
* Point lookups
* Indexed lookups
* Aggregations
* Data loading
* Concurrent operations

The current benchmark results show that **FalkorDB achieved the strongest performance across the tested workloads**, particularly in latency and concurrent throughput.

**Neo4j Aura** demonstrated relatively consistent performance across the tested graph operations.

**ArangoDB** and **Memgraph** showed higher latency under the tested conditions.

These results should be interpreted only within the specific benchmark environment and configuration used. They are not intended to represent a universal ranking of graph database technologies.

CognoDB Cloud remains a primary benchmark target, and its results should be added after completing a comparable run using the same methodology.

---

## Dataset Attribution

The benchmark dataset is sourced from the **Stanford Network Analysis Project (SNAP)**.

Dataset:

**soc-Pokec**

SNAP:

https://snap.stanford.edu/data/

---
