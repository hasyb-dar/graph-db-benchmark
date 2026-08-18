package com.benchmark.benchmark;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class DataLoader {

    private static final int MAX_RELATIONSHIPS = 100_000;
    private static final int BATCH_SIZE = 1_000;

    private static final int MAX_RETRIES = 5;
    private static final int RETRY_DELAY_SECONDS = 5;

    private final Driver driver;

    public DataLoader(Driver driver) {
        this.driver = driver;
    }

    public void load(String filePath) throws Exception {

        long startTime = System.nanoTime();

        long relationshipCount = 0;

        // Used to calculate the number of unique nodes.
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

                /*
                 * Track unique nodes.
                 */
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

                        batch =
                                new ArrayList<>(
                                        batch.subList(
                                                0,
                                                remaining
                                        )
                                );
                    }

                    if (!batch.isEmpty()) {

                        insertBatchWithRetry(batch);

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

                    batch =
                            new ArrayList<>(
                                    batch.subList(
                                            0,
                                            remaining
                                    )
                            );
                }

                if (!batch.isEmpty()) {

                    insertBatchWithRetry(batch);

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
                "        DATA LOAD COMPLETE"
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
                "Program finished successfully."
        );
    }

    private void insertBatchWithRetry(
            List<Relationship> relationships)
            throws Exception {

        int attempt = 0;

        while (true) {

            try {

                insertBatch(relationships);

                return;

            } catch (Exception e) {

                attempt++;

                System.out.println();

                System.out.println(
                        "WARNING: Batch insertion failed."
                );

                System.out.println(
                        "Retry attempt "
                                + attempt
                                + " of "
                                + MAX_RETRIES
                );

                System.out.println(
                        "Error: "
                                + e.getClass()
                                .getSimpleName()
                );

                if (attempt >= MAX_RETRIES) {

                    System.out.println();

                    System.out.println(
                            "ERROR: Maximum retry attempts reached."
                    );

                    throw e;
                }

                System.out.println(
                        "Waiting "
                                + RETRY_DELAY_SECONDS
                                + " seconds..."
                );

                Thread.sleep(
                        RETRY_DELAY_SECONDS * 1000L
                );
            }
        }
    }

    private void insertBatch(
            List<Relationship> relationships) {

        List<Value> data =
                new ArrayList<>(
                        relationships.size()
                );

        for (Relationship relationship :
                relationships) {

            data.add(
                    Values.parameters(
                            "source",
                            relationship.source,

                            "target",
                            relationship.target
                    )
            );
        }

        try (Session session =
                     driver.session()) {

            session.executeWrite(tx -> {

                tx.run(
                        """
                        UNWIND $relationships AS rel

                        MERGE (a:Person {
                            id: rel.source
                        })

                        MERGE (b:Person {
                            id: rel.target
                        })

                        MERGE (a)-[:FRIEND]->(b)
                        """,

                        Values.parameters(
                                "relationships",
                                data
                        )
                ).consume();

                return null;
            });
        }
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