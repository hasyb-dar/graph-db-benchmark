package com.benchmark.benchmark;

import com.benchmark.client.ArangoGraphClient;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Mixed read/write concurrency benchmark for ArangoDB, mirroring
 * MemgraphConcurrentBenchmarkRunner / FalkorDBConcurrentBenchmarkRunner:
 * 10 concurrent clients, 80% reads / 20% writes, each client with its
 * own connection.
 */
public class ArangoConcurrentBenchmarkRunner {

    private static final int CONCURRENCY = 10;
    private static final int TOTAL_OPERATIONS = 1_000;
    private static final double READ_RATIO = 0.80;

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
                    "Missing ArangoDB environment variables."
            );
        }

        ArangoGraphClient client =
                new ArangoGraphClient(uri, username, password, "_system");

        try {

            client.connect();

            System.out.println();
            System.out.println("======================================");
            System.out.println("   ARANGODB CONCURRENT READ/WRITE");
            System.out.println("======================================");

            long vertexCount =
                    firstLongResult(
                            client.runQuery(
                                    "RETURN LENGTH(benchmark_vertices)",
                                    null
                            )
                    );

            if (vertexCount == 0) {
                throw new IllegalStateException(
                        "No vertices found. Run ArangoDataLoaderRunner first."
                );
            }

            System.out.println("Concurrency: " + CONCURRENCY);
            System.out.println("Total operations: " + TOTAL_OPERATIONS);
            System.out.println("Read ratio: 80%");
            System.out.println("Write ratio: 20%");
            System.out.println("Vertices available: " + vertexCount);

            System.out.println();
            System.out.println("Warming up...");

            warmup(client, vertexCount);

            System.out.println("Warm-up complete.");

            System.out.println();
            System.out.println("Running concurrent workload...");

            runConcurrentBenchmark(uri, username, password, vertexCount);

            System.out.println();
            System.out.println("======================================");
            System.out.println("   CONCURRENT BENCHMARK COMPLETE");
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

    // ==================================================
    // WARM-UP
    // ==================================================

    private static void warmup(ArangoGraphClient client, long vertexCount) {

        for (int i = 0; i < 20; i++) {

            client.runQuery(
                    "RETURN LENGTH(benchmark_vertices)",
                    null
            );

            long id = 1 + (i % vertexCount);

            client.runQuery(
                    "RETURN DOCUMENT('benchmark_vertices', @key)",
                    Map.of("key", "v" + id)
            );
        }
    }

    // ==================================================
    // CONCURRENT BENCHMARK
    // ==================================================

    private static void runConcurrentBenchmark(
            String uri,
            String username,
            String password,
            long vertexCount
    ) {

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

                ArangoGraphClient threadClient =
                        new ArangoGraphClient(
                                uri, username, password, "_system"
                        );

                try {

                    threadClient.connect();

                    ready.countDown();

                    start.await();

                    for (int i = 0; i < operationsPerClient; i++) {

                        boolean isRead =
                                ((clientId * operationsPerClient + i) % 10) < 8;

                        long nodeId =
                                1 + (
                                        (clientId * operationsPerClient + i)
                                                % vertexCount
                                );

                        long operationStart = System.nanoTime();

                        if (isRead) {
                            runRead(threadClient, nodeId);
                        } else {
                            runWrite(threadClient, nodeId);
                        }

                        long operationEnd = System.nanoTime();

                        long latency = operationEnd - operationStart;

                        synchronized (latencyLock) {
                            operationLatencies.add(latency / 1_000_000);
                        }
                    }

                } catch (Exception e) {

                    System.out.println(
                            "Client " + clientId + " failed: "
                                    + e.getMessage()
                    );

                } finally {

                    threadClient.close();
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

    private static void runRead(ArangoGraphClient client, long nodeId) {

        client.runQuery(
                "RETURN DOCUMENT('benchmark_vertices', @key)",
                Map.of("key", "v" + nodeId)
        );
    }

    private static void runWrite(ArangoGraphClient client, long nodeId) {

        client.runWrite(
                """
                UPDATE @key WITH { benchmark_write: true }
                IN benchmark_vertices
                """,
                Map.of("key", "v" + nodeId)
        );
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