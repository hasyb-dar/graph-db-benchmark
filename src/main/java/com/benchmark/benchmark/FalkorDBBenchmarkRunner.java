package com.benchmark.benchmark;

import com.benchmark.client.FalkorDBGraphClient;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Benchmarks FalkorDB against the same :Vertex/:RELATIONSHIP schema
 * used by MemgraphBenchmarkRunner, so results line up in the README's
 * results matrix.
 */
public class FalkorDBBenchmarkRunner {

    private static final int WARMUP_ITERATIONS = 20;
    private static final int MEASURED_ITERATIONS = 100;

    private static final Random RANDOM = new Random(42);

    public static void main(String[] args) {

        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();

        String host = dotenv.get("FALKORDB_HOST");
        String portStr = dotenv.get("FALKORDB_PORT");
        String username = dotenv.get("FALKORDB_USERNAME");
        String password = dotenv.get("FALKORDB_PASSWORD");
        String sslStr = dotenv.get("FALKORDB_SSL");

        if (host == null || host.isBlank()) {
            throw new IllegalStateException(
                    "Missing FALKORDB_HOST environment variable."
            );
        }

        int port = portStr == null || portStr.isBlank()
                ? 6379
                : Integer.parseInt(portStr.trim());

        boolean useSsl =
                sslStr != null && sslStr.trim().equalsIgnoreCase("true");

        FalkorDBGraphClient client =
                new FalkorDBGraphClient(
                        host, port, username, password, useSsl
                );

        try {

            System.out.println();
            System.out.println("======================================");
            System.out.println("       FalkorDB GRAPH BENCHMARK");
            System.out.println("======================================");

            client.connect();

            System.out.println("Connected to FalkorDB successfully.");

            ensureIndex(client);

            long vertexCount =
                    countRows(
                            client,
                            "MATCH (n:Vertex) RETURN count(n) AS c",
                            "c"
                    );

            long edgeCount =
                    countRows(
                            client,
                            "MATCH ()-[r:RELATIONSHIP]->() RETURN count(r) AS c",
                            "c"
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
                        "No Vertex nodes found. Run FalkorDBDataLoaderRunner first."
                );
            }

            System.out.println();
            System.out.println("Warming up FalkorDB...");

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
            System.out.println("       FALKORDB BENCHMARK COMPLETE");
            System.out.println("======================================");

        } finally {

            client.close();
        }
    }

    // ==================================================
    // INDEX
    // ==================================================

    private static void ensureIndex(FalkorDBGraphClient client) {

        try {

            client.runQuery(
                    "CREATE INDEX FOR (n:Vertex) ON (n.id)",
                    null
            );

            System.out.println();
            System.out.println("Ensured index on :Vertex(id).");

        } catch (Exception e) {

            System.out.println();
            System.out.println(
                    "Index creation skipped/failed (may already exist): "
                            + e.getMessage()
            );
        }
    }

    // ==================================================
    // HELPERS
    // ==================================================

    private static long countRows(
            FalkorDBGraphClient client,
            String query,
            String column
    ) {

        var rows = client.runQuery(query, Map.of());

        if (rows.isEmpty()) {
            return 0;
        }

        Object value = rows.get(0).get(column);

        return value instanceof Number number ? number.longValue() : 0;
    }

    private static List<Long> loadRandomNodes(FalkorDBGraphClient client) {

        List<Long> nodes = new ArrayList<>();

        var rows =
                client.runQuery(
                        "MATCH (n:Vertex) RETURN n.id AS id LIMIT 1000",
                        Map.of()
                );

        for (var row : rows) {

            Object id = row.get("id");

            if (id instanceof Number number) {
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
            FalkorDBGraphClient client,
            List<Long> nodes
    ) {

        int count = Math.min(WARMUP_ITERATIONS, nodes.size());

        for (int i = 0; i < count; i++) {

            long nodeId = nodes.get(i);

            client.runQuery(
                    """
                    MATCH (a:Vertex {id: $id})
                          -[:RELATIONSHIP]->
                          (b)
                    RETURN count(b)
                    """,
                    Map.of("id", nodeId)
            );
        }
    }

    // ==================================================
    // 1-HOP / 2-HOP / 3-HOP TRAVERSAL
    // ==================================================

    private static void runTraversalBenchmark(
            FalkorDBGraphClient client,
            String name,
            List<Long> nodes,
            int hops
    ) {

        List<Double> latencies = new ArrayList<>();

        String query;

        if (hops == 1) {

            query =
                    """
                    MATCH (a:Vertex {id: $id})
                          -[:RELATIONSHIP]->
                          (b)
                    RETURN count(b)
                    """;

        } else if (hops == 2) {

            query =
                    """
                    MATCH (a:Vertex {id: $id})
                          -[:RELATIONSHIP]->
                          (b)
                          -[:RELATIONSHIP]->
                          (c)
                    RETURN count(c)
                    """;

        } else {

            query =
                    """
                    MATCH (a:Vertex {id: $id})
                          -[:RELATIONSHIP]->
                          (b)
                          -[:RELATIONSHIP]->
                          (c)
                          -[:RELATIONSHIP]->
                          (d)
                    RETURN count(d)
                    """;
        }

        for (int i = 0; i < MEASURED_ITERATIONS; i++) {

            long nodeId = nodes.get(RANDOM.nextInt(nodes.size()));

            long start = System.nanoTime();

            client.runQuery(query, Map.of("id", nodeId));

            long end = System.nanoTime();

            latencies.add((end - start) / 1_000_000.0);
        }

        printResults(name, latencies);
    }

    // ==================================================
    // POINT LOOKUP
    // ==================================================

    private static void runPointLookupBenchmark(
            FalkorDBGraphClient client,
            List<Long> nodes
    ) {

        List<Double> latencies = new ArrayList<>();

        for (int i = 0; i < MEASURED_ITERATIONS; i++) {

            long nodeId = nodes.get(RANDOM.nextInt(nodes.size()));

            long start = System.nanoTime();

            client.runQuery(
                    "MATCH (n:Vertex {id: $id}) RETURN n.id",
                    Map.of("id", nodeId)
            );

            long end = System.nanoTime();

            latencies.add((end - start) / 1_000_000.0);
        }

        printResults("Point Lookup", latencies);
    }

    // ==================================================
    // INDEXED PROPERTY LOOKUP
    // ==================================================

    private static void runIndexedLookupBenchmark(
            FalkorDBGraphClient client,
            long vertexCount
    ) {

        List<Double> latencies = new ArrayList<>();

        int upperBound = (int) Math.max(1, vertexCount);

        for (int i = 0; i < MEASURED_ITERATIONS; i++) {

            long id = 1 + RANDOM.nextInt(upperBound);

            long start = System.nanoTime();

            client.runQuery(
                    """
                    MATCH (n:Vertex)
                    WHERE n.id = $id
                    RETURN n.id
                    """,
                    Map.of("id", id)
            );

            long end = System.nanoTime();

            latencies.add((end - start) / 1_000_000.0);
        }

        printResults("Indexed Property Lookup", latencies);
    }

    // ==================================================
    // AGGREGATION
    // ==================================================

    private static void runAggregationBenchmark(FalkorDBGraphClient client) {

        List<Double> latencies = new ArrayList<>();

        for (int i = 0; i < MEASURED_ITERATIONS; i++) {

            long start = System.nanoTime();

            client.runQuery(
                    "MATCH (n:Vertex) RETURN count(n)",
                    Map.of()
            );

            long end = System.nanoTime();

            latencies.add((end - start) / 1_000_000.0);
        }

        printResults("Aggregation", latencies);
    }

    // ==================================================
    // PRINT RESULTS
    // ==================================================

    private static void printResults(String name, List<Double> latencies) {

        Collections.sort(latencies);

        double p50 = percentile(latencies, 50);
        double p95 = percentile(latencies, 95);

        double average =
                latencies.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0);

        System.out.println();
        System.out.println("--------------------------------------");
        System.out.println(name);
        System.out.println("Iterations: " + latencies.size());
        System.out.printf("Average: %.3f ms%n", average);
        System.out.printf("p50: %.3f ms%n", p50);
        System.out.printf("p95: %.3f ms%n", p95);
        System.out.println("--------------------------------------");
    }

    // ==================================================
    // PERCENTILE
    // ==================================================

    private static double percentile(List<Double> values, double percentile) {

        if (values.isEmpty()) {
            return 0;
        }

        double index = (percentile / 100.0) * (values.size() - 1);

        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);

        if (lower == upper) {
            return values.get(lower);
        }

        double weight = index - lower;

        return values.get(lower) * (1 - weight)
                + values.get(upper) * weight;
    }
}