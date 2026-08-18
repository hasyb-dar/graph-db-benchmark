package com.benchmark.config;

/**
 * Simple holder for one platform's connection details.
 * Populate these from environment variables — never hardcode secrets.
 */
public record PlatformConfig(
        String name,
        String uri,
        String username,
        String password
) {
    public static PlatformConfig fromEnv(String name, String uriEnv, String userEnv, String passEnv) {
        String uri = System.getenv(uriEnv);
        String user = System.getenv(userEnv);
        String pass = System.getenv(passEnv);

        if (uri == null || user == null || pass == null) {
            throw new IllegalStateException(
                    "Missing env vars for " + name + ": expected " + uriEnv + ", " + userEnv + ", " + passEnv
            );
        }
        return new PlatformConfig(name, uri, user, pass);
    }
}