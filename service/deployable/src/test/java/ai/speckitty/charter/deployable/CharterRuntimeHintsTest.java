package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * What the native executable must carry that Spring AOT does not find on its own (ADR-0046 decision 5; the research
 * items N2 to N8 of mission service-release-native): the resources the service loads itself, the bodies written
 * outside a controller's signature, and the guide fetch's resolver guard. Each assertion names a resource or type the
 * service really loads; a missing hint fails only the native executable, which these tests catch on the JVM.
 */
class CharterRuntimeHintsTest {

    private final RuntimeHints hints = registered();

    private static RuntimeHints registered() {
        RuntimeHints hints = new RuntimeHints();
        new CharterRuntimeHints().registerHints(hints, CharterRuntimeHintsTest.class.getClassLoader());
        return hints;
    }

    @Test
    void theApplicationImportsTheHints() {
        assertThat(CharterServiceApplication.class.getAnnotation(ImportRuntimeHints.class))
                .isNotNull()
                .extracting(ImportRuntimeHints::value)
                .isEqualTo(new Class<?>[] {CharterRuntimeHints.class});
    }

    @Test
    void thePackSchemasAndTheFileKindsAreIncluded() {
        assertThat(RuntimeHintsPredicates.resource().forResource("packs/file-kinds.yaml"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.resource().forResource("packs/schemas/charter.schema.json"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.resource().forResource("packs/schemas/agent-profile.schema.json"))
                .accepts(hints);
    }

    @Test
    void theVersionsAndTheBundledContractAreIncluded() {
        assertThat(RuntimeHintsPredicates.resource()
                        .forResource("ai/speckitty/charter/packs/infra/api/service-version.properties"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.resource()
                        .forResource("ai/speckitty/charter/packs/infra/api/contract-version.properties"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.resource()
                        .forResource("ai/speckitty/charter/deployable/charter-openapi.yaml"))
                .accepts(hints);
    }

    @Test
    void theSchemaValidatorsMetaSchemasAndMessagesAreIncluded() {
        assertThat(RuntimeHintsPredicates.resource().forResource("draft/2020-12/schema"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.resource().forResource("draft/2020-12/meta/validation"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.resource().forBundle("jsv-messages")).accepts(hints);
    }

    @Test
    void theContainerSettingsBind() {
        assertThat(RuntimeHintsPredicates.reflection().onType(ContainerProperties.class))
                .accepts(hints);
    }

    /**
     * Hibernate Validator reads a record's constraints from its fields, and the values through them: binding hints
     * register only the constructor, so without these the native executable would skip or fail the validation.
     */
    @Test
    void theContainerSettingsCanBeValidated() throws NoSuchFieldException {
        assertThat(RuntimeHintsPredicates.reflection()
                        .onFieldAccess(ContainerProperties.class.getDeclaredField("allowedHosts")))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                        .onType(
                                TypeReference.of(
                                        "org.hibernate.validator.internal.constraintvalidators.bv.notempty.NotEmptyValidatorForCollection"))
                        .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                        .onType(TypeReference.of(
                                "org.hibernate.validator.internal.constraintvalidators.bv.NotBlankValidator"))
                        .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
                .accepts(hints);
    }

    @Test
    void theResolverGuardIsLoadedAsAServiceProvider() {
        assertThat(RuntimeHintsPredicates.resource()
                        .forResource("META-INF/services/java.net.spi.InetAddressResolverProvider"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                        .onType(TypeReference.of(
                                "ai.speckitty.charter.packs.infra.fetch.GlobalAddressResolverProvider"))
                        .withMemberCategory(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS))
                .accepts(hints);
    }
}
