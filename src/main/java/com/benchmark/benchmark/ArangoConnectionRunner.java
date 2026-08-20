package com.benchmark.benchmark;

import com.benchmark.client.ArangoGraphClient;
import com.benchmark.client.GraphClient;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.List;
import java.util.Map;

public class ArangoConnectionRunner {

    public static void main(String[] args) {

        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();

        String uri =
                dotenv.get("ARANGO_URI");

        String username =
                dotenv.get("ARANGO_USERNAME");

        String password =
                dotenv.get("ARANGO_PASSWORD");

        if (uri == null || uri.isBlank()
                || username == null || username.isBlank()
                || password == null || password.isBlank()) {

            throw new IllegalStateException(
                    "Could not load ArangoDB values from .env.\n"
                            + "Required:\n"
                            + "ARANGO_URI\n"
                            + "ARANGO_USERNAME\n"
                            + "ARANGO_PASSWORD"
            );
        }

        String database = "_system";

        GraphClient client =
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
                    "             ArangoDB"
            );
            System.out.println(
                    "======================================"
            );

            // =========================================
            // STEP 1: CONNECT
            // =========================================

            client.connect();

            System.out.println(
                    "Connection successful."
            );

            ArangoGraphClient arango =
                    (ArangoGraphClient) client;

            // =========================================
            // STEP 2: TEST AQL
            // =========================================

            List<Map<String, Object>> test =
                    client.runQuery(
                            "RETURN 1",
                            null
                    );

            System.out.println();
            System.out.println(
                    "Basic AQL result:"
            );
            System.out.println(test);

            // =========================================
            // STEP 3: CREATE TEST COLLECTION
            // =========================================

//            arango.createBenchmarkCollection();

            // =========================================
            // STEP 4: CREATE GRAPH COLLECTIONS
            // =========================================

            arango.createGraphCollections();

            // =========================================
            // IMPORTANT:
            //
            // benchmark_edges must already exist
            // in ArangoDB Cloud as an EDGE collection.
            //
            // Do NOT create it here as a normal
            // document collection.
            // =========================================

            System.out.println();
            System.out.println(
                    "Using existing edge collection: "
                            + "benchmark_edges"
            );

            // =========================================
            // STEP 5: CLEAR VERTICES
            // =========================================

//            client.runWrite(
//                    "FOR v IN benchmark_vertices "
//                            + "REMOVE v IN benchmark_vertices",
//                    null
//            );
//
//            System.out.println(
//                    "benchmark_vertices collection cleared."
//            );

            // =========================================
            // STEP 6: CLEAR EDGES
            // =========================================

//            client.runWrite(
//                    "FOR e IN benchmark_edges "
//                            + "REMOVE e IN benchmark_edges",
//                    null
//            );
//
//            System.out.println(
//                    "benchmark_edges collection cleared."
//            );

//            // =========================================
//            // STEP 7: INSERT VERTEX 1
//            // =========================================
//
//            client.runWrite(
//                    "INSERT {"
//                            + "_key: '1', "
//                            + "name: 'Vertex 1'"
//                            + "} INTO benchmark_vertices",
//                    null
//            );
//
//            // =========================================
//            // STEP 8: INSERT VERTEX 2
//            // =========================================
//
//            client.runWrite(
//                    "INSERT {"
//                            + "_key: '2', "
//                            + "name: 'Vertex 2'"
//                            + "} INTO benchmark_vertices",
//                    null
//            );
//
//            // =========================================
//            // STEP 9: INSERT VERTEX 3
//            // =========================================
//
//            client.runWrite(
//                    "INSERT {"
//                            + "_key: '3', "
//                            + "name: 'Vertex 3'"
//                            + "} INTO benchmark_vertices",
//                    null
//            );
//
//            // =========================================
//            // STEP 10: INSERT VERTEX 4
//            // =========================================
//
//            client.runWrite(
//                    "INSERT {"
//                            + "_key: '4', "
//                            + "name: 'Vertex 4'"
//                            + "} INTO benchmark_vertices",
//                    null
//            );
//
//            System.out.println();
//            System.out.println(
//                    "Inserted 4 graph vertices."
//            );
//
//            // =========================================
//            // STEP 11: READ VERTICES
//            // =========================================
//
//            List<Map<String, Object>> vertices =
//                    client.runQuery(
//                            "FOR v IN benchmark_vertices "
//                                    + "SORT v._key "
//                                    + "RETURN v",
//                            null
//                    );
//
//            System.out.println();
//            System.out.println(
//                    "Graph vertices:"
//            );
//
//            System.out.println(vertices);
//
//            // =========================================
//            // STEP 12: INSERT EDGE 1
//            // =========================================
//
//            client.runWrite(
//                    "INSERT {"
//                            + "_key: 'e1', "
//                            + "_from: "
//                            + "'benchmark_vertices/1', "
//                            + "_to: "
//                            + "'benchmark_vertices/2'"
//                            + "} INTO benchmark_edges",
//                    null
//            );
//
//            // =========================================
//            // STEP 13: INSERT EDGE 2
//            // =========================================
//
//            client.runWrite(
//                    "INSERT {"
//                            + "_key: 'e2', "
//                            + "_from: "
//                            + "'benchmark_vertices/2', "
//                            + "_to: "
//                            + "'benchmark_vertices/3'"
//                            + "} INTO benchmark_edges",
//                    null
//            );
//
//            // =========================================
//            // STEP 14: INSERT EDGE 3
//            // =========================================
//
//            client.runWrite(
//                    "INSERT {"
//                            + "_key: 'e3', "
//                            + "_from: "
//                            + "'benchmark_vertices/3', "
//                            + "_to: "
//                            + "'benchmark_vertices/4'"
//                            + "} INTO benchmark_edges",
//                    null
//            );
//
//            System.out.println();
//            System.out.println(
//                    "Inserted 3 graph edges."
//            );
//
//            // =========================================
//            // STEP 15: READ EDGES
//            // =========================================
//
//            List<Map<String, Object>> edges =
//                    client.runQuery(
//                            "FOR e IN benchmark_edges "
//                                    + "SORT e._key "
//                                    + "RETURN e",
//                            null
//                    );
//
//            System.out.println();
//            System.out.println(
//                    "Graph edges:"
//            );
//
//            System.out.println(edges);

