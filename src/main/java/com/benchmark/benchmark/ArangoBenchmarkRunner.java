package com.benchmark.benchmark;

import com.benchmark.client.ArangoGraphClient;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Benchmarks ArangoDB against the same logical query shapes used by
 * BenchmarkRunner.java (CognoDB), MemgraphBenchmarkRunner, and
 * FalkorDBBenchmarkRunner:
 *   - Traversals return a COUNT, not full vertex objects, so all
 *     platforms are paying for the same amount of work (explore the
 *     paths) without one platform also paying to materialize and
 *     transfer thousands of full documents that the others don't.
 *   - Random start nodes, 100 measured iterations after a 20-iteration
 *     warm-up, p50/p95 reported in milliseconds (not raw nanoseconds).
 *
 * The previous version of this class returned full vertex objects with
 * NO limit on the 1..3 hop traversal, which let 3-hop fan-out on a
 * densely connected social graph balloon to several seconds -- an
 * artifact of the query shape, not of ArangoDB itself. Fixed here.
 */
public class ArangoBenchmarkRunner {

    private static final int WARMUP_ITERATIONS = 20;
    private static final int MEASURED_ITERATIONS = 100;

    private static final Random RANDOM = new Random(42);

    public static void main(String[] args) {

        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .load();

        String uri = dotenv.get("ARANGO_URI");
        String username = dotenv.get("ARANGO_USERNAME");
        String password = dotenv.get("ARANGO_PASSWORD");

        if (uri == null || uri.isBlank()
                || username == null || username.isBlank()
                || password == null || password.isBlank()) {

            throw new IllegalStateException(
                    "Missing ArangoDB environment variables:\n"
                            + "ARANGO_URI\nARANGO_USERNAME\nARANGO_PASSWORD"
            );
        }

        ArangoGraphClient client =
                new ArangoGraphClient(uri, username, password, "_system");

        try {

            System.out.println();
            System.out.println("======================================");
            System.out.println("       ArangoDB GRAPH BENCHMARK");
            System.out.println("======================================");

            client.connect();

            System.out.println("Connection successful.");

            // Index needed for the "indexed property lookup" benchmark.
            // benchmark_vertices._key already has an automatic primary
            // index (used by the point-lookup benchmark below); node_id
            // does not, so it needs to be created explicitly.
            client.ensurePersistentIndex(
                    "benchmark_vertices",
                    List.of("node_id")
            );

            long vertexCount =
                    firstLongResult(
                            client.runQuery(
                                    "RETURN LENGTH(benchmark_vertices)",
                                    null
                            )
                    );

            long edgeCount =
                    firstLongResult(
                            client.runQuery(
                                    "RETURN LENGTH(benchmark_edges)",
                                    null
                            )
                    );

            System.out.println();
            System.out.println("Vertex count: " + vertexCount);
            System.out.println("Edge count: " + edgeCount);

            List<Long> startNodes = loadRandomNodes(client);

            System.out.println(
                    "Random start nodes available: " + startNodes.size()
            );

            if (startNodes.isEmpty()) {
                throw new IllegalStateException(
                        "No vertices found. Run ArangoDataLoaderRunner first."
                );
            }

            System.out.println();
            System.out.println("Warming up ArangoDB...");

            warmup(client, startNodes);

            System.out.println("Warm-up complete.");

            System.out.println();
            System.out.println("Running benchmark...");

            runTraversalBenchmark(client, "1-Hop Traversal", startNodes, 1);
            runTraversalBenchmark(client, "2-Hop Traversal", startNodes, 2);
            runTraversalBenchmark(client, "3-Hop Traversal", startNodes, 3);

            runPointLookupBenchmark(client, startNodes);
            runIndexedLookupBenchmark(client, vertexCount);
            runAggregationBenchmark(client);

            System.out.println();
            System.out.println("======================================");
            System.out.println("       ARANGODB BENCHMARK COMPLETE");
            System.out.println("======================================");

        } finally {

            client.close();
        }
    }

    // ==================================================
    // HELPERS
    // ==================================================

    private static long firstLongResult(List<Map<String, Object>> rows) {

        if (rows.isEmpty()) {
            return 0;
        }

        Object value = rows.get(0).get("result");

        return value instanceof Number number ? number.longValue() : 0;
    }

    private static List<Long> loadRandomNodes(ArangoGraphClient client) {

        List<Long> nodes = new ArrayList<>();

        var rows =
                client.runQuery(
                        "FOR v IN benchmark_vertices "
                                + "LIMIT 1000 "
                                + "RETURN v.node_id",
                        null
                );

        for (var row : rows) {

            Object value = row.get("result");

            if (value instanceof Number number) {
                nodes.add(number.longValue());
            }
        }

        Collections.shuffle(nodes, RANDOM);

        return nodes;
    }

