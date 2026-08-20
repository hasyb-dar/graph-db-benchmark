package com.benchmark.benchmark;

import io.github.cdimascio.dotenv.Dotenv;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

public class MemgraphDataLoaderRunner {

    public static void main(String[] args) throws Exception {

        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();

        String uri = dotenv.get("MEMGRAPH_URI");
        String username = dotenv.get("MEMGRAPH_USERNAME");
        String password = dotenv.get("MEMGRAPH_PASSWORD");

        if (uri == null || uri.isBlank()) {

            throw new IllegalStateException(
                    "Missing MEMGRAPH_URI environment variable.\n"
                            + "Required:\n"
                            + "MEMGRAPH_URI\n"
                            + "MEMGRAPH_USERNAME (optional)\n"
                            + "MEMGRAPH_PASSWORD (optional)"
            );
        }

        if (username == null) {
            username = "";
        }

        if (password == null) {
            password = "";
        }

        String datasetPath =
                "data/soc-pokec-relationships.txt.gz";

        Driver driver =
                username.isBlank()
                        ? GraphDatabase.driver(uri)
                        : GraphDatabase.driver(
                        uri,
                        AuthTokens.basic(username, password)
                );

        try {

            System.out.println();
            System.out.println(
                    "======================================"
            );

            System.out.println(
                    "       Memgraph Pokec Data Loader"
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
                    "Batch size: 10000"
            );

            System.out.println();

            driver.verifyConnectivity();

            System.out.println(
                    "Connected to Memgraph successfully."
            );

            /*
             * Clear old graph data before loading, so re-runs
             * start from a clean state (mirrors
             * ArangoDataLoaderRunner's behavior).
             */
            System.out.println();
            System.out.println(
                    "Clearing old graph data..."
            );

            try (Session session = driver.session()) {

                session.run(
                        "MATCH (n:Vertex) DETACH DELETE n"
                ).consume();
            }

            System.out.println(
                    "Old graph data cleared."
            );

            System.out.println();
            System.out.println(
                    "Starting Pokec data load..."
            );

            MemgraphDataLoader loader =
                    new MemgraphDataLoader(driver);

            loader.load(datasetPath);

            System.out.println();
            System.out.println(
                    "======================================"
            );

            System.out.println(
                    "       MEMGRAPH DATA LOAD PASSED"
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
                    "       MEMGRAPH DATA LOAD FAILED"
            );

            System.out.println(
                    "======================================"
            );

            e.printStackTrace();

        } finally {

            driver.close();
        }
    }
}