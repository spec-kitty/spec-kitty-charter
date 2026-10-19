package ai.speckitty.charter.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * The version of this service build, which the contract reports as {@code ServiceInfo.serviceVersion}. It is
 * separate from {@link ContractVersion}: the build writes the root project's {@code version} (from
 * {@code gradle.properties}) into the resource {@code service-version.properties} beside this class.
 */
final class ServiceVersion {

    private static final String VALUE = load();

    private ServiceVersion() {}

    /**
     * The service version.
     *
     * @return the build's version, a semantic version, for example {@code 0.1.0-SNAPSHOT}
     */
    static String value() {
        return VALUE;
    }

    private static String load() {
        Properties properties = new Properties();
        try (InputStream in = ServiceVersion.class.getResourceAsStream("service-version.properties")) {
            if (in == null) {
                throw new IllegalStateException("service-version.properties is missing from the build");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String version = properties.getProperty("version");
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("service-version.properties has no version");
        }
        return version;
    }
}
