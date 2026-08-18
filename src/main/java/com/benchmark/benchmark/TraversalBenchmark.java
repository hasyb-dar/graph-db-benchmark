package com.benchmark.benchmark;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class TraversalBenchmark {

    private static final int ITERATIONS = 100;
    private static final int WARMUP_ITERATIONS = 20;

    private final Driver driver;

    public TraversalBenchmark(Driver driver) {
        this.driver = driver;
    }

    public BenchmarkResult run1Hop() {

        String query = """
                MATCH (n)-[r]->(m)
                WHERE id(n) = $nodeId
                RETURN count(m) AS result
                """;

        List<Long> nodeIds = getRandomNodeIds();

        // Warm-up
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executeQuery(query, nodeIds.get(i % nodeIds.size()));
        }

        long[] latencies = new long[ITERATIONS];

        for (int i = 0; i < ITERATIONS; i++) {

            long nodeId = nodeIds.get(i % nodeIds.size());

            long start = System.nanoTime();

            executeQuery(query, nodeId);

            long end = System.nanoTime();

            latencies[i] = end - start;
        }

        double p50 =
                PercentileCalculator.percentile(latencies, 50);

        double p95 =
                PercentileCalculator.percentile(latencies, 95);

        return new BenchmarkResult(
                "CognoDB",
                "1-hop traversal",
                ITERATIONS,
                p50,
                p95
        );
    }

    private void executeQuery(String query, long nodeId) {

        try (Session session = driver.session(
                SessionConfig.forDatabase("neo4j"))) {

            session.run(
                    query,
                    org.neo4j.driver.Values.parameters(
                            "nodeId",
                            nodeId
                    )
            ).consume();
        }
    }

    private List<Long> getRandomNodeIds() {

        String query = """
                MATCH (n)
                RETURN id(n) AS nodeId
                LIMIT 1000
                """;

        List<Long> nodeIds = new ArrayList<>();

        try (Session session = driver.session(
                SessionConfig.forDatabase("neo4j"))) {

            session.run(query).list(record ->
                    record.get("nodeId").asLong()
            ).forEach(nodeIds::add);
        }

        if (nodeIds.isEmpty()) {
            throw new IllegalStateException(
                    "No nodes found. Load the dataset first."
            );
        }

        Collections.shuffle(nodeIds, new Random());

        return nodeIds;
    }
}