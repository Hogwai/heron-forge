package dev.hogwai.platform.spi.host;

import java.time.Duration;
import java.util.Objects;

/** Framework-independent host bind and request-timeout configuration.
 *
 * @param bindAddress    address on which the adapter binds
 * @param port           bind port, or zero for an automatically selected port
 * @param requestTimeout maximum request duration
 */
public record HostConfiguration(String bindAddress, int port, Duration requestTimeout) {

    /** Validates the host bind address, port, and timeout. */
    public HostConfiguration {
        Objects.requireNonNull(bindAddress, "bindAddress must not be null");
        if (bindAddress.isBlank()) {
            throw new IllegalArgumentException("bindAddress must not be blank");
        }
        if (port != 0 && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException("port must be 0 or between 1 and 65535");
        }
        Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be strictly positive");
        }
    }
}
