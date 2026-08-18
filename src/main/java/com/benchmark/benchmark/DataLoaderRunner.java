package com.benchmark.benchmark;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

public class DataLoaderRunner {

    public static void main(String[] args)
            throws Exception {

        /*
         * Read CognoDB credentials from
         * environment variables.
         */
        String uri =
                System.getenv("COGNODB_URI");

        String username =
                System.getenv("COGNODB_USER");

        String password =
                System.getenv("COGNODB_PASSWORD");

        /*
         * Make sure all environment variables
         * are available.
         */
        if (uri == null || uri.isBlank()
                || username == null
                || username.isBlank()
                || password == null
                || password.isBlank()) {

            throw new IllegalStateException(
                    "Missing CognoDB environment variables.\n"
                            + "Required variables:\n"
                            + "COGNODB_URI\n"
                            + "COGNODB_USER\n"
                            + "COGNODB_PASSWORD"
            );
        }

        /*
         * Dataset location.
         *
         * This is relative to the project
         * working directory.
         */
        String datasetPath =
                "data/soc-pokec-relationships.txt.gz";

        System.out.println();
        System.out.println(
                "======================================"
        );

        System.out.println(
                "       CognoDB Data Loader"
        );

        System.out.println(
                "======================================"
        );

        System.out.println(
                "Connecting to CognoDB..."
        );

        /*
         * Create Neo4j/CognoDB driver.
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
             * Test the connection.
             */
            driver.verifyConnectivity();

            System.out.println(
                    "Connected to CognoDB successfully."
            );

            System.out.println(
                    "Dataset: "
                            + datasetPath
            );

            System.out.println(
                    "Maximum relationships: 100000"
            );

            System.out.println(
                    "Batch size: 1000"
            );

            System.out.println();
            System.out.println(
                    "Starting data load..."
            );

            /*
             * Create loader.
             */
            DataLoader loader =
                    new DataLoader(driver);

            /*
             * Start loading.
             */
            loader.load(datasetPath);

            System.out.println();
            System.out.println(
                    "Program finished successfully."
            );
        }
    }
}