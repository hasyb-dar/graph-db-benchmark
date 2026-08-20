package com.benchmark.benchmark;

import com.benchmark.client.FalkorDBGraphClient;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Mixed read/write concurrency benchmark for FalkorDB, mirroring
 * MemgraphConcurrentBenchmarkRunner but via FalkorDBGraphClient and the
 * jfalkordb (Jedis-backed) client instead of a Bolt driver.
 */
public class FalkorDBConcurrentBenchmarkRunner {

    // Assignment asks for a stated client concurrency.
    private static final int CONCURRENCY = 10;

    // Total operations performed by all clients.
    private static final int TOTAL_OPERATIONS = 1_000;

    // 80% reads, 20% writes.
    private static final double READ_RATIO = 0.80;

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

            client.connect();

            System.out.println();
            System.out.println("======================================");
            System.out.println("   FALKORDB CONCURRENT READ/WRITE");
            System.out.println("======================================");

            long vertexCount =
                    countRows(
                            client,
                            "MATCH (n:Vertex) RETURN count(n) AS c",
                            "c"
                    );

            if (vertexCount == 0) {
                throw new IllegalStateException(
                        "No Vertex nodes found. Run FalkorDBDataLoaderRunner first."
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

            runConcurrentBenchmark(
                    host, port, username, password, useSsl, vertexCount
            );

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

    // ==================================================
    // WARM-UP
    // ==================================================

    private static void warmup(
            FalkorDBGraphClient client,
            long vertexCount
    ) {

        for (int i = 0; i < 20; i++) {

            client.runQuery(
                    "MATCH (n:Vertex) RETURN count(n)",
                    Map.of()
            );

            long id = 1 + (i % vertexCount);

            client.runQuery(
                    "MATCH (n:Vertex {id: $id}) RETURN n.id",
                    Map.of("id", id)
            );
        }
    }

    // ==================================================
    // CONCURRENT BENCHMARK
    // ==================================================

    private static void runConcurrentBenchmark(
            String host,
            int port,
            String username,
            String password,
            boolean useSsl,
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

        // Each thread gets its own FalkorDBGraphClient (own Jedis pool)
        // so clients don't block each other.
        for (int client = 0; client < CONCURRENCY; client++) {

            final int clientId = client;

            executor.submit(() -> {

                FalkorDBGraphClient threadClient =
                        new FalkorDBGraphClient(
                                host, port, username, password, useSsl
                        );

                try {

                    threadClient.connect();

                    ready.countDown();

                    // Wait until every client is ready.
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
    // READ OPERATION
    // ==================================================

    private static void runRead(FalkorDBGraphClient client, long nodeId) {

        client.runQuery(
                "MATCH (n:Vertex {id: $id}) RETURN n.id",
                Map.of("id", nodeId)
        );
    }

    // ==================================================
    // WRITE OPERATION
    // ==================================================

    private static void runWrite(FalkorDBGraphClient client, long nodeId) {

        client.runWrite(
                """
                MATCH (n:Vertex {id: $id})
                SET n.benchmark_write = true
                RETURN n.id
                """,
                Map.of("id", nodeId)
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

    // ==================================================
    // PERCENTILE
    // ==================================================

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