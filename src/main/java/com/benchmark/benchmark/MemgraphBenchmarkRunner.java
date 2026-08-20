package com.benchmark.benchmark;

import com.benchmark.client.BoltGraphClient;
import com.benchmark.client.GraphClient;
import com.benchmark.config.PlatformConfig;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Benchmarks Memgraph through the generic GraphClient abstraction
 * (BoltGraphClient speaks Bolt+Cypher, so it works unmodified against
 * CognoDB, Neo4j Aura, and Memgraph).
 *
 * Targets the schema written by MemgraphDataLoader:
 *   (:Vertex {id: <long>})-[:RELATIONSHIP]->(:Vertex)
 *
 * Reports the same metric set as BenchmarkRunner.java (CognoDB) so
 * results line up in the README's results matrix.
 */
public class MemgraphBenchmarkRunner {

    private static final int WARMUP_ITERATIONS = 20;
    private static final int MEASURED_ITERATIONS = 100;

    // Reproducible random selection
    private static final Random RANDOM = new Random(42);

    public static void main(String[] args) {

        PlatformConfig config =
                PlatformConfig.fromEnv(
                        "Memgraph",
                        "MEMGRAPH_URI",
                        "MEMGRAPH_USERNAME",
                        "MEMGRAPH_PASSWORD"
                );

        GraphClient client = new BoltGraphClient(config);

        try {

            System.out.println();
            System.out.println("======================================");
            System.out.println("       " + config.name() + " GRAPH BENCHMARK");
            System.out.println("======================================");

            client.connect();

            System.out.println(
                    "Connected to " + config.name() + " successfully."
            );

            ensureIndex(config);

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
                    "Random start nodes available: "
                            + startNodes.size()
            );

            if (startNodes.isEmpty()) {
                throw new IllegalStateException(
                        "No Vertex nodes found. Run MemgraphDataLoader first."
                );
            }

            // --------------------------------------
            // WARM-UP
            // --------------------------------------

            System.out.println();
            System.out.println("Warming up " + config.name() + "...");

            warmup(client, startNodes);

            System.out.println("Warm-up complete.");

            // --------------------------------------
            // BENCHMARKS
            // --------------------------------------

            System.out.println();
            System.out.println("Running benchmark...");

            runTraversalBenchmark(
                    client, "1-Hop Traversal", startNodes, 1
            );

            runTraversalBenchmark(
                    client, "2-Hop Traversal", startNodes, 2
            );

            runTraversalBenchmark(
                    client, "3-Hop Traversal", startNodes, 3
            );

            runPointLookupBenchmark(client, startNodes);

            runIndexedLookupBenchmark(client, vertexCount);

            runAggregationBenchmark(client);

            System.out.println();
            System.out.println("======================================");
            System.out.println(
                    "       " + config.name().toUpperCase()
                            + " BENCHMARK COMPLETE"
            );
            System.out.println("======================================");

        } finally {

            client.close();
        }
    }

    // ==================================================
    // INDEX
    // ==================================================

    /*
     * Memgraph requires CREATE INDEX to run as an auto-commit
     * (implicit) transaction. BoltGraphClient.runWrite() wraps every
     * write in an explicit transaction via session.executeWrite(),
     * which Memgraph rejects for index DDL. So this opens its own
     * short-lived driver/session and calls session.run() directly,
     * bypassing GraphClient for this one auto-commit statement.
     */
    private static void ensureIndex(PlatformConfig config) {

        Driver driver =
                config.username() == null || config.username().isBlank()
                        ? GraphDatabase.driver(config.uri())
                        : GraphDatabase.driver(
                        config.uri(),
                        AuthTokens.basic(
                                config.username(),
                                config.password()
                        )
                );

        try (Session session = driver.session()) {

            session.run("CREATE INDEX ON :Vertex(id)").consume();

            System.out.println();
            System.out.println("Ensured index on :Vertex(id).");

        } catch (Exception e) {

            System.out.println();
            System.out.println(
                    "Index creation skipped/failed (may already exist): "
                            + e.getMessage()
            );

        } finally {

            driver.close();
        }
    }

    // ==================================================
    // HELPERS
    // ==================================================

    private static long countRows(
            GraphClient client,
            String query,
            String column
    ) {

        var rows = client.runQuery(query, Map.of());

        if (rows.isEmpty()) {
            return 0;
        }

        Object value = rows.get(0).get(column);

        return value instanceof Number number
                ? number.longValue()
                : 0;
    }

    private static List<Long> loadRandomNodes(GraphClient client) {

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

    private static void warmup(GraphClient client, List<Long> nodes) {

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
            GraphClient client,
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
            GraphClient client,
            List<Long> nodes
    ) {

        List<Double> latencies = new ArrayList<>();

        for (int i = 0; i < MEASURED_ITERATIONS; i++) {

            long nodeId = nodes.get(RANDOM.nextInt(nodes.size()));

            long start = System.nanoTime();

            client.runQuery(
                    """
                    MATCH (n:Vertex {id: $id})
                    RETURN n.id
                    """,
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
            GraphClient client,
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

    private static void runAggregationBenchmark(GraphClient client) {

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