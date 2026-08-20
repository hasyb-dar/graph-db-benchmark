package com.benchmark.benchmark;

import com.benchmark.client.FalkorDBGraphClient;
import io.github.cdimascio.dotenv.Dotenv;

public class FalkorDBDataLoaderRunner {

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
                    "Missing FALKORDB_HOST environment variable.\n"
                            + "Required:\n"
                            + "FALKORDB_HOST\n"
                            + "FALKORDB_PORT\n"
                            + "FALKORDB_USERNAME (optional)\n"
                            + "FALKORDB_PASSWORD (optional)\n"
                            + "FALKORDB_SSL (true/false, optional)"
            );
        }

        int port = portStr == null || portStr.isBlank()
                ? 6379
                : Integer.parseInt(portStr.trim());

        boolean useSsl =
                sslStr != null && sslStr.trim().equalsIgnoreCase("true");

        String datasetPath = "data/soc-pokec-relationships.txt.gz";

        FalkorDBGraphClient client =
                new FalkorDBGraphClient(
                        host, port, username, password, useSsl
                );

        try {

            System.out.println();
            System.out.println("======================================");
            System.out.println("       FalkorDB Pokec Data Loader");
            System.out.println("======================================");
            System.out.println("Dataset: " + datasetPath);
            System.out.println("Maximum relationships: 100000");
            System.out.println("Batch size: 1000");
            System.out.println();

            client.connect();

            System.out.println("Connected to FalkorDB successfully.");

            // Clear old graph data so re-runs start clean.
            System.out.println();
            System.out.println("Clearing old graph data...");

            client.runWrite(
                    "MATCH (n:Vertex) DETACH DELETE n",
                    null
            );

            System.out.println("Old graph data cleared.");

            // Create the index BEFORE loading. Without it, every MERGE
            // during insertBatch() does a full vertex scan, which gets
            // slower as the vertex set grows and can exceed the
            // client's socket timeout on later batches.
            System.out.println();
            System.out.println("Creating index on :Vertex(id)...");

            try {

                client.runQuery(
                        "CREATE INDEX FOR (n:Vertex) ON (n.id)",
                        null
                );

                System.out.println("Index created.");

            } catch (Exception e) {

                System.out.println(
                        "Index creation skipped/failed (may already exist): "
                                + e.getMessage()
                );
            }

            System.out.println();
            System.out.println("Starting Pokec data load...");

            FalkorDBDataLoader loader = new FalkorDBDataLoader(client);

            loader.load(datasetPath);

            System.out.println();
            System.out.println("======================================");
            System.out.println("       FALKORDB DATA LOAD PASSED");
            System.out.println("======================================");

        } catch (Exception e) {

            System.out.println();
            System.out.println("======================================");
            System.out.println("       FALKORDB DATA LOAD FAILED");
            System.out.println("======================================");

            e.printStackTrace();

        } finally {

            client.close();
        }
    }
}