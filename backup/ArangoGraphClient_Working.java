package com.benchmark.client;

import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.ArangoCollection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArangoGraphClient implements GraphClient {

    private final String uri;
    private final String username;
    private final String password;
    private final String databaseName;

    private ArangoDB arangoDB;
    private ArangoDatabase database;

    public ArangoGraphClient(
            String uri,
            String username,
            String password,
            String databaseName) {

        this.uri = uri;
        this.username = username;
        this.password = password;
        this.databaseName = databaseName;
    }

    @Override
    public String platformName() {
        return "ArangoDB";
    }

    @Override
    public void connect() {

        System.out.println("Connecting to ArangoDB...");

        try {

            String cleanHost = uri
                    .replace("https://", "")
                    .replace("http://", "")
                    .replace("/", "");

            System.out.println(
                    "ArangoDB host: " + cleanHost
            );

            System.out.println(
                    "ArangoDB port: 8529"
            );

            System.out.println(
                    "ArangoDB SSL: enabled"
            );

            arangoDB = new ArangoDB.Builder()
                    .host(cleanHost, 8529)
                    .user(username)
                    .password(password)
                    .useSsl(true)
                    .build();

            database = arangoDB.db(databaseName);

            database.getVersion();

            System.out.println(
                    "Connected to ArangoDB successfully."
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to connect to ArangoDB.",
                    e
            );
        }
    }

    @Override
    public List<Map<String, Object>> runQuery(
            String query,
            Map<String, Object> params) {

        if (database == null) {

            throw new IllegalStateException(
                    "ArangoDB is not connected."
            );
        }

        List<Map<String, Object>> results =
                new ArrayList<>();

        Map<String, Object> bindVars =
                params == null
                        ? new HashMap<>()
                        : new HashMap<>(params);

        try {

            var cursor = database.query(
                    query,
                    Object.class,
                    bindVars
            );

            while (cursor.hasNext()) {

                Object result = cursor.next();

                Map<String, Object> row =
                        new HashMap<>();

                if (result instanceof Map<?, ?> map) {

                    for (Map.Entry<?, ?> entry :
                            map.entrySet()) {

                        row.put(
                                String.valueOf(
                                        entry.getKey()
                                ),
                                entry.getValue()
                        );
                    }

                } else {

                    row.put(
                            "result",
                            result
                    );
                }

                results.add(row);
            }

            return results;

        } catch (Exception e) {

            throw new RuntimeException(
                    "ArangoDB query failed: " + query,
                    e
            );
        }
    }

    @Override
    public void runWrite(
            String query,
            Map<String, Object> params) {

        runQuery(query, params);
    }

    /*
     * Creates a normal document collection.
     */
    public void createDocumentCollection(
            String name) {

        if (database == null) {

            throw new IllegalStateException(
                    "ArangoDB is not connected."
            );
        }

        try {

            ArangoCollection collection =
                    database.collection(name);

            if (collection.exists()) {

                System.out.println(
                        "Collection already exists: "
                                + name
                );

                return;
            }

            database.createCollection(name);

            System.out.println(
                    "Created collection: " + name
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to create collection: "
                            + name,
                    e
            );
        }
    }

    /*
     * Creates the vertex collection.
     *
     * benchmark_vertices is a normal document
     * collection, which is correct for vertices.
     */
    public void createVertexCollection() {

        createDocumentCollection(
                "benchmark_vertices"
        );
    }

    @Override
    public Map<String, Object> footprint() {

        Map<String, Object> result =
                new HashMap<>();

        result.put(
                "platform",
                "ArangoDB"
        );

        result.put(
                "database",
                databaseName
        );

        result.put(
                "connected",
                database != null
        );

        return result;
    }

    @Override
    public void close() {

        if (arangoDB != null) {

            try {

                arangoDB.shutdown();

            } catch (Exception ignored) {
            }

            arangoDB = null;
            database = null;
        }

        System.out.println(
                "ArangoDB connection closed."
        );
    }
}