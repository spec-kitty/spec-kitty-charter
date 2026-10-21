package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.api.model.Diagnostic;
import ai.speckitty.charter.api.model.GuidanceEntry;
import ai.speckitty.charter.api.model.PackSummary;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relation;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Every domain enum that crosses the wire spells its values exactly as the contract enum does: each domain
 * token is a value of the generated enum, and the generated enum has no value the domain lacks.
 */
class WireTokenParityTest {

    /** One domain enum against its generated contract enum. */
    private record Pair(
            String contractEnum,
            List<String> domainTokens,
            List<String> contractValues,
            Function<String, ?> fromValue) {}

    private static List<Pair> pairs() {
        return List.of(
                new Pair(
                        "CharterRefusalCode",
                        tokens(RefusalCode.values(), RefusalCode::token),
                        values(CharterRefusal.CodeEnum.values(), CharterRefusal.CodeEnum::getValue),
                        CharterRefusal.CodeEnum::fromValue),
                new Pair(
                        "DiagnosticCode",
                        tokens(
                                ai.speckitty.charter.packs.domain.Diagnostic.Code.values(),
                                ai.speckitty.charter.packs.domain.Diagnostic.Code::token),
                        values(Diagnostic.CodeEnum.values(), Diagnostic.CodeEnum::getValue),
                        Diagnostic.CodeEnum::fromValue),
                new Pair(
                        "PackLevel",
                        tokens(PackLevel.values(), PackLevel::token),
                        values(PackSummary.LevelEnum.values(), PackSummary.LevelEnum::getValue),
                        PackSummary.LevelEnum::fromValue),
                new Pair(
                        "NodeKind",
                        tokens(NodeKind.values(), NodeKind::token),
                        values(GuidanceEntry.KindEnum.values(), GuidanceEntry.KindEnum::getValue),
                        GuidanceEntry.KindEnum::fromValue),
                new Pair(
                        "Relation",
                        tokens(Relation.values(), Relation::token),
                        values(GuidanceEntry.RelationEnum.values(), GuidanceEntry.RelationEnum::getValue),
                        GuidanceEntry.RelationEnum::fromValue));
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
