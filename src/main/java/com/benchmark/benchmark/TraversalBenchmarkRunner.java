package com.benchmark.benchmark;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

public class TraversalBenchmarkRunner {

    public static void main(String[] args) {

        String uri = System.getenv("COGNODB_URI");
        String username = System.getenv("COGNODB_USER");
        String password = System.getenv("COGNODB_PASSWORD");

        if (uri == null || username == null || password == null) {
            throw new IllegalStateException(
                    "Missing CognoDB environment variables"
            );
        }

        try (Driver driver = GraphDatabase.driver(
                uri,
                AuthTokens.basic(username, password)
        )) {

            driver.verifyConnectivity();

            System.out.println(
                    "Connected to CognoDB successfully."
            );

            TraversalBenchmark benchmark =
                    new TraversalBenchmark(driver);

            BenchmarkResult result =
                    benchmark.run1Hop();

            System.out.println();
            System.out.println("===== BENCHMARK RESULT =====");
            System.out.println(result);
        }
    }
}