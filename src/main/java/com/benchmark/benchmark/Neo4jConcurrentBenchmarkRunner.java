package com.benchmark.benchmark;

import io.github.cdimascio.dotenv.Dotenv;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Mixed read/write concurrency benchmark for Neo4j Aura, mirroring
 * ConcurrentBenchmarkRunner.java (CognoDB) with the same Person/FRIEND
 * schema and 10-client, 80/20 read/write pattern used across all
 * platforms.
 */
public class Neo4jConcurrentBenchmarkRunner {

    private static final int CONCURRENCY = 10;
    private static final int TOTAL_OPERATIONS = 1_000;
    private static final double READ_RATIO = 0.80;
    private static final int PERSON_COUNT = 49_683;

    public static void main(String[] args) {

        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .load();

        String uri = dotenv.get("NEO4J_URI");
        String username = dotenv.get("NEO4J_USERNAME");
        String password = dotenv.get("NEO4J_PASSWORD");

        if (uri == null || uri.isBlank()
                || username == null || username.isBlank()
                || password == null || password.isBlank()) {

            throw new IllegalStateException(
                    "Missing Neo4j environment variables."
            );
        }

        try (Driver driver = GraphDatabase.driver(
                uri,
                AuthTokens.basic(username, password)
        )) {

            driver.verifyConnectivity();

            System.out.println();
            System.out.println("======================================");
            System.out.println("   NEO4J AURA CONCURRENT READ/WRITE");
            System.out.println("======================================");

            System.out.println("Concurrency: " + CONCURRENCY);
            System.out.println("Total operations: " + TOTAL_OPERATIONS);
            System.out.println("Read ratio: 80%");
            System.out.println("Write ratio: 20%");

            System.out.println();
            System.out.println("Warming up...");

            warmup(driver);

            System.out.println("Warm-up complete.");

            System.out.println();
            System.out.println("Running concurrent workload...");

            runConcurrentBenchmark(driver);

            System.out.println();
            System.out.println("======================================");
            System.out.println("   CONCURRENT BENCHMARK COMPLETE");
            System.out.println("======================================");
        }
    }

    // ==================================================
    // WARM-UP
    // ==================================================

    private static void warmup(Driver driver) {

        try (Session session = driver.session()) {

            for (int i = 0; i < 20; i++) {

                session.run(
                        """
                        MATCH (n:Person)
                        RETURN count(n)
                        """
                ).consume();

                long id = 1 + (i % PERSON_COUNT);

                session.run(
                        """
                        MATCH (n:Person {id: $id})
                        RETURN n.id
                        """,
                        Values.parameters("id", id)
                ).consume();
            }
        }
    }

    // ==================================================
    // CONCURRENT BENCHMARK
    // ==================================================

    private static void runConcurrentBenchmark(Driver driver) {

        ExecutorService executor =
                Executors.newFixedThreadPool(CONCURRENCY);

        CountDownLatch ready = new CountDownLatch(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(CONCURRENCY);

        List<Long> operationLatencies = new ArrayList<>();
        Object latencyLock = new Object();

        int operationsPerClient = TOTAL_OPERATIONS / CONCURRENCY;

        for (int client = 0; client < CONCURRENCY; client++) {

            final int clientId = client;

            executor.submit(() -> {

                try {

                    ready.countDown();

                    start.await();

                    try (Session session = driver.session()) {

                        for (int i = 0; i < operationsPerClient; i++) {

                            boolean isRead =
                                    ((clientId * operationsPerClient + i) % 10) < 8;

                            long nodeId =
                                    1 + (
                                            (clientId * operationsPerClient + i)
                                                    % PERSON_COUNT
                                    );

                            long operationStart = System.nanoTime();

                            if (isRead) {
                                runRead(session, nodeId);
                            } else {
                                runWrite(session, nodeId);
                            }

                            long operationEnd = System.nanoTime();

                            long latency = operationEnd - operationStart;

                            synchronized (latencyLock) {
                                operationLatencies.add(latency / 1_000_000);
                            }
                        }
                    }

                } catch (Exception e) {

                    System.out.println(
                            "Client " + clientId + " failed: "
                                    + e.getMessage()
                    );

                } finally {

                    finished.countDown();
                }
            });
        }

        try {

            ready.await();

            long startTime = System.nanoTime();

            start.countDown();

            finished.await();

            long endTime = System.nanoTime();

            double seconds = (endTime - startTime) / 1_000_000_000.0;

            double throughput = TOTAL_OPERATIONS / seconds;

            System.out.println();
            System.out.println("--------------------------------------");
            System.out.println("Concurrent Read/Write Results");
            System.out.println("Clients: " + CONCURRENCY);
            System.out.println("Operations: " + TOTAL_OPERATIONS);
            System.out.printf("Wall-clock time: %.3f seconds%n", seconds);
            System.out.printf(
                    "Throughput: %.3f operations/second%n", throughput
            );
            System.out.println("Read operations: 80%");
            System.out.println("Write operations: 20%");

            printLatencyResults(operationLatencies);

            System.out.println("--------------------------------------");

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new RuntimeException(
                    "Concurrent benchmark interrupted", e
            );

        } finally {

            executor.shutdown();

            try {

                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }

            } catch (InterruptedException e) {

                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // ==================================================
    // READ / WRITE OPERATIONS
    // ==================================================

    private static void runRead(Session session, long nodeId) {

        session.run(
                """
                MATCH (n:Person {id: $id})
                RETURN n.id
                """,
                Values.parameters("id", nodeId)
        ).consume();
    }

    private static void runWrite(Session session, long nodeId) {

        session.run(
                """
                MERGE (n:Person {id: $id})
                SET n.benchmark_write = true
                RETURN n.id
                """,
                Values.parameters("id", nodeId)
        ).consume();
    }

    // ==================================================
    // LATENCY RESULTS
    // ==================================================

    private static void printLatencyResults(List<Long> latencies) {

        if (latencies.isEmpty()) {
            System.out.println("No operation latency data recorded.");
            return;
        }

        List<Long> sorted = new ArrayList<>(latencies);
        sorted.sort(Long::compareTo);

        double p50 = percentile(sorted, 50);
        double p95 = percentile(sorted, 95);

        System.out.printf("Operation latency p50: %.3f ms%n", p50);
        System.out.printf("Operation latency p95: %.3f ms%n", p95);
    }

    private static double percentile(List<Long> values, double percentile) {

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