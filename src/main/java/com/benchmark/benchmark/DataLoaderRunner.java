package com.benchmark.benchmark;

import io.github.cdimascio.dotenv.Dotenv;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

public class DataLoaderRunner {

    public static void main(String[] args) throws Exception {

        /*
         * Load .env from the project root.
         */
        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .load();

        /*
         * Read Neo4j credentials.
         */
        String uri =
                dotenv.get("NEO4J_URI");

        String username =
                dotenv.get("NEO4J_USERNAME");

        String password =
                dotenv.get("NEO4J_PASSWORD");

        /*
         * Check that all required variables exist.
         */
        if (uri == null || uri.isBlank()
                || username == null || username.isBlank()
                || password == null || password.isBlank()) {

            throw new IllegalStateException(
                    "Missing Neo4j environment variables.\n"
                            + "Required:\n"
                            + "NEO4J_URI\n"
                            + "NEO4J_USERNAME\n"
                            + "NEO4J_PASSWORD\n\n"
                            + "Make sure .env is in the project root."
            );
        }

        /*
         * Dataset location.
         */
        String datasetPath =
                "data/soc-pokec-relationships.txt.gz";

        System.out.println();
        System.out.println(
                "======================================"
        );

        System.out.println(
                "          Neo4j Data Loader"
        );

        System.out.println(
                "======================================"
        );

        System.out.println();
        System.out.println(
                "Connecting to Neo4j..."
        );

        System.out.println(
                "Dataset: " + datasetPath
        );

        System.out.println(
                "Maximum relationships: 100000"
        );

        System.out.println(
                "Batch size: 1000"
        );

        /*
         * Connect to Neo4j Aura.
         */
        try (Driver driver =
                     GraphDatabase.driver(
                             uri,
                             AuthTokens.basic(
                                     username,
                                     password
                             )
                     )) {

            /*
             * Verify the connection.
             */
            driver.verifyConnectivity();

            System.out.println();
            System.out.println(
                    "Connected to Neo4j successfully."
            );

            /*
             * Test query.
             */
            var result =
                    driver.session()
                            .run("RETURN 1 AS test")
                            .single();

            System.out.println(
                    "Test query result: "
                            + result.get("test").asInt()
            );

            /*
             * Start loading the dataset.
             */
            System.out.println();
            System.out.println(
                    "Starting data load..."
            );

            DataLoader loader =
                    new DataLoader(driver);

            loader.load(datasetPath);

            System.out.println();
            System.out.println(
                    "======================================"
            );

            System.out.println(
                    "       DATA LOAD FINISHED"
            );

            System.out.println(
                    "======================================"
            );

            System.out.println();
            System.out.println(
                    "Program finished successfully."
            );
        }
    }
}

