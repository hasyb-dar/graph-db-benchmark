package com.benchmark.benchmark;

import com.benchmark.client.ArangoGraphClient;
import io.github.cdimascio.dotenv.Dotenv;

public class ArangoDataLoaderRunner {

    public static void main(String[] args) {

        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();

        String uri = dotenv.get("ARANGO_URI");
        String username = dotenv.get("ARANGO_USERNAME");
        String password = dotenv.get("ARANGO_PASSWORD");

        if (uri == null || uri.isBlank()
                || username == null || username.isBlank()
                || password == null || password.isBlank()) {

            throw new IllegalStateException(
                    "Missing ArangoDB environment variables.\n"
                            + "Required:\n"
                            + "ARANGO_URI\n"
                            + "ARANGO_USERNAME\n"
                            + "ARANGO_PASSWORD"
            );
        }

        String database = "_system";

        String datasetPath =
                "data/soc-pokec-relationships.txt.gz";

        ArangoGraphClient client =
                new ArangoGraphClient(
                        uri,
                        username,
                        password,
                        database
                );

        try {

            System.out.println();
            System.out.println(
                    "======================================"
            );

            System.out.println(
                    "       ArangoDB Pokec Data Loader"
            );

            System.out.println(
                    "======================================"
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

            System.out.println();

            // Connect
            client.connect();

            System.out.println(
                    "Connection successful."
            );

            // IMPORTANT:
            // We are using the collections that already exist.
            // Do NOT recreate them.

            System.out.println();
            System.out.println(
                    "Using existing collections:"
            );

            System.out.println(
                    "benchmark_vertices"
            );

            System.out.println(
                    "benchmark_edges"
            );

            // Clear old graph data
            client.runWrite(
                    "FOR v IN benchmark_vertices "
                            + "REMOVE v IN benchmark_vertices",
                    null
            );

            client.runWrite(
                    "FOR e IN benchmark_edges "
                            + "REMOVE e IN benchmark_edges",
                    null
            );

            System.out.println();
            System.out.println(
                    "Old graph data cleared."
            );

            // Start loading
            System.out.println();
            System.out.println(
                    "Starting Pokec data load..."
            );

            ArangoDataLoader loader =
                    new ArangoDataLoader(client);

            loader.load(datasetPath);

            System.out.println();
            System.out.println(
                    "======================================"
            );

            System.out.println(
                    "       ARANGODB DATA LOAD PASSED"
            );

            System.out.println(
                    "======================================"
            );

        } catch (Exception e) {

            System.out.println();
            System.out.println(
                    "======================================"
            );

            System.out.println(
                    "       ARANGODB DATA LOAD FAILED"
            );

            System.out.println(
                    "======================================"
            );

            e.printStackTrace();

        } finally {

            client.close();
        }
    }
}