            // =========================================
            // STEP 16: COUNT VERTICES
            // =========================================

            List<Map<String, Object>> vertexCount =
                    client.runQuery(
                            "RETURN LENGTH("
                                    + "FOR v IN benchmark_vertices "
                                    + "RETURN v"
                                    + ")",
                            null
                    );

            System.out.println();
            System.out.println(
                    "Vertex count:"
            );

            System.out.println(vertexCount);

            // -----------------------------------------
            // BENCHMARK: VERTEX COUNT QUERY
            // -----------------------------------------

            String vertexCountQuery =
                    "RETURN LENGTH("
                            + "FOR v IN benchmark_vertices "
                            + "RETURN v"
                            + ")";

            long[] vertexCountTimes = new long[10];

            for (int i = 0; i < 10; i++) {

                vertexCountTimes[i] =
                        ((ArangoGraphClient) client)
                                .measureQueryTime(vertexCountQuery);

                System.out.println(
                        "Vertex count run "
                                + (i + 1)
                                + ": "
                                + vertexCountTimes[i]
                                + " ns"
                );
            }

            java.util.Arrays.sort(vertexCountTimes);

            long totalVertexCountTime = 0;

            for (long time : vertexCountTimes) {
                totalVertexCountTime += time;
            }

            long averageVertexCountTime =
                    totalVertexCountTime / vertexCountTimes.length;

            long p50VertexCountTime =
                    vertexCountTimes[4];

            int p95Index =
                    (int) Math.ceil(
                            0.95 * vertexCountTimes.length
                    ) - 1;

            long p95VertexCountTime =
                    vertexCountTimes[p95Index];

            System.out.println();
            System.out.println(
                    "Average vertex count query time: "
                            + averageVertexCountTime
                            + " ns"
            );

            System.out.println(
                    "P50 vertex count query time: "
                            + p50VertexCountTime
                            + " ns"
            );

            System.out.println(
                    "P95 vertex count query time: "
                            + p95VertexCountTime
                            + " ns"
            );

            // =========================================
            // STEP 17: COUNT EDGES
            // =========================================

            List<Map<String, Object>> edgeCount =
                    client.runQuery(
                            "RETURN LENGTH("
                                    + "FOR e IN benchmark_edges "
                                    + "RETURN e"
                                    + ")",
                            null
                    );

            System.out.println();
            System.out.println(
                    "Edge count:"
            );

            System.out.println(edgeCount);

            // -----------------------------------------
            // BENCHMARK: EDGE COUNT QUERY
            // -----------------------------------------

            String edgeCountQuery =
                    "RETURN LENGTH("
                            + "FOR e IN benchmark_edges "
                            + "RETURN e"
                            + ")";

            long[] edgeCountTimes = new long[10];

            for (int i = 0; i < 10; i++) {

                edgeCountTimes[i] =
                        ((ArangoGraphClient) client)
                                .measureQueryTime(edgeCountQuery);

                System.out.println(
                        "Edge count run "
                                + (i + 1)
                                + ": "
                                + edgeCountTimes[i]
                                + " ns"
                );
            }

            java.util.Arrays.sort(edgeCountTimes);

            long totalEdgeCountTime = 0;

            for (long time : edgeCountTimes) {
                totalEdgeCountTime += time;
            }

