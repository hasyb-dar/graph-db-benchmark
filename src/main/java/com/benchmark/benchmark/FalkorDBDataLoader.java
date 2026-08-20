package com.benchmark.benchmark;

import com.benchmark.client.FalkorDBGraphClient;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Loads the Pokec relationships dataset into FalkorDB using the same
 * schema as MemgraphDataLoader: (:Vertex {id})-[:RELATIONSHIP]->(:Vertex).
 */
public class FalkorDBDataLoader {

    private static final int MAX_RELATIONSHIPS = 100_000;
    private static final int BATCH_SIZE = 1_000;

    private final FalkorDBGraphClient client;

    public FalkorDBDataLoader(FalkorDBGraphClient client) {
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

            List<long[]> batch = new ArrayList<>(BATCH_SIZE);

            String line;

            while ((line = reader.readLine()) != null) {

                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.trim().split("\\s+");

                if (parts.length < 2) {
                    continue;
                }

                long source;
                long target;

                try {

                    source = Long.parseLong(parts[0]);
                    target = Long.parseLong(parts[1]);

                } catch (NumberFormatException e) {
                    continue;
                }

                uniqueNodes.add(source);
                uniqueNodes.add(target);

                batch.add(new long[]{source, target});

                if (batch.size() >= BATCH_SIZE) {

                    int remaining =
                            MAX_RELATIONSHIPS - (int) relationshipCount;

                    if (batch.size() > remaining) {
                        batch = new ArrayList<>(
                                batch.subList(0, remaining)
                        );
                    }

                    if (!batch.isEmpty()) {

                        insertBatch(batch);

                        relationshipCount += batch.size();

                        System.out.println(
                                "Loaded relationships: "
                                        + relationshipCount
                        );
                    }

                    batch.clear();

                    if (relationshipCount >= MAX_RELATIONSHIPS) {
                        break;
                    }
                }
            }

            if (!batch.isEmpty()
                    && relationshipCount < MAX_RELATIONSHIPS) {

                int remaining =
                        MAX_RELATIONSHIPS - (int) relationshipCount;

                if (batch.size() > remaining) {
                    batch = new ArrayList<>(
                            batch.subList(0, remaining)
                    );
                }

                if (!batch.isEmpty()) {

                    insertBatch(batch);

                    relationshipCount += batch.size();

                    System.out.println(
                            "Loaded relationships: " + relationshipCount
                    );
                }
            }
        }

        long endTime = System.nanoTime();

        double seconds = (endTime - startTime) / 1_000_000_000.0;

        long nodeCount = uniqueNodes.size();

        double nodesPerSecond =
                seconds > 0 ? nodeCount / seconds : 0;

        double relationshipsPerSecond =
                seconds > 0 ? relationshipCount / seconds : 0;

        System.out.println();
        System.out.println("======================================");
        System.out.println("       FALKORDB DATA LOAD COMPLETE");
        System.out.println("======================================");
        System.out.println("Nodes loaded: " + nodeCount);
        System.out.println("Relationships loaded: " + relationshipCount);
        System.out.printf("Load time: %.3f seconds%n", seconds);
        System.out.printf("Nodes/second: %.2f%n", nodesPerSecond);
        System.out.printf(
                "Relationships/second: %.2f%n", relationshipsPerSecond
        );
        System.out.println("======================================");
    }

    /*
     * One Cypher statement per batch: UPSERT vertices for both endpoints,
     * then create the edges. Mirrors the pattern used by
     * ArangoDataLoader/MemgraphDataLoader, adapted to openCypher's MERGE.
     */
    private void insertBatch(List<long[]> relationships) {

        List<List<Long>> relationshipData =
                new ArrayList<>(relationships.size());

        for (long[] pair : relationships) {
            relationshipData.add(List.of(pair[0], pair[1]));
        }

        String query = """
                UNWIND $relationships AS rel
                MERGE (a:Vertex {id: rel[0]})
                MERGE (b:Vertex {id: rel[1]})
                MERGE (a)-[:RELATIONSHIP]->(b)
                """;

        client.runWrite(
                query,
                Map.of("relationships", relationshipData)
        );
    }
}