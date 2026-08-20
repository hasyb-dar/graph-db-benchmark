package com.benchmark.benchmark;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class MemgraphDataLoader {

    private static final int MAX_RELATIONSHIPS = 100_000;
    private static final int BATCH_SIZE = 10_000;

    private final Driver driver;

    public MemgraphDataLoader(Driver driver) {
        this.driver = driver;
    }

    public void load(String filePath) throws Exception {

        long startTime = System.nanoTime();

        /*
         * Step 1:
         * Read the first 100,000 relationships and collect
         * all unique vertex IDs.
         */
        List<Map<String, Object>> relationships =
                new ArrayList<>(MAX_RELATIONSHIPS);

        java.util.HashSet<Long> vertexIds =
                new java.util.HashSet<>();

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

            String line;

            while ((line = reader.readLine()) != null) {

                if (line.startsWith("#")
                        || line.trim().isEmpty()) {
                    continue;
                }

                String[] parts =
                        line.trim().split("\\s+");

                if (parts.length < 2) {
                    continue;
                }

                try {

                    long source =
                            Long.parseLong(parts[0]);

                    long target =
                            Long.parseLong(parts[1]);

                    relationships.add(
                            Map.of(
                                    "source", source,
                                    "target", target
                            )
                    );

                    vertexIds.add(source);
                    vertexIds.add(target);

                } catch (NumberFormatException e) {
                    continue;
                }

                if (relationships.size()
                        >= MAX_RELATIONSHIPS) {
                    break;
                }
            }
        }

        System.out.println();
        System.out.println(
                "Unique vertices found: "
                        + vertexIds.size()
        );

        System.out.println(
                "Relationships found: "
                        + relationships.size()
        );

        /*
         * Step 2:
         * Create all vertices in batches.
         */
        System.out.println();
        System.out.println(
                "Loading vertices..."
        );

        List<Long> vertices =
                new ArrayList<>(vertexIds);

        long vertexCount = 0;

        for (int i = 0;
             i < vertices.size();
             i += BATCH_SIZE) {

            int end =
                    Math.min(
                            i + BATCH_SIZE,
                            vertices.size()
                    );

            List<Long> batch =
                    vertices.subList(i, end);

            insertVertexBatch(batch);

            vertexCount += batch.size();

            System.out.println(
                    "Loaded vertices: "
                            + vertexCount
            );
        }

        /*
         * Step 3:
         * Create relationships using existing vertices.
         */
        System.out.println();
        System.out.println(
                "Loading relationships..."
        );

        long relationshipCount = 0;

        for (int i = 0;
             i < relationships.size();
             i += BATCH_SIZE) {

            int end =
                    Math.min(
                            i + BATCH_SIZE,
                            relationships.size()
                    );

            List<Map<String, Object>> batch =
                    relationships.subList(i, end);

            insertRelationshipBatch(batch);

            relationshipCount += batch.size();

            System.out.println(
                    "Loaded relationships: "
                            + relationshipCount
            );
        }

        long endTime = System.nanoTime();

        double seconds =
                (endTime - startTime)
                        / 1_000_000_000.0;

        System.out.println();
        System.out.println(
                "======================================"
        );

        System.out.println(
                "       MEMGRAPH DATA LOAD COMPLETE"
        );

        System.out.println(
                "======================================"
        );

        System.out.println(
                "Nodes loaded: "
                        + vertexCount
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
                "Relationships/second: %.2f%n",
                seconds > 0
                        ? relationshipCount / seconds
                        : 0
        );

        System.out.println(
                "======================================"
        );
    }

    private void insertVertexBatch(
            List<Long> vertices) {

        String query =
                "UNWIND $vertices AS id " +
                        "CREATE (:Vertex {id: id})";

        try (Session session = driver.session()) {

            session.executeWrite(tx -> {

                tx.run(
                        query,
                        Values.parameters(
                                "vertices",
                                vertices
                        )
                ).consume();

                return null;
            });

        } catch (Exception e) {

            throw new RuntimeException(
                    "Memgraph vertex batch insert failed.",
                    e
            );
        }
    }

    private void insertRelationshipBatch(
            List<Map<String, Object>> relationships) {

        String query =
                "UNWIND $relationships AS rel " +
                        "MATCH (a:Vertex {id: rel.source}) " +
                        "MATCH (b:Vertex {id: rel.target}) " +
                        "CREATE (a)-[:RELATIONSHIP]->(b)";

        try (Session session = driver.session()) {

            session.executeWrite(tx -> {

                tx.run(
                        query,
                        Values.parameters(
                                "relationships",
                                relationships
                        )
                ).consume();

                return null;
            });

        } catch (Exception e) {

            throw new RuntimeException(
                    "Memgraph relationship batch insert failed.",
                    e
            );
        }
    }
}