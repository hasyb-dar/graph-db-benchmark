package com.benchmark;

import com.benchmark.client.BoltGraphClient;
import com.benchmark.config.PlatformConfig;

import java.util.List;
import java.util.Map;

/**
 * Standalone sanity check — run this class directly to confirm the
 * CognoDB connection and driver setup work before building the full harness.
 * Delete or ignore once the real benchmark runner is in place.
 */
public class ConnectionTest {

    public static void main(String[] args) {
        PlatformConfig cognoDb = PlatformConfig.fromEnv(
                "CognoDB",
                "COGNODB_URI",
                "COGNODB_USER",
                "COGNODB_PASSWORD"
        );

        try (BoltGraphClient client = new BoltGraphClient(cognoDb)) {
            client.connect();
            System.out.println("Connected to " + client.platformName() + " successfully.");

            List<Map<String, Object>> result = client.runQuery("RETURN 1 AS test", Map.of());
            System.out.println("Query result: " + result);
        }
    }
}