    // ==================================================
    // WARM-UP
    // ==================================================

    private static void warmup(
            ArangoGraphClient client,
            List<Long> nodes
    ) {

        int count = Math.min(WARMUP_ITERATIONS, nodes.size());

        for (int i = 0; i < count; i++) {

            String startVertex = "benchmark_vertices/v" + nodes.get(i);

            client.measureQueryTime(
                    """
                    WITH benchmark_vertices
                    RETURN LENGTH(
                        FOR v, e, p IN 1..1 OUTBOUND @start benchmark_edges
                        RETURN 1
                    )
                    """,
                    Map.of("start", startVertex)
            );
        }
    }

    // ==================================================
    // 1-HOP / 2-HOP / 3-HOP TRAVERSAL
    // ==================================================

    private static void runTraversalBenchmark(
            ArangoGraphClient client,
            String name,
            List<Long> nodes,
            int hops
    ) {

        String query = """
                WITH benchmark_vertices
                RETURN LENGTH(
                    FOR v, e, p IN 1..%d OUTBOUND @start benchmark_edges
                    RETURN 1
                )
                """.formatted(hops);

        long[] latencies = new long[MEASURED_ITERATIONS];

        for (int i = 0; i < MEASURED_ITERATIONS; i++) {

            long nodeId = nodes.get(RANDOM.nextInt(nodes.size()));

            String startVertex = "benchmark_vertices/v" + nodeId;

            latencies[i] =
                    client.measureQueryTime(
                            query,
                            Map.of("start", startVertex)
                    );
        }

        printResults(name, latencies);
    }

    // ==================================================
    // POINT LOOKUP (primary index on _key)
    // ==================================================

    private static void runPointLookupBenchmark(
            ArangoGraphClient client,
            List<Long> nodes
    ) {

        long[] latencies = new long[MEASURED_ITERATIONS];

        for (int i = 0; i < MEASURED_ITERATIONS; i++) {

            long nodeId = nodes.get(RANDOM.nextInt(nodes.size()));

            String key = "v" + nodeId;

            latencies[i] =
                    client.measureQueryTime(
                            "RETURN DOCUMENT('benchmark_vertices', @key)",
                            Map.of("key", key)
                    );
        }

        printResults("Point Lookup", latencies);
    }

    // ==================================================
    // INDEXED PROPERTY LOOKUP (persistent index on node_id)
    // ==================================================

    private static void runIndexedLookupBenchmark(
            ArangoGraphClient client,
            long vertexCount
    ) {

        long[] latencies = new long[MEASURED_ITERATIONS];

        int upperBound = (int) Math.max(1, vertexCount);

        for (int i = 0; i < MEASURED_ITERATIONS; i++) {

            long id = 1 + RANDOM.nextInt(upperBound);

            latencies[i] =
                    client.measureQueryTime(
                            """
                            FOR v IN benchmark_vertices
                            FILTER v.node_id == @id
                            RETURN v.node_id
                            """,
                            Map.of("id", id)
                    );
        }

        printResults("Indexed Property Lookup", latencies);
    }

    // ==================================================
    // AGGREGATION
    // ==================================================

    private static void runAggregationBenchmark(ArangoGraphClient client) {

        long[] latencies = new long[MEASURED_ITERATIONS];

        for (int i = 0; i < MEASURED_ITERATIONS; i++) {

            latencies[i] =
                    client.measureQueryTime(
                            "RETURN LENGTH(benchmark_vertices)",
                            null
                    );
        }

        printResults("Aggregation", latencies);
    }

    // ==================================================
    // PRINT RESULTS (nanoseconds -> milliseconds)
    // ==================================================

    private static void printResults(String name, long[] times) {

        long[] sorted = times.clone();
        java.util.Arrays.sort(sorted);

        long total = 0;

        for (long time : sorted) {
            total += time;
        }

        double averageMs = (total / (double) sorted.length) / 1_000_000.0;

        double p50Ms = sorted[sorted.length / 2] / 1_000_000.0;

        int p95Index =
                (int) Math.ceil(0.95 * sorted.length) - 1;

        double p95Ms = sorted[p95Index] / 1_000_000.0;

        System.out.println();
        System.out.println("--------------------------------------");
        System.out.println(name);
        System.out.println("Iterations: " + sorted.length);
        System.out.printf("Average: %.3f ms%n", averageMs);
        System.out.printf("p50: %.3f ms%n", p50Ms);
        System.out.printf("p95: %.3f ms%n", p95Ms);
        System.out.println("--------------------------------------");
    }
}