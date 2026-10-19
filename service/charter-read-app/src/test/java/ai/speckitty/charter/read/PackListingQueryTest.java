package ai.speckitty.charter.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.domain.CharterOffering;
import ai.speckitty.charter.domain.CharterPack;
import ai.speckitty.charter.domain.CharterPackRepository;
import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.Diagnostic;
import ai.speckitty.charter.domain.DoctrineGraph;
import ai.speckitty.charter.domain.DrgEdge;
import ai.speckitty.charter.domain.DrgNode;
import ai.speckitty.charter.domain.NodeKind;
import ai.speckitty.charter.domain.PackDescriptor;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.domain.RefusalCode;
import ai.speckitty.charter.domain.Relation;
import ai.speckitty.charter.domain.Urn;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The listing asks the repository port exactly once per answer and passes its refusal through unchanged. A mock is
 * the clearest way to state "once and nothing else" about the port; the other read tests use hand-written lambdas.
 */
@ExtendWith(MockitoExtension.class)
class PackListingQueryTest {

    private static final Urn TYPE = Urn.missionType("software-dev");
    private static final Urn D1 = new Urn(NodeKind.DIRECTIVE, "D1");

    @Mock
    private CharterPackRepository packs;

    private static final String SOURCES = "sha256:" + "6".repeat(64);

    private PackListingQuery query() {
        return new PackListingQuery(new ConsistentLoad(packs, () -> SOURCES));
    }

    @Test
    void summarizesEveryPackOfOneLoadInLayerOrder() {
        CharterPack org = new CharterPack(
                "acme",
                PackLevel.ORG,
                Optional.of(new PackDescriptor("acme", "2.0.0", Optional.of("base"), "Acme", PackLevel.ORG)),
                new DoctrineGraph(
                        List.of(new DrgNode(TYPE, Optional.empty()), new DrgNode(D1, Optional.empty())),
                        List.of(DrgEdge.of(TYPE, D1, Relation.SCOPE))),
                List.of());
        Diagnostic missingLevel = new Diagnostic(Diagnostic.Code.PACK_LEVEL_MISSING, "no pack.yaml");
        CharterPack builtIn = new CharterPack(
                "built-in", PackLevel.BUILT_IN, Optional.empty(), DoctrineGraph.empty(), List.of(missingLevel));
        when(packs.load()).thenReturn(new CharterOffering(List.of(org, builtIn)));

        PackListing answer = query().answer();
        List<PackSummary> listing = answer.packs();

        assertThat(listing)
                .extracting(
                        PackSummary::name,
                        PackSummary::level,
                        PackSummary::packId,
                        PackSummary::packVersion,
                        PackSummary::parentPack,
                        PackSummary::nodeCount,
                        PackSummary::edgeCount,
                        PackSummary::diagnostics)
                .containsExactly(
                        tuple(
                                "built-in",
                                PackLevel.BUILT_IN,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                0,
                                0,
                                List.of(missingLevel)),
                        tuple(
                                "acme",
                                PackLevel.ORG,
                                Optional.of("acme"),
                                Optional.of("2.0.0"),
                                Optional.of("base"),
                                2,
                                1,
                                List.of()));
        assertThat(answer.sourceFingerprint()).isEqualTo(SOURCES);
        verify(packs).load();
        verifyNoMoreInteractions(packs);
    }

    @Test
    void passesTheRepositorysRefusalThroughUnchanged() {
        CharterRefusal unreadable = new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack acme is not a directory");
        when(packs.load()).thenThrow(unreadable);

        PackListingQuery query = query();

        assertThatThrownBy(query::answer).isSameAs(unreadable);
        verify(packs).load();
        verifyNoMoreInteractions(packs);
    }
}
