package dev.hogwai.platform.runtime.provider;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

/**
 * Package-private test {@link ClassLoader} that counts how many times the
 * {@code META-INF/services/dev.hogwai.platform.spi.provider.ProviderFactory}
 * resource is enumerated and can be configured to fail that enumeration with a
 * {@link SecurityException} or a {@link LinkageError}.
 *
 * <p>Used to prove that each registry instance performs exactly one discovery
 * pass and that relevant discovery failures are normalized without leaking
 * details.
 */
final class TestServiceClassLoader extends ClassLoader {

    private static final String SERVICE_RESOURCE =
            "META-INF/services/dev.hogwai.platform.spi.provider.ProviderFactory";

    private int serviceResourceEnumerations;
    private SecurityException securityFailure;
    private LinkageError linkageFailure;

    TestServiceClassLoader(ClassLoader parent) {
        super(parent);
    }

    /**
     * Configures the loader to throw a {@link SecurityException} when the
     * service resource is enumerated.
     */
    void failWithSecurity() {
        this.securityFailure = new SecurityException("access denied");
    }

    /**
     * Configures the loader to throw a {@link LinkageError} when the service
     * resource is enumerated.
     */
    void failWithLinkage() {
        this.linkageFailure = new NoClassDefFoundError("missing dependency");
    }

    /**
     * @return the number of service resource enumerations
     */
    int serviceResourceEnumerations() {
        return serviceResourceEnumerations;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (SERVICE_RESOURCE.equals(name)) {
            serviceResourceEnumerations++;
            if (securityFailure != null) {
                throw securityFailure;
            }
            if (linkageFailure != null) {
                throw linkageFailure;
            }
        }
        return super.getResources(name);
    }
}