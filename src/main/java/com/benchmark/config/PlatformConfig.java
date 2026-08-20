package com.benchmark.config;

import io.github.cdimascio.dotenv.Dotenv;

public record PlatformConfig(
        String name,
        String uri,
        String username,
        String password
) {

    public static PlatformConfig fromEnv(
            String name,
            String uriEnv,
            String userEnv,
            String passEnv
    ) {

        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();

        String uri = dotenv.get(uriEnv);
        String user = dotenv.get(userEnv);
        String pass = dotenv.get(passEnv);

        if (uri == null || user == null || pass == null) {
            throw new IllegalStateException(
                    "Could not load .env values for " + name +
                            ". Check that .env is in the project root."
            );
        }

        return new PlatformConfig(name, uri, user, pass);
    }
}