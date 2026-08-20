package dev.hogwai.platform.spi.data.access;

import java.util.Objects;

/** Connection settings used to open a data access. */
public record DataAccessConfiguration(String url, String username, String password) {

    /** Validates the connection settings. */
    public DataAccessConfiguration {
        Objects.requireNonNull(url, "url must not be null");
        if (url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        Objects.requireNonNull(username, "username must not be null");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        Objects.requireNonNull(password, "password must not be null");
    }

    /**
     * Returns this configuration without exposing the password.
     *
     * @return a redacted configuration description
     */
    @Override
    public String toString() {
        return "DataAccessConfiguration[url=" + url + ", username=" + username + ", password=<redacted>]";
    }
}
