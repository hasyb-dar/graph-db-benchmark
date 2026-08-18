package com.benchmark.client;

import java.util.List;
import java.util.Map;

/**
 * Common interface every platform client implements, so loaders and
 * workload runners can stay platform-agnostic.
 */
public interface GraphClient extends AutoCloseable {

    /** Human-readable platform name, used in reports (e.g. "CognoDB", "Neo4j Aura"). */
    String platformName();

    /** Open the connection / verify connectivity. Called once before any work. */
    void connect();

    /**
     * Run a single query and return result rows as maps of column name -> value.
     * Used for both reads and writes; for writes the returned list may be empty.
     */
    List<Map<String, Object>> runQuery(String query, Map<String, Object> params);

    /** Run a write query in its own transaction (used by the data loader for batching). */
    void runWrite(String query, Map<String, Object> params);

    /** Optional: report footprint info if the platform exposes it (stored size, memory). */
    default Map<String, Object> footprint() {
        return Map.of();
    }

    @Override
    void close();
}