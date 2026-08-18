package com.benchmark.client;

import com.benchmark.config.PlatformConfig;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Works for any platform that speaks Bolt + Cypher: CognoDB, Neo4j Aura, Memgraph.
 * Just construct with a different PlatformConfig per platform.
 */
public class BoltGraphClient implements GraphClient {

    private final PlatformConfig config;
    private Driver driver;

    public BoltGraphClient(PlatformConfig config) {
        this.config = config;
    }

    @Override
    public String platformName() {
        return config.name();
    }

    @Override
    public void connect() {
        driver = GraphDatabase.driver(
                config.uri(),
                AuthTokens.basic(config.username(), config.password())
        );
        driver.verifyConnectivity();
    }

    @Override
    public List<Map<String, Object>> runQuery(String query, Map<String, Object> params) {
        try (Session session = driver.session()) {
            Result result = session.run(query, params == null ? Map.of() : params);
            List<Map<String, Object>> rows = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> row = new LinkedHashMap<>();
                record.keys().forEach(key -> row.put(key, record.get(key).asObject()));
                rows.add(row);
            }
            return rows;
        }
    }

    @Override
    public void runWrite(String query, Map<String, Object> params) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run(query, params == null ? Map.of() : params);
                return null;
            });
        }
    }

    @Override
    public void close() {
        if (driver != null) {
            driver.close();
        }
    }
}
