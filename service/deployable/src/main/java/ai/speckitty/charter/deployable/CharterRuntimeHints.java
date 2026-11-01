package ai.speckitty.charter.deployable;

import ai.speckitty.charter.packs.infra.fetch.GlobalAddressResolverProvider;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.boot.context.properties.bind.BindableRuntimeHintsRegistrar;

/**
 * What the native executable needs that Spring AOT does not find on its own (ADR-0046 decision 5). AOT sees the beans,
 * the controllers' signatures and {@code application*.yaml}; it does not see a resource the service opens itself, a
 * body written outside a controller, or a JDK service provider. A missing hint fails only the native executable, at
 * the first use, so each one here is tested ({@code CharterRuntimeHintsTest}) and the executable is started in CI.
 * The API adapter registers the hints of its own transport bodies ({@code ProblemRuntimeHints}): only it names them.
 */
public class CharterRuntimeHints implements RuntimeHintsRegistrar {

    /** The Hibernate Validator implementations of {@link ContainerProperties}' {@code @NotEmpty} and {@code @NotBlank}. */
    private static final String[] CONTAINER_VALIDATORS = {
        "org.hibernate.validator.internal.constraintvalidators.bv.notempty.NotEmptyValidatorForCollection",
        "org.hibernate.validator.internal.constraintvalidators.bv.NotBlankValidator",
    };

    /** For Spring, which instantiates the registrar named by {@code @ImportRuntimeHints}. */
    public CharterRuntimeHints() {}

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        // The pack schemas and the file-kind table the YAML adapter loads from the class path (PackSchemas,
        // PackFileKind).
        hints.resources().registerPattern("packs/schemas/*.schema.json");
        hints.resources().registerPattern("packs/file-kinds.yaml");
        // The service and contract versions (ServiceVersion, ContractVersion) and the bundled contract the service
        // serves as GET /openapi.yaml (ContractDocumentWiring).
        hints.resources().registerPattern("ai/speckitty/charter/packs/infra/api/*-version.properties");
        hints.resources().registerPattern("ai/speckitty/charter/deployable/charter-openapi.yaml");
        // The schema validator's own 2020-12 meta-schemas, which it loads from its jar, and its message bundle.
        hints.resources().registerPattern("draft/2020-12/schema");
        hints.resources().registerPattern("draft/2020-12/meta/*");
        hints.resources().registerResourceBundle("jsv-messages");
        // The container settings, which ContainerWiring binds itself under the profile.
        BindableRuntimeHintsRegistrar.forTypes(ContainerProperties.class).registerHints(hints, classLoader);
        // Hibernate Validator reads the record's constraints from its fields and the values through them,
        // and creates the validators of its two constraints; the binding hints cover only the constructor.
        hints.reflection()
                .registerType(
                        ContainerProperties.class,
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.INVOKE_DECLARED_METHODS);
        for (String validator : CONTAINER_VALIDATORS) {
            hints.reflection().registerType(TypeReference.of(validator), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
        // The guide fetch's DNS-rebinding guard, a resolver provider the JDK loads with ServiceLoader. Without it the
        // service refuses to start with an allowlist set (GlobalAddressResolverProvider.installRequiringGuard).
        hints.resources().registerPattern("META-INF/services/java.net.spi.InetAddressResolverProvider");
        hints.reflection().registerType(GlobalAddressResolverProvider.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
    }
}
