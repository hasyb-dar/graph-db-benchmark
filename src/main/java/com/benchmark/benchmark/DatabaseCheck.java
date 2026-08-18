package com.benchmark.benchmark;

import io.github.cdimascio.dotenv.Dotenv;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;

public class DatabaseCheck {

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

            driver.verifyConnectivity();

            System.out.println(
                    "Connected to CognoDB successfully."
            );

            try (var session = driver.session()) {

                Record nodeRecord = session.run(
                        "MATCH (n:Person) RETURN count(n) AS count"
                ).single();

                Record relationshipRecord = session.run(
                        "MATCH ()-[r:FRIEND]->() RETURN count(r) AS count"
                ).single();

                long nodes =
                        nodeRecord.get("count").asLong();

                long relationships =
                        relationshipRecord.get("count").asLong();

                System.out.println();
                System.out.println(
                        "================================="
                );
                System.out.println(
                        "       COGNODB DATA CHECK"
                );
                System.out.println(
                        "================================="
                );
                System.out.println(
                        "Person nodes: " + nodes
                );
                System.out.println(
                        "FRIEND relationships: " + relationships
                );
                System.out.println(
                        "================================="
                );
            }
        }
    }
}