package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import ai.speckitty.charter.activations.domain.ActivationChange;
import ai.speckitty.charter.activations.domain.CascadeScope;
import ai.speckitty.charter.activations.domain.EffectiveSet;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport;
import ai.speckitty.charter.api.model.ActivationWarning;
import ai.speckitty.charter.api.model.DenyNotApplied;
import ai.speckitty.charter.api.model.EffectiveArtifact;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
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
import org.junit.jupiter.api.TestFactory;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * The enums of the activation wire shapes against the domain enums that fill them, as {@code WireTokenParityTest} does
 * for the packs slice: each domain token is a value of the contract enum, and the contract enum has no value the
 * domain lacks. The mappers read each enum by {@code fromValue}, so a constant added on one side only would be a
 * runtime 500, not a compile error (#430).
 *
 * <p>{@code RequiredNotInForce.reason} has no row: its domain type is {@link EffectiveSet.Reason}, which has nine
 * values against the contract's two, and narrowing it is a change of the domain records and their readers.
 */
class ActivationWireTokenParityTest {

    /** One domain enum against its contract enum. */
    private record Pair(
            String contractEnum,
            List<String> domainTokens,
            List<String> contractValues,
            Function<String, ?> fromValue) {}

    /** The values of the contract's {@code CascadeKind} enum; the generator types {@code cascade} as strings. */
    private static final List<String> CASCADE_KINDS = contractEnum("CascadeKind.yaml");

    /** The cascade's kinds are the activatable kinds, and {@code all} for every one of them (rule W21). */
    private static final List<String> ACTIVATABLE_AND_ALL = Stream.concat(
                    Arrays.stream(ArtifactKind.values())
                            .filter(ArtifactKind::activatable)
                            .map(ArtifactKind::token),
                    Stream.of(CascadeScope.ALL))
            .toList();

    private static List<Pair> pairs() {
        return List.of(
                // No Java enum stands behind CascadeKind: it is ArtifactKind.activatable plus CascadeScope.ALL.
                new Pair("CascadeKind", ACTIVATABLE_AND_ALL, CASCADE_KINDS, token -> member(CASCADE_KINDS, token)),
                new Pair(
                        "EffectiveArtifactState",
                        tokens(EffectiveSet.State.values(), EffectiveSet.State::token),
                        values(EffectiveArtifact.StateEnum.values(), EffectiveArtifact.StateEnum::getValue),
                        EffectiveArtifact.StateEnum::fromValue),
                new Pair(
                        "EffectiveArtifactReason",
                        tokens(EffectiveSet.Reason.values(), EffectiveSet.Reason::token),
                        values(EffectiveArtifact.ReasonEnum.values(), EffectiveArtifact.ReasonEnum::getValue),
                        EffectiveArtifact.ReasonEnum::fromValue),
                new Pair(
                        "ArtifactPolicy",
                        tokens(EffectiveSet.Policy.values(), EffectiveSet.Policy::token),
                        values(EffectiveArtifact.PolicyEnum.values(), EffectiveArtifact.PolicyEnum::getValue),
                        EffectiveArtifact.PolicyEnum::fromValue),
                new Pair(
                        "DenyNotApplied.reason",
                        tokens(EffectiveSkippedReport.DenyReason.values(), EffectiveSkippedReport.DenyReason::token),
                        values(DenyNotApplied.ReasonEnum.values(), DenyNotApplied.ReasonEnum::getValue),
                        DenyNotApplied.ReasonEnum::fromValue),
                new Pair(
                        "ActivationWarning.code",
                        tokens(ActivationChange.Code.values(), ActivationChange.Code::token),
                        values(ActivationWarning.CodeEnum.values(), ActivationWarning.CodeEnum::getValue),
                        ActivationWarning.CodeEnum::fromValue));
    }

    private static List<String> contractEnum(String schemaFile) {
        Path file = Path.of(System.getProperty("charter.contractSchemas"), schemaFile);
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