            long averageEdgeCountTime =
                    totalEdgeCountTime / edgeCountTimes.length;

            long p50EdgeCountTime =
                    edgeCountTimes[4];

            int edgeP95Index =
                    (int) Math.ceil(
                            0.95 * edgeCountTimes.length
                    ) - 1;

            long p95EdgeCountTime =
                    edgeCountTimes[edgeP95Index];

            System.out.println();
            System.out.println(
                    "Average edge count query time: "
                            + averageEdgeCountTime
                            + " ns"
            );

            System.out.println(
                    "P50 edge count query time: "
                            + p50EdgeCountTime
                            + " ns"
            );

            System.out.println(
                    "P95 edge count query time: "
                            + p95EdgeCountTime
                            + " ns"
            );

            // =========================================
            // STEP 18: GRAPH TRAVERSAL
            // =========================================

            List<Map<String, Object>> traversal =
                    client.runQuery(
                            "WITH benchmark_vertices "
                                    + "FOR v, e, p IN 1..3 OUTBOUND "
                                    + "'benchmark_vertices/1' "
                                    + "benchmark_edges "
                                    + "RETURN {"
                                    + "vertex: v.name, "
                                    + "edge: e._key"
                                    + "}",
                            null
                    );

            System.out.println();
            System.out.println(
                    "Traversal from Vertex 1:"
            );

            System.out.println(traversal);

            // -----------------------------------------
            // BENCHMARK: GRAPH TRAVERSAL
            // -----------------------------------------

            String traversalQuery =
                    "WITH benchmark_vertices "
                            + "FOR v, e, p "
                            + "IN 1..3 OUTBOUND "
                            + "'benchmark_vertices/1' "
                            + "benchmark_edges "
                            + "RETURN {"
                            + "vertex: v.name, "
                            + "edge: e._key"
                            + "}";

            long[] traversalTimes = new long[10];

            for (int i = 0; i < 10; i++) {

                traversalTimes[i] =
                        ((ArangoGraphClient) client)
                                .measureQueryTime(traversalQuery);

                System.out.println(
                        "Traversal run "
                                + (i + 1)
                                + ": "
                                + traversalTimes[i]
                                + " ns"
                );
            }

            java.util.Arrays.sort(traversalTimes);

            long totalTraversalTime = 0;

            for (long time : traversalTimes) {
                totalTraversalTime += time;
            }

            long averageTraversalTime =
                    totalTraversalTime / traversalTimes.length;

            long p50TraversalTime =
                    traversalTimes[4];

            int traversalP95Index =
                    (int) Math.ceil(
                            0.95 * traversalTimes.length
                    ) - 1;

            long p95TraversalTime =
                    traversalTimes[traversalP95Index];

            System.out.println();
            System.out.println(
                    "Average traversal query time: "
                            + averageTraversalTime
                            + " ns"
            );

            System.out.println(
                    "P50 traversal query time: "
                            + p50TraversalTime
                            + " ns"
            );

            System.out.println(
                    "P95 traversal query time: "
                            + p95TraversalTime
                            + " ns"
            );

            // =========================================
            // STEP 19: FINAL BENCHMARK SUMMARY
            // =========================================

            System.out.println();
            System.out.println(
                    "======================================"
            );
            System.out.println(
                    "       ARANGODB BENCHMARK RESULTS"
            );
            System.out.println(
                    "======================================"
            );

            System.out.println(
                    "Vertex Count:"
            );

            System.out.println(
                    "  Average: "
                            + averageVertexCountTime
                            + " ns"
            );

            System.out.println(
                    "  P50:     "
                            + p50VertexCountTime
                            + " ns"
            );

            System.out.println(
                    "  P95:     "
                            + p95VertexCountTime
                            + " ns"
            );

            System.out.println();

            System.out.println(
                    "Edge Count:"
            );

            System.out.println(
                    "  Average: "
                            + averageEdgeCountTime
                            + " ns"
            );

            System.out.println(
                    "  P50:     "
                            + p50EdgeCountTime
                            + " ns"
            );

            System.out.println(
                    "  P95:     "
                            + p95EdgeCountTime
                            + " ns"
            );

            System.out.println();

            System.out.println(
                    "Graph Traversal:"
            );

            System.out.println(
                    "  Average: "
                            + averageTraversalTime
                            + " ns"
            );

            System.out.println(
                    "  P50:     "
                            + p50TraversalTime
                            + " ns"
            );

            System.out.println(
                    "  P95:     "
                            + p95TraversalTime
                            + " ns"
            );

            System.out.println();
            System.out.println(
                    "======================================"
            );
            System.out.println(
                    "       ARANGODB GRAPH TEST PASSED"
            );
            System.out.println(
                    "======================================"
            );

        } finally {

            client.close();
        }
    }
}