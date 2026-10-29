package ai.speckitty.charter.deployable;

import ai.speckitty.charter.packs.infra.api.ContractDocument;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the bundled contract {@code GET /openapi.yaml} serves (#209). The build packages the {@code bundleContract}
 * output as the resource {@value #RESOURCE} beside this class; it is read and digested once, here. Without it the
 * service does not start: it never answers a missing, partial or stale document (ADR-0011, D1).
 */
@Configuration(proxyBeanMethods = false)
public class ContractDocumentWiring {

    /** The packaged bundle's name, as the contract build writes it. */
    static final String RESOURCE = "charter-openapi.yaml";

    /** For Spring, which instantiates the configuration class. */
    public ContractDocumentWiring() {}

    /**
     * The bundled contract.
     *
     * @return the document, read from the classpath
     * @throws IllegalStateException when the resource is missing, unreadable or not well-formed UTF-8
     */
    @Bean
    ContractDocument contractDocument() {
        return load(ContractDocumentWiring.class, RESOURCE);
    }

    /**
     * Reads a bundled contract from a resource.
     *
     * @param anchor the class the resource name is relative to
     * @param name the resource name
     * @return the document
     * @throws IllegalStateException when the resource is missing, unreadable or not well-formed UTF-8
     */
    static ContractDocument load(Class<?> anchor, String name) {
        byte[] bytes;
        try (InputStream in = anchor.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException(
                        name + " is missing from the build; the service cannot serve its contract");
            }
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(name + " cannot be read from the build", e);
        }
        try {
            return new ContractDocument(bytes);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " cannot be served: " + e.getMessage(), e);
        }
    }
}
