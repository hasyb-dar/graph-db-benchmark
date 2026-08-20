package com.benchmark.benchmark;
import com.benchmark.client.BoltGraphClient;
import com.benchmark.client.GraphClient;
import com.benchmark.config.PlatformConfig;
import java.util.List;
import java.util.Map;
public class PlatformBenchmarkRunner {
    private static final int WARMUP_RUNS = 3;
    private static final int BENCHMARK_RUNS = 10;
    public static void main(String[] args) {
        PlatformConfig config =
                PlatformConfig.fromEnv(
                        "Neo4j",
                        "NEO4J_URI",
                        "NEO4J_USERNAME",
                        "NEO4J_PASSWORD"
                );
        GraphClient client =
                new BoltGraphClient(config);
        try {
            System.out.println();
            System.out.println("======================================");
            System.out.println("       " + config.name());
            System.out.println("======================================");
            // -------------------------------------------------
            // 1. CONNECT
            // -------------------------------------------------
            client.connect();
            System.out.println(
                    "Connected to " + config.name()
                            + " successfully."
            );
            // -------------------------------------------------
            // 2. VERIFY DATASET
            // -------------------------------------------------
            var nodeResult =
                    client.runQuery(
                            "MATCH (n) RETURN count(n) AS nodes",
                            Map.of()
                    );
            var relationshipResult =
                    client.runQuery(
                            "MATCH ()-[r]->() "
                                    + "RETURN count(r) AS relationships",
                            Map.of()
                    );
            System.out.println();
            System.out.println(
                    "Dataset verification:"
            );
            System.out.println(
                    "Nodes: "
                            + nodeResult
            );
            System.out.println(
                    "Relationships: "
                            + relationshipResult
            );
            // -------------------------------------------------
            // 3. TEST QUERIES
            // -------------------------------------------------
            List<BenchmarkQuery> queries =
                    List.of(
                            new BenchmarkQuery(
                                    "Node Count",
                                    "MATCH (n) "
                                            + "RETURN count(n) AS count"
                            ),
                            new BenchmarkQuery(
                                    "Relationship Count",
                                    "MATCH ()-[r]->() "
                                            + "RETURN count(r) AS count"
                            ),
                            new BenchmarkQuery(
                                    "Node Lookup",
                                    "MATCH (n:Person {id: 1}) "
                                            + "RETURN n"
                            ),
                            new BenchmarkQuery(
                                    "1-Hop Traversal",
                                    "MATCH (a:Person {id: 1})"
                                            + "-[:FRIEND]->(b) "
                                            + "RETURN b "
                                            + "LIMIT 20"
                            ),
                            new BenchmarkQuery(
                                    "2-Hop Traversal",
                                    "MATCH (a:Person {id: 1})"
                                            + "-[:FRIEND]->"
                                            + "(b)-[:FRIEND]->(c) "
                                            + "RETURN c "
                                            + "LIMIT 20"
                            )
                    );
            // -------------------------------------------------
            // 4. RUN BENCHMARKS
            // -------------------------------------------------
            System.out.println();
            System.out.println("======================================");
            System.out.println("          BENCHMARK START");
            System.out.println("======================================");
            System.out.println(
                    "Warmup runs: "
                            + WARMUP_RUNS
            );
            System.out.println(
                    "Benchmark runs: "
                            + BENCHMARK_RUNS
            );
            System.out.println();
            for (BenchmarkQuery benchmark :
                    queries) {
                runBenchmark(
                        client,
                        benchmark
                );
            }
            System.out.println();
            System.out.println("======================================");
            System.out.println("        BENCHMARK COMPLETE");
            System.out.println("======================================");
        } finally {
            client.close();
        }
    }
    private static void runBenchmark(
            GraphClient client,
            BenchmarkQuery benchmark) {
        System.out.println();
        System.out.println(
                "Query: "
                        + benchmark.name
        );
        // -------------------------------------------------
        // WARMUP
        // -------------------------------------------------
        for (int i = 0;
             i < WARMUP_RUNS;
             i++) {
            client.runQuery(
                    benchmark.cypher,
                    Map.of()
            );
        }
        // -------------------------------------------------
        // BENCHMARK
        // -------------------------------------------------
        long totalNanos = 0;
        long minNanos =
                Long.MAX_VALUE;
        long maxNanos =
                Long.MIN_VALUE;
        for (int i = 0;
             i < BENCHMARK_RUNS;
             i++) {
            long start =
                    System.nanoTime();
            client.runQuery(
                    benchmark.cypher,
                    Map.of()
            );
            long end =
                    System.nanoTime();
            long elapsed =
                    end - start;
            totalNanos += elapsed;
            minNanos =
                    Math.min(
                            minNanos,
                            elapsed
                    );
            maxNanos =
                    Math.max(
                            maxNanos,
                            elapsed
                    );
        }
        double averageMs =
                totalNanos
                        / (double) BENCHMARK_RUNS
                        / 1_000_000.0;
        double minMs =
                minNanos
                        / 1_000_000.0;
        double maxMs =
                maxNanos
                        / 1_000_000.0;
        // -------------------------------------------------
        // RESULTS
        // -------------------------------------------------
        System.out.printf(
                "Average: %.3f ms%n",
                averageMs
        );
        System.out.printf(
                "Minimum: %.3f ms%n",
                minMs
        );
        System.out.printf(
                "Maximum: %.3f ms%n",
                maxMs
        );
    }
    private static class BenchmarkQuery {
        final String name;
        final String cypher;
        BenchmarkQuery(
                String name,
                String cypher) {
            this.name = name;
            this.cypher = cypher;
        }
    }
}
