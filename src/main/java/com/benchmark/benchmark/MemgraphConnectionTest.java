package com.benchmark.benchmark;

import io.github.cdimascio.dotenv.Dotenv;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

public class MemgraphConnectionTest {

    public static void main(String[] args) {

        Driver driver = null;

        try {

            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();

            String uri = dotenv.get("MEMGRAPH_URI");
            String username = dotenv.get("MEMGRAPH_USERNAME");
            String password = dotenv.get("MEMGRAPH_PASSWORD");

            System.out.println("======================================");
            System.out.println("       MEMGRAPH CONNECTION TEST");
            System.out.println("======================================");

            System.out.println("RAW URI = [" + uri + "]");

            if (uri == null || uri.isBlank()) {
                throw new RuntimeException(
                        "MEMGRAPH_URI is missing from .env"
                );
            }

            /*
             * IMPORTANT:
             * Do NOT convert bolt+s:// to bolt:// here.
             *
             * We first test the exact URI from .env.
             */

            uri = uri.trim();

            if (uri.startsWith("\"") && uri.endsWith("\"")) {
                uri = uri.substring(1, uri.length() - 1);
            }

            if (uri.startsWith("'") && uri.endsWith("'")) {
                uri = uri.substring(1, uri.length() - 1);
            }

            System.out.println("FINAL URI = [" + uri + "]");

            if (username == null) {
                username = "";
            }

            if (password == null) {
                password = "";
            }

            System.out.println(
                    "Username present: "
                            + !username.isBlank()
            );

            System.out.println(
                    "Password present: "
                            + !password.isBlank()
            );

            System.out.println();
            System.out.println("Creating driver...");

            if (!username.isBlank()) {

                driver = GraphDatabase.driver(
                        uri,
                        AuthTokens.basic(
                                username,
                                password
                        )
                );

            } else {

                driver = GraphDatabase.driver(uri);
            }

            System.out.println(
                    "Driver created."
            );

            System.out.println(
                    "Verifying connectivity..."
            );

            driver.verifyConnectivity();

            System.out.println();
            System.out.println("======================================");
            System.out.println(
                    "       CONNECTION SUCCESSFUL"
            );
            System.out.println("======================================");

        } catch (Exception e) {

            System.out.println();
            System.out.println("======================================");
            System.out.println(
                    "       CONNECTION FAILED"
            );
            System.out.println("======================================");
            System.out.println();

            e.printStackTrace();

        } finally {

            if (driver != null) {

                driver.close();

                System.out.println();
                System.out.println(
                        "Driver closed."
                );
            }
        }
    }
}