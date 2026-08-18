package com.benchmark.benchmark;

import io.github.cdimascio.dotenv.Dotenv;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class BenchmarkRunner {

    private static final int WARMUP_ITERATIONS = 20;
    private static final int MEASURED_ITERATIONS = 100;

    // Dataset contains 49,683 Person nodes
    private static final int PERSON_COUNT = 49_683;

    // Reproducible random selection
    private static final Random RANDOM = new Random(42);

    public static void main(String[] args) {

        Dotenv dotenv = Dotenv.load();

        String uri = dotenv.get("COGNODB_URI");
        String username = dotenv.get("COGNODB_USER");
        String password = dotenv.get("COGNODB_PASSWORD");

        if (uri == null || username == null || password == null) {
            throw new IllegalStateException(
                    "Missing CognoDB environment variables"
            );
        }

        try (Driver driver = GraphDatabase.driver(
                uri,
                AuthTokens.basic(username, password)
        )) {

            // Verify database connection
            driver.verifyConnectivity();

            System.out.println();
            System.out.println("======================================");
            System.out.println("       COGNODB GRAPH BENCHMARK");
            System.out.println("======================================");

            try (Session session = driver.session()) {

                // Get reproducible set of existing nodes
                List<Long> startNodes =
                        loadRandomNodes(session);

                System.out.println(
                        "Random start nodes available: "
                                + startNodes.size()
                );

                if (startNodes.isEmpty()) {
                    throw new IllegalStateException(
                            "No Person nodes found."
                    );
                }

                // --------------------------------------
                // WARM-UP
                // --------------------------------------

                System.out.println();
                System.out.println(
                        "Warming up database..."
                );

                warmup(
                        session,
                        startNodes
                );

                System.out.println(
                        "Warm-up complete."
                );

                // --------------------------------------
                // BENCHMARKS
                // --------------------------------------

                System.out.println();
                System.out.println(
                        "Running benchmark..."
                );

                // 1-hop
                runTraversalBenchmark(
                        session,
                        "1-Hop Traversal",
                        startNodes,
                        1
                );

                // 2-hop
                runTraversalBenchmark(
                        session,
                        "2-Hop Traversal",
                        startNodes,
                        2
                );

                // 3-hop
                runTraversalBenchmark(
                        session,
                        "3-Hop Traversal",
                        startNodes,
                        3
                );

                // Point lookup
                runPointLookupBenchmark(
                        session,
                        startNodes
                );

                // Indexed lookup
                runIndexedLookupBenchmark(
                        session
                );

                // Aggregation
                runAggregationBenchmark(
                        session
                );
            }

            System.out.println();
            System.out.println("======================================");
            System.out.println("       BENCHMARK COMPLETE");
            System.out.println("======================================");
        }
    }

    // ==================================================
    // LOAD RANDOM START NODES
    // ==================================================

    private static List<Long> loadRandomNodes(
            Session session
    ) {

        List<Long> nodes =
                new ArrayList<>();

        session.run(
                """
                MATCH (n:Person)
                RETURN n.id AS id
                LIMIT 1000
                """
        ).forEachRemaining(record -> {

            if (!record.get("id").isNull()) {

                nodes.add(
                        record.get("id").asLong()
                );
            }
        });

        Collections.shuffle(
                nodes,
                RANDOM
        );

        return nodes;
    }

    // ==================================================
    // WARM-UP
    // ==================================================

    private static void warmup(
            Session session,
            List<Long> nodes
    ) {

        int count =
                Math.min(
                        WARMUP_ITERATIONS,
                        nodes.size()
                );

        for (int i = 0; i < count; i++) {

            long nodeId =
                    nodes.get(i);

            session.run(
                    """
                    MATCH (a:Person {id: $id})
                          -[:FRIEND]->
                          (b)
                    RETURN count(b)
                    """,
                    Values.parameters(
                            "id",
                            nodeId
                    )
            ).consume();
        }
    }

    // ==================================================
    // 1-HOP / 2-HOP / 3-HOP TRAVERSAL
    // ==================================================

    private static void runTraversalBenchmark(
            Session session,
            String name,
            List<Long> nodes,
            int hops
    ) {

        List<Double> latencies =
                new ArrayList<>();

        String query;

        if (hops == 1) {

            query =
                    """
                    MATCH (a:Person {id: $id})
                          -[:FRIEND]->
                          (b)
                    RETURN count(b)
                    """;

        } else if (hops == 2) {

            query =
                    """
                    MATCH (a:Person {id: $id})
                          -[:FRIEND]->
                          (b)
                          -[:FRIEND]->
                          (c)
                    RETURN count(c)
                    """;

        } else {

            query =
                    """
                    MATCH (a:Person {id: $id})
                          -[:FRIEND]->
                          (b)
                          -[:FRIEND]->
                          (c)
                          -[:FRIEND]->
                          (d)
                    RETURN count(d)
                    """;
        }

        for (
                int i = 0;
                i < MEASURED_ITERATIONS;
                i++
        ) {

            long nodeId =
                    nodes.get(
                            RANDOM.nextInt(
                                    nodes.size()
                            )
                    );

            long start =
                    System.nanoTime();

            session.run(
                    query,
                    Values.parameters(
                            "id",
                            nodeId
                    )
            ).consume();

            long end =
                    System.nanoTime();

            double milliseconds =
                    (end - start)
                            / 1_000_000.0;

            latencies.add(
                    milliseconds
            );
        }

        printResults(
                name,
                latencies
        );
    }

    // ==================================================
    // POINT LOOKUP
    // ==================================================

    private static void runPointLookupBenchmark(
            Session session,
            List<Long> nodes
    ) {

        List<Double> latencies =
                new ArrayList<>();

        for (
                int i = 0;
                i < MEASURED_ITERATIONS;
                i++
        ) {

            long nodeId =
                    nodes.get(
                            RANDOM.nextInt(
                                    nodes.size()
                            )
                    );

            long start =
                    System.nanoTime();

            session.run(
                    """
                    MATCH (n:Person {id: $id})
                    RETURN n.id
                    """,
                    Values.parameters(
                            "id",
                            nodeId
                    )
            ).consume();

            long end =
                    System.nanoTime();

            latencies.add(
                    (end - start)
                            / 1_000_000.0
            );
        }

        printResults(
                "Point Lookup",
                latencies
        );
    }

    // ==================================================
    // INDEXED PROPERTY LOOKUP
    // ==================================================

    private static void runIndexedLookupBenchmark(
            Session session
    ) {

        List<Double> latencies =
                new ArrayList<>();

        for (
                int i = 0;
                i < MEASURED_ITERATIONS;
                i++
        ) {

            long id =
                    1 + RANDOM.nextInt(
                            PERSON_COUNT
                    );

            long start =
                    System.nanoTime();

            session.run(
                    """
                    MATCH (n:Person)
                    WHERE n.id = $id
                    RETURN n.id
                    """,
                    Values.parameters(
                            "id",
                            id
                    )
            ).consume();

            long end =
                    System.nanoTime();

            latencies.add(
                    (end - start)
                            / 1_000_000.0
            );
        }

        printResults(
                "Indexed Property Lookup",
                latencies
        );
    }

    // ==================================================
    // AGGREGATION
    // ==================================================

    private static void runAggregationBenchmark(
            Session session
    ) {

        List<Double> latencies =
                new ArrayList<>();

        for (
                int i = 0;
                i < MEASURED_ITERATIONS;
                i++
        ) {

            long start =
                    System.nanoTime();

            session.run(
                    """
                    MATCH (n:Person)
                    RETURN count(n)
                    """
            ).consume();

            long end =
                    System.nanoTime();

            latencies.add(
                    (end - start)
                            / 1_000_000.0
            );
        }

        printResults(
                "Aggregation",
                latencies
        );
    }

    // ==================================================
    // PRINT RESULTS
    // ==================================================

    private static void printResults(
            String name,
            List<Double> latencies
    ) {

        Collections.sort(
                latencies
        );

        double p50 =
                percentile(
                        latencies,
                        50
                );

        double p95 =
                percentile(
                        latencies,
                        95
                );

        double average =
                latencies.stream()
                        .mapToDouble(
                                Double::doubleValue
                        )
                        .average()
                        .orElse(0);

        System.out.println();

        System.out.println(
                "--------------------------------------"
        );

        System.out.println(
                name
        );

        System.out.println(
                "Iterations: "
                        + latencies.size()
        );

        System.out.printf(
                "Average: %.3f ms%n",
                average
        );

        System.out.printf(
                "p50: %.3f ms%n",
                p50
        );

        System.out.printf(
                "p95: %.3f ms%n",
                p95
        );

        System.out.println(
                "--------------------------------------"
        );
    }

    // ==================================================
    // PERCENTILE
    // ==================================================

    private static double percentile(
            List<Double> values,
            double percentile
    ) {

        if (values.isEmpty()) {
            return 0;
        }

        double index =
                (percentile / 100.0)
                        * (values.size() - 1);

        int lower =
                (int) Math.floor(index);

        int upper =
                (int) Math.ceil(index);

        if (lower == upper) {
            return values.get(lower);
        }

        double weight =
                index - lower;

        return values.get(lower)
                * (1 - weight)
                +
                values.get(upper)
                        * weight;
    }
}