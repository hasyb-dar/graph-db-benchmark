package com.benchmark.client;

import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.model.PersistentIndexOptions;

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

            System.out.println("ArangoDB host: " + cleanHost);
            System.out.println("ArangoDB port: 8529");
            System.out.println("ArangoDB SSL: enabled");

            arangoDB = new ArangoDB.Builder()
                    .host(cleanHost, 8529)
                    .user(username)
                    .password(password)
                    .useSsl(true)
                    .build();

            database = arangoDB.db(databaseName);

            database.getVersion();

            System.out.println("Connected to ArangoDB successfully.");

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

        List<Map<String, Object>> results = new ArrayList<>();

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

                Map<String, Object> row = new HashMap<>();

                if (result instanceof Map<?, ?> map) {

                    for (Map.Entry<?, ?> entry : map.entrySet()) {

                        row.put(
                                String.valueOf(entry.getKey()),
                                entry.getValue()
                        );
                    }

                } else {

                    row.put("result", result);
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
     * Create the vertex and edge collections.
     */
    public void createGraphCollections() {

        if (database == null) {
            throw new IllegalStateException(
                    "ArangoDB is not connected."
            );
        }

        try {

            List<Map<String, Object>> vertexCheck =
                    runQuery(
                            "FOR c IN COLLECTIONS() "
                                    + "FILTER c.name == 'benchmark_vertices' "
                                    + "RETURN c",
                            null
                    );

            if (vertexCheck.isEmpty()) {

                runWrite(
                        "RETURN V8_TO_BOOL("
                                + "CREATE_COLLECTION('benchmark_vertices')"
                                + ")",
                        null
                );

                System.out.println(
                        "Created vertex collection: benchmark_vertices"
                );

            } else {

                System.out.println(
                        "Collection already exists: benchmark_vertices"
                );
            }

            List<Map<String, Object>> edgeCheck =
                    runQuery(
                            "FOR c IN COLLECTIONS() "
                                    + "FILTER c.name == 'benchmark_edges' "
                                    + "RETURN c",
                            null
                    );

            if (edgeCheck.isEmpty()) {

                runWrite(
                        "RETURN V8_TO_BOOL("
                                + "CREATE_COLLECTION("
                                + "'benchmark_edges', "
                                + "{type: 3}"
                                + ")"
                                + ")",
                        null
                );

                System.out.println(
                        "Created EDGE collection: benchmark_edges"
                );

            } else {

                System.out.println(
                        "Using existing edge collection: benchmark_edges"
                );
            }

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to create graph collections.",
                    e
            );
        }
    }

    /*
     * Ensure a persistent (hash-tree) index exists on the given fields
     * of the given collection. Used for the "indexed property lookup"
     * benchmark -- benchmark_vertices.node_id is NOT covered by the
     * automatic primary index on _key, so this needs to be created
     * explicitly, the same way MemgraphBenchmarkRunner/
     * FalkorDBBenchmarkRunner create an index on :Vertex(id).
     */
    public void ensurePersistentIndex(
            String collectionName,
            List<String> fields) {

        if (database == null) {
            throw new IllegalStateException(
                    "ArangoDB is not connected."
            );
        }

        try {

            database.collection(collectionName)
                    .ensurePersistentIndex(
                            fields,
                            new PersistentIndexOptions()
                    );

            System.out.println(
                    "Ensured persistent index on "
                            + collectionName + fields
            );

        } catch (Exception e) {

            System.out.println(
                    "Index creation skipped/failed (may already exist): "
                            + e.getMessage()
            );
        }
    }

    /*
     * Measure query execution time (no bind variables).
     */
    public long measureQueryTime(String query) {
        return measureQueryTime(query, null);
    }

    /*
     * Measure query execution time with bind variables, so randomized
     * lookups/traversals (different start node each iteration) can be
     * timed the same way the other platforms' benchmark runners do.
     */
    public long measureQueryTime(
            String query,
            Map<String, Object> bindVars) {

        if (database == null) {
            throw new IllegalStateException(
                    "ArangoDB is not connected."
            );
        }

        Map<String, Object> vars =
                bindVars == null
                        ? new HashMap<>()
                        : bindVars;

        long startTime = System.nanoTime();

        try {

            var cursor = database.query(
                    query,
                    Object.class,
                    vars
            );

            while (cursor.hasNext()) {
                cursor.next();
            }

        } catch (Exception e) {

            throw new RuntimeException(
                    "ArangoDB benchmark query failed: " + query,
                    e
            );
        }

        long endTime = System.nanoTime();

        return endTime - startTime;
    }

    @Override
    public Map<String, Object> footprint() {

        Map<String, Object> result = new HashMap<>();

        result.put("platform", "ArangoDB");
        result.put("database", databaseName);
        result.put("connected", database != null);

        return result;
    }

    @Override
    public void close() {

        if (arangoDB != null) {

            try {
                arangoDB.shutdown();
            } catch (Exception ignored) {
                // Ignore shutdown errors
            }

            arangoDB = null;
            database = null;
        }

        System.out.println("ArangoDB connection closed.");
    }
}