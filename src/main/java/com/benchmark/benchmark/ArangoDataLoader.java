package com.benchmark.benchmark;

import com.benchmark.client.ArangoGraphClient;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class ArangoDataLoader {

    private static final int MAX_RELATIONSHIPS = 100_000;
    private static final int BATCH_SIZE = 1_000;

    private final ArangoGraphClient client;

    public ArangoDataLoader(ArangoGraphClient client) {
        this.client = client;
    }

    public void load(String filePath) throws Exception {

        long startTime = System.nanoTime();

        long relationshipCount = 0;

        Set<Long> uniqueNodes = new HashSet<>();

        try (
                FileInputStream fileInputStream =
                        new FileInputStream(filePath);

                GZIPInputStream gzipInputStream =
                        new GZIPInputStream(fileInputStream);

                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        gzipInputStream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            List<Relationship> batch =
                    new ArrayList<>(BATCH_SIZE);

            String line;

            while ((line = reader.readLine()) != null) {

                if (line.startsWith("#")) {
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] parts =
                        line.trim().split("\\s+");

                if (parts.length < 2) {
                    continue;
                }

                long source;
                long target;

                try {

                    source =
                            Long.parseLong(parts[0]);

                    target =
                            Long.parseLong(parts[1]);

                } catch (NumberFormatException e) {

                    continue;
                }

                uniqueNodes.add(source);
                uniqueNodes.add(target);

                batch.add(
                        new Relationship(
                                source,
                                target
                        )
                );

                if (batch.size() >= BATCH_SIZE) {

                    int remaining =
                            MAX_RELATIONSHIPS
                                    - (int) relationshipCount;

                    if (batch.size() > remaining) {

                        batch = new ArrayList<>(
                                batch.subList(
                                        0,
                                        remaining
                                )
                        );
                    }

                    if (!batch.isEmpty()) {

                        insertBatch(batch);

                        relationshipCount +=
                                batch.size();

                        System.out.println(
                                "Loaded relationships: "
                                        + relationshipCount
                        );
                    }

                    batch.clear();

                    if (relationshipCount
                            >= MAX_RELATIONSHIPS) {

                        break;
                    }
                }
            }

            /*
             * Insert final partial batch.
             */
            if (!batch.isEmpty()
                    && relationshipCount
                    < MAX_RELATIONSHIPS) {

                int remaining =
                        MAX_RELATIONSHIPS
                                - (int) relationshipCount;

                if (batch.size() > remaining) {

                    batch = new ArrayList<>(
                            batch.subList(
                                    0,
                                    remaining
                            )
                    );
                }

                if (!batch.isEmpty()) {

                    insertBatch(batch);

                    relationshipCount +=
                            batch.size();

                    System.out.println(
                            "Loaded relationships: "
                                    + relationshipCount
                    );
                }
            }
        }

        long endTime = System.nanoTime();

        double seconds =
                (endTime - startTime)
                        / 1_000_000_000.0;

        long nodeCount =
                uniqueNodes.size();

        double nodesPerSecond =
                seconds > 0
                        ? nodeCount / seconds
                        : 0;

        double relationshipsPerSecond =
                seconds > 0
                        ? relationshipCount / seconds
                        : 0;

        System.out.println();
        System.out.println(
                "======================================"
        );

        System.out.println(
                "       ARANGODB DATA LOAD COMPLETE"
        );

        System.out.println(
                "======================================"
        );

        System.out.println(
                "Nodes loaded: "
                        + nodeCount
        );

        System.out.println(
                "Relationships loaded: "
                        + relationshipCount
        );

        System.out.printf(
                "Load time: %.3f seconds%n",
                seconds
        );

        System.out.printf(
                "Nodes/second: %.2f%n",
                nodesPerSecond
        );

        System.out.printf(
                "Relationships/second: %.2f%n",
                relationshipsPerSecond
        );

        System.out.println(
                "======================================"
        );

        System.out.println(
                "ArangoDB data load finished successfully."
        );
    }

    /*
     * Insert one batch using ONE AQL request.
     *
     * This is much faster than sending three separate
     * network requests for every relationship.
     */
    private void insertBatch(
            List<Relationship> relationships) {

        List<List<Long>> relationshipData =
                new ArrayList<>(relationships.size());

        for (Relationship relationship :
                relationships) {

            List<Long> pair =
                    new ArrayList<>(2);

            pair.add(relationship.source);
            pair.add(relationship.target);

            relationshipData.add(pair);
        }

        /*
         * =========================================
         * STEP 1: INSERT / UPSERT VERTICES
         * =========================================
         */

        String vertexQuery = """
            FOR rel IN @relationships

                UPSERT {
                    _key: CONCAT(
                        "v",
                        TO_STRING(rel[0])
                    )
                }

                INSERT {
                    _key: CONCAT(
                        "v",
                        TO_STRING(rel[0])
                    ),
                    node_id: rel[0]
                }

                UPDATE {}

                IN benchmark_vertices
            """;

        client.runWrite(
                vertexQuery,
                java.util.Map.of(
                        "relationships",
                        relationshipData
                )
        );

        /*
         * Insert target vertices separately.
         */

        String targetVertexQuery = """
            FOR rel IN @relationships

                UPSERT {
                    _key: CONCAT(
                        "v",
                        TO_STRING(rel[1])
                    )
                }

                INSERT {
                    _key: CONCAT(
                        "v",
                        TO_STRING(rel[1])
                    ),
                    node_id: rel[1]
                }

                UPDATE {}

                IN benchmark_vertices
            """;

        client.runWrite(
                targetVertexQuery,
                java.util.Map.of(
                        "relationships",
                        relationshipData
                )
        );

        /*
         * =========================================
         * STEP 2: INSERT EDGES
         * =========================================
         */

        String edgeQuery = """
            FOR rel IN @relationships

                INSERT {
                    _from: CONCAT(
                        "benchmark_vertices/v",
                        TO_STRING(rel[0])
                    ),

                    _to: CONCAT(
                        "benchmark_vertices/v",
                        TO_STRING(rel[1])
                    )
                }

                INTO benchmark_edges
            """;

        client.runWrite(
                edgeQuery,
                java.util.Map.of(
                        "relationships",
                        relationshipData
                )
        );
    }

    private static class Relationship {

        final long source;
        final long target;

        Relationship(
                long source,
                long target) {

            this.source = source;
            this.target = target;
        }
    }
}