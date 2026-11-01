package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.ArtifactSummary;
import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.OfferingPack;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ArtifactReadService;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * Every domain enum that crosses the wire spells its values exactly as the contract enum does: each domain
 * token is a value of the contract enum, and the contract enum has no value the domain lacks.
 */
class WireTokenParityTest {

    /** One domain enum against its contract enum. */
    private record Pair(
            String contractEnum,
            List<String> domainTokens,
            List<String> contractValues,
            Function<String, ?> fromValue) {}

    /** The values of the contract's {@code ArtifactKindToken} enum, the {@code ?kind=} filter's. */
    private static final List<String> ARTIFACT_KIND_TOKENS = artifactKindTokens();

    private static List<Pair> pairs() {
        return List.of(
                new Pair(
                        "CharterRefusalCode",
                        tokens(RefusalCode.values(), RefusalCode::token),
                        values(CharterRefusal.CodeEnum.values(), CharterRefusal.CodeEnum::getValue),
                        CharterRefusal.CodeEnum::fromValue),
                // The served kinds against ArtifactKindToken, which only the ?kind= filter references. The generator
                // types that parameter as a String, so the enum is read from the contract file itself.
                new Pair(
                        "ArtifactKindToken",
                        tokens(ArtifactReadService.SERVED.toArray(ArtifactKind[]::new), ArtifactKind::token),
                        ARTIFACT_KIND_TOKENS,
                        token -> member(ARTIFACT_KIND_TOKENS, token)),
                // The pack levels against PackLevel, as the generator types it on OfferingPack.level.
                new Pair(
                        "PackLevel",
                        tokens(PackLevel.values(), PackLevel::token),
                        values(OfferingPack.LevelEnum.values(), OfferingPack.LevelEnum::getValue),
                        OfferingPack.LevelEnum::fromValue));
    }

    private static List<String> artifactKindTokens() {
        Path file = Path.of(System.getProperty("charter.contractSchemas"), "ArtifactKindToken.yaml");
        try {
            Map<?, ?> document = (Map<?, ?>) new Load(LoadSettings.builder().build())
                    .loadFromString(Files.readString(file, StandardCharsets.UTF_8));
            return ((List<?>) document.get("enum"))
                    .stream().map(String::valueOf).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(file.toString(), e);
        }
    }

    private static String member(List<String> values, String token) {
        if (!values.contains(token)) {
            throw new IllegalArgumentException(token + " is not one of " + values);
        }
        return token;
    }

    private static <E> List<String> tokens(E[] constants, Function<E, String> token) {
        return Arrays.stream(constants).map(token).toList();
    }

    private static <E> List<String> values(E[] constants, Function<E, String> value) {
        return tokens(constants, value);
    }

    /** The parity check of one pair: every domain token is a contract value, and there is no other value. */
    private static void assertParity(Pair pair) {
        for (String token : pair.domainTokens()) {
            assertThatCode(() -> pair.fromValue().apply(token))
                    .as(pair.contractEnum() + " accepts " + token)
                    .doesNotThrowAnyException();
        }
        assertThat(pair.contractValues())
                .as(pair.contractEnum() + " values")
                .containsExactlyInAnyOrderElementsOf(pair.domainTokens());
    }

    private static Pair withTokens(Pair pair, List<String> tokens) {
        return new Pair(pair.contractEnum(), tokens, pair.contractValues(), pair.fromValue());
    }

    @TestFactory
    Stream<DynamicTest> everyDomainTokenIsAContractValueAndNoMore() {
        return pairs().stream().map(pair -> dynamicTest(pair.contractEnum(), () -> assertParity(pair)));
    }

    @Test
    void aResponseKindIsAnOpenSlugSoAClientToleratesAKindItDoesNotKnow() throws NoSuchMethodException {
        // ArtifactSummary.kind and Artifact.kind are open slugs, not ArtifactKindToken: a kind a later version serves
        // (glossary, procedure) must not break a client generated from this one.
        for (Class<?> model : List.of(ArtifactSummary.class, Artifact.class)) {
            assertThat(model.getMethod("getKind").getReturnType())
                    .as(model.getSimpleName() + ".kind")
                    .isEqualTo(String.class);
            Pattern pattern = model.getMethod("getKind").getAnnotation(Pattern.class);
            assertThat(pattern).as(model.getSimpleName() + ".kind's @Pattern").isNotNull();
            assertThat("glossary").matches(pattern.regexp());
            assertThat("Tactic").doesNotMatch(pattern.regexp());
        }
        assertThat(new ArtifactSummary("glossary", "g", "G", Map.<String, Link>of()).getKind())
                .isEqualTo("glossary");
    }

    @TestFactory
    Stream<DynamicTest> theParityCheckFailsOnASkewedPair() {
        // Positive control for every pair: the same check fails when the domain has a token the contract lacks,
        // and when the contract has a value the domain lacks.
        return pairs().stream()
                .map(pair -> dynamicTest(pair.contractEnum(), () -> {
                    List<String> extra = Stream.concat(pair.domainTokens().stream(), Stream.of("not_a_contract_value"))
                            .toList();
                    assertThatThrownBy(() -> assertParity(withTokens(pair, extra)))
                            .isInstanceOf(AssertionError.class);
                    List<String> missing =
                            pair.domainTokens().subList(1, pair.domainTokens().size());
                    assertThatThrownBy(() -> assertParity(withTokens(pair, missing)))
                            .isInstanceOf(AssertionError.class);
                }));
    }
}
