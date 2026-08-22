package dev.hogwai.platform.spi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a platform service and carries its identity metadata.
 *
 * <p>The {@code heron-platform-processor} annotation processor generates the
 * corresponding {@code META-INF/services/<interface>} descriptor for any
 * annotated class whose {@link #value()} names one of the platform service
 * contracts: {@code ProviderFactory}, {@code DataAccessFactory} or
 * {@code HostAdapter}. The processor also validates the declaration at compile
 * time: the annotated class must be public and non-abstract with a public
 * no-argument constructor, it must implement the declared contract, the
 * identifier must be non-blank and free of whitespace, the version must be a
 * canonical {@code major.minor.patch} string, and two services of the same
 * contract may not declare the same identifier inside a single compilation.
 *
 * <p>The identifier mirrors the {@code providerId} carried by a provider
 * descriptor; keeping it on the annotation lets tooling discover service
 * identity without instantiating the implementation. The runtime remains the
 * final authority and rejects duplicated provider identifiers across modules.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface HeronService {

    /**
     * Returns the service contract implemented by the annotated class.
     *
     * @return the service contract interface
     */
    Class<?> value();

    /**
     * Returns the service identifier, unique among services of the same
     * contract. Must be non-blank and free of whitespace.
     *
     * @return the service identifier
     */
    String id();

    /**
     * Returns the service version as a canonical {@code major.minor.patch}
     * string.
     *
     * @return the service version
     */
    String version() default "1.0.0";
}
