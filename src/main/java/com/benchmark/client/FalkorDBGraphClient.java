package com.benchmark.client;

import com.falkordb.Driver;
import com.falkordb.Graph;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.falkordb.impl.api.DriverImpl;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GraphClient implementation for FalkorDB, using the official jfalkordb
 * client (Maven: com.falkordb:jfalkordb).
 *
 * FalkorDB is a Redis module speaking openCypher, not Bolt, so it can't
 * reuse BoltGraphClient/PlatformConfig the way CognoDB/Neo4j/Memgraph do.
 * This class talks RESP via Jedis instead.
 *
 * All benchmark data lives under a single named graph key (see GRAPH_NAME)
 * inside the FalkorDB instance -- FalkorDB has no separate "database"
 * concept, a graph key is the closest equivalent.
 */
public class FalkorDBGraphClient implements GraphClient {

    private static final String GRAPH_NAME = "benchmark";

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final boolean useSsl;

    private JedisPool jedisPool;
    private Driver driver;
    private Graph graph;

    public FalkorDBGraphClient(
            String host,
            int port,
            String username,
            String password,
            boolean useSsl
    ) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.useSsl = useSsl;
    }

    @Override
    public String platformName() {
        return "FalkorDB";
    }

    @Override
    public void connect() {

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(32);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(2);

        DefaultJedisClientConfig.Builder configBuilder =
                DefaultJedisClientConfig.builder();

        if (username != null && !username.isBlank()) {
            configBuilder.user(username);
        }

        if (password != null && !password.isBlank()) {
            configBuilder.password(password);
        }

        if (useSsl) {
            configBuilder.ssl(true);
        }

        // Free-tier / cross-region instances can be slow, especially
        // before an index exists. Give queries generous headroom so a
        // slow MERGE doesn't get killed by Jedis's short default
        // socket timeout.
        configBuilder.connectionTimeoutMillis(10_000);
        configBuilder.socketTimeoutMillis(60_000);

        HostAndPort hostAndPort = new HostAndPort(host, port);

        JedisClientConfig clientConfig = configBuilder.build();

        jedisPool = new JedisPool(poolConfig, hostAndPort, clientConfig);

        driver = new DriverImpl(jedisPool);

        graph = driver.graph(GRAPH_NAME);

        // Verify connectivity with a trivial query.
        graph.query("RETURN 1");
    }

    @Override
    public List<Map<String, Object>> runQuery(
            String query,
            Map<String, Object> params
    ) {

        if (graph == null) {
            throw new IllegalStateException(
                    "FalkorDB is not connected."
            );
        }

        ResultSet resultSet =
                (params == null || params.isEmpty())
                        ? graph.query(query)
                        : graph.query(query, params);

        List<Map<String, Object>> rows = new ArrayList<>();

        for (Record record : resultSet) {

            Map<String, Object> row = new LinkedHashMap<>();

            for (String key : record.keys()) {
                row.put(key, record.getValue(key));
            }

            rows.add(row);
        }

        return rows;
    }

    @Override
    public void runWrite(String query, Map<String, Object> params) {
        // FalkorDB has no explicit-vs-autocommit transaction split like
        // Memgraph's Bolt driver does; every graph.query() call is its
        // own atomic operation, so writes and reads share one path.
        runQuery(query, params);
    }

    @Override
    public Map<String, Object> footprint() {

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("platform", "FalkorDB");
        result.put("graph", GRAPH_NAME);
        result.put("connected", graph != null);

        return result;
    }

    @Override
    public void close() {

        if (jedisPool != null) {

            try {
                jedisPool.close();
            } catch (Exception ignored) {
                // Ignore shutdown errors
            }

            jedisPool = null;
            driver = null;
            graph = null;
        }

        System.out.println("FalkorDB connection closed.");
    }
}