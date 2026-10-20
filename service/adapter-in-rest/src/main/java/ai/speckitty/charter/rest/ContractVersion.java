package ai.speckitty.charter.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * The {@code info.version} of the contract this service was built against. The build writes it from
 * {@code contracts/charter/openapi.yaml} into the resource {@code contract-version.properties} beside this class.
 */
final class ContractVersion {

    private static final String VALUE = load();

    private ContractVersion() {}

    /**
     * The contract version.
     *
     * @return the contract's {@code info.version}, for example {@code 0.1.0}
     */
    static String value() {
        return VALUE;
    }

    private static String load() {
        Properties properties = new Properties();
        try (InputStream in = ContractVersion.class.getResourceAsStream("contract-version.properties")) {
            if (in == null) {
                throw new IllegalStateException("contract-version.properties is missing from the build");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String version = properties.getProperty("version");
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("contract-version.properties has no version");
        }
        return version;
    }
}
