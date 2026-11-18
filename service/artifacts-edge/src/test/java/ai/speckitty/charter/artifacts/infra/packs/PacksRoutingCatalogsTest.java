package ai.speckitty.charter.artifacts.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.artifacts.application.RoutingCatalogReads;
import ai.speckitty.charter.artifacts.application.RoutingCatalogView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.routing.domain.AccessMethod;
import ai.speckitty.charter.routing.domain.CatalogSource;
import ai.speckitty.charter.routing.domain.Grade;
import ai.speckitty.charter.routing.domain.ModelCost;
import ai.speckitty.charter.routing.domain.ModelEntry;
import ai.speckitty.charter.routing.domain.RoutingCatalog;
import ai.speckitty.charter.routing.domain.RoutingObjective;
import ai.speckitty.charter.routing.domain.RoutingPolicy;
import ai.speckitty.charter.routing.domain.RoutingWeights;
import ai.speckitty.charter.routing.domain.TaskFit;
import ai.speckitty.charter.routing.domain.TaskType;
import ai.speckitty.charter.routing.domain.TierConstraint;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The packs adapter copies the routing slice's catalog into the edge's view, every member of every record, each enum as
 * its token and each number the exact decimal the file writes; the view has a member for every member of the domain, so
 * a field added to the routing domain cannot be dropped silently. The list is in id order, and the revision and the
 * verified fingerprint pass through.
 */
class PacksRoutingCatalogsTest {

    private static final PackIdentifier QA = new PackIdentifier("qa");
    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final PackRevision REVISION = PackRevision.compute(List.of("qa"));
    private static final String AT = "2026-11-18T00:00:00Z";

    /** A catalog that declares every optional member, each grade distinct from its neighbours. */
    private static RoutingCatalog full(String id) {
        return new RoutingCatalog(
                id,
                QA,
                Optional.of("A full catalog."),
                AT,
                List.of(new TaskType(
                        "review",
                        "Review",
                        Optional.of("Reviews a change."),
                        Optional.of(Grade.HIGH),
                        Optional.of(Grade.LOW))),
                List.of(new ModelEntry(
                        "model-a",
                        "example",
                        Optional.of("family-a"),
                        List.of("Bash", "Edit"),
                        List.of("careful"),
                        List.of("slow"),
                        List.of(new TaskFit(
                                "review",
                                new BigDecimal("0.90"),
                                Optional.of(Grade.MEDIUM),
                                Optional.of("Reads well."))),
                        new ModelCost(
                                CostTier.PREMIUM,
                                Optional.of(new BigDecimal("3.00")),
                                Optional.of(new BigDecimal("15.50")),
                                Optional.of("USD"),
                                Optional.of("https://example.invalid/pricing")),
                        Optional.of(Grade.LOW))),
                new RoutingPolicy(
                        RoutingObjective.QUALITY_FIRST,
                        new RoutingWeights(
                                new BigDecimal("1.0"),
                                new BigDecimal("0.20"),
                                new BigDecimal("0.1"),
                                new BigDecimal("0")),
                        List.of(new TierConstraint("review", CostTier.MEDIUM))),
                List.of(new CatalogSource(
                        "example", "https://example.invalid/catalog", AccessMethod.API, AT, Optional.of("CC-BY-4.0"))));
    }

    /** A catalog that declares no optional member. */
    private static RoutingCatalog minimal(String id) {
        return new RoutingCatalog(
                id,
                QA,
                Optional.empty(),
                AT,
                List.of(new TaskType("triage", "Triage", Optional.empty(), Optional.empty(), Optional.empty())),
                List.of(new ModelEntry(
                        "model-b",
                        "example",
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new TaskFit("triage", new BigDecimal("0.5"), Optional.empty(), Optional.empty())),
                        new ModelCost(
                                CostTier.LOW, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                        Optional.empty())),
                new RoutingPolicy(
                        RoutingObjective.COST_FIRST,
                        new RoutingWeights(
                                new BigDecimal("0.3"), new BigDecimal("0.7"), new BigDecimal("0"), new BigDecimal("0")),
                        List.of()),
                List.of(new CatalogSource(
                        "example", "https://example.invalid/catalog", AccessMethod.MANUAL, AT, Optional.empty())));
    }

    private static List<String> names(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private final ForeignArtifactRead packs = mock(ForeignArtifactRead.class);

    @Test
    void everyViewHasTheMembersOfItsDomainRecordInOrder() {
        assertThat(names(RoutingCatalogView.class)).isEqualTo(names(RoutingCatalog.class));
        assertThat(names(RoutingCatalogView.TaskTypeView.class)).isEqualTo(names(TaskType.class));
        assertThat(names(RoutingCatalogView.ModelView.class)).isEqualTo(names(ModelEntry.class));
        assertThat(names(RoutingCatalogView.FitView.class)).isEqualTo(names(TaskFit.class));
        assertThat(names(RoutingCatalogView.CostView.class)).isEqualTo(names(ModelCost.class));
        assertThat(names(RoutingCatalogView.SourceView.class)).isEqualTo(names(CatalogSource.class));
        assertThat(names(RoutingCatalogView.ConstraintView.class)).isEqualTo(names(TierConstraint.class));
    }

    @Test
    void thePolicyViewHasThePolicysMembersWithTheWeightsInlinedInPlace() {
        List<String> expected = new ArrayList<>();
        for (String name : names(RoutingPolicy.class)) {
            if (name.equals("weights")) {
                expected.addAll(names(RoutingWeights.class));
            } else {
                expected.add(name);
            }
        }

        assertThat(names(RoutingCatalogView.PolicyView.class)).isEqualTo(expected);
    }

    @Test
    void aFullCatalogMapsEveryMemberEachEnumAsItsTokenAndEachDecimalExactly() {
        RoutingCatalogView view = PacksRoutingCatalogs.viewOf(full("full"));

        assertThat(view.id()).isEqualTo("full");
        assertThat(view.packId()).isEqualTo(QA);
        assertThat(view.description()).contains("A full catalog.");
        assertThat(view.generatedAt()).isEqualTo(AT);
        assertThat(view.taskTypes())
                .containsExactly(new RoutingCatalogView.TaskTypeView(
                        "review", "Review", Optional.of("Reviews a change."), Optional.of("high"), Optional.of("low")));
        assertThat(view.models()).singleElement().satisfies(model -> {
            assertThat(model.id()).isEqualTo("model-a");
            assertThat(model.provider()).isEqualTo("example");
            assertThat(model.family()).contains("family-a");
            assertThat(model.tools()).containsExactly("Bash", "Edit");
            assertThat(model.strengths()).containsExactly("careful");
            assertThat(model.weaknesses()).containsExactly("slow");
            assertThat(model.taskFit())
                    .containsExactly(new RoutingCatalogView.FitView(
                            "review", new BigDecimal("0.90"), Optional.of("medium"), Optional.of("Reads well.")));
            assertThat(model.cost())
                    .isEqualTo(new RoutingCatalogView.CostView(
                            "premium",
                            Optional.of(new BigDecimal("3.00")),
                            Optional.of(new BigDecimal("15.50")),
                            Optional.of("USD"),
                            Optional.of("https://example.invalid/pricing")));
            assertThat(model.latencyTier()).contains("low");
        });
        assertThat(view.policy())
                .isEqualTo(new RoutingCatalogView.PolicyView(
                        "quality_first",
                        new BigDecimal("1.0"),
                        new BigDecimal("0.20"),
                        new BigDecimal("0.1"),
                        new BigDecimal("0"),
                        List.of(new RoutingCatalogView.ConstraintView("review", "medium"))));
        assertThat(view.sources())
                .containsExactly(new RoutingCatalogView.SourceView(
                        "example", "https://example.invalid/catalog", "api", AT, Optional.of("CC-BY-4.0")));
    }

    @Test
    void aMinimalCatalogMapsEveryOptionalMemberToEmpty() {
        RoutingCatalogView view = PacksRoutingCatalogs.viewOf(minimal("lean"));

        assertThat(view)
                .isEqualTo(new RoutingCatalogView(
                        "lean",
                        QA,
                        Optional.empty(),
                        AT,
                        List.of(new RoutingCatalogView.TaskTypeView(
                                "triage", "Triage", Optional.empty(), Optional.empty(), Optional.empty())),
                        List.of(new RoutingCatalogView.ModelView(
                                "model-b",
                                "example",
                                Optional.empty(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(new RoutingCatalogView.FitView(
                                        "triage", new BigDecimal("0.5"), Optional.empty(), Optional.empty())),
                                new RoutingCatalogView.CostView(
                                        "low", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                                Optional.empty())),
                        new RoutingCatalogView.PolicyView(
                                "cost_first",
                                new BigDecimal("0.3"),
                                new BigDecimal("0.7"),
                                new BigDecimal("0"),
                                new BigDecimal("0"),
                                List.of()),
                        List.of(new RoutingCatalogView.SourceView(
                                "example", "https://example.invalid/catalog", "manual", AT, Optional.empty()))));
    }

    @Test
    void theListIsInIdOrderWithTheRevisionAndTheFingerprint() {
        when(packs.held(QA, ArtifactKind.ROUTING_CATALOG))
                .thenReturn(new ConsistentRead.Verified<>(
                        new ForeignArtifactRead.Held(List.of(minimal("zeta"), full("alpha")), REVISION), FINGERPRINT));

        RoutingCatalogReads.Listed listed = new PacksRoutingCatalogs(packs).list(QA);

        assertThat(listed.catalogs()).extracting(RoutingCatalogView::id).containsExactly("alpha", "zeta");
        assertThat(listed.catalogs())
                .extracting(catalog -> catalog.policy().objective())
                .containsExactly("quality_first", "cost_first");
        assertThat(listed.revision()).isEqualTo(REVISION.token());
        assertThat(listed.sourceFingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void theItemIsTheViewOfTheCatalogItsPackStoresWithTheRevisionAndTheFingerprint() {
        when(packs.read(QA, ArtifactKind.ROUTING_CATALOG, "full", 0))
                .thenReturn(new ConsistentRead.Verified<>(
                        new ForeignArtifactRead.Foreign(full("full"), REVISION, Optional.empty(), List.of()),
                        FINGERPRINT));

        RoutingCatalogReads.Read read = new PacksRoutingCatalogs(packs).catalog(QA, "full");

        assertThat(read.catalog()).isEqualTo(PacksRoutingCatalogs.viewOf(full("full")));
        assertThat(read.revision()).isEqualTo(REVISION.token());
        assertThat(read.sourceFingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void theTwinsListReadAtAnotherFingerprintIsStaleInput() {
        when(packs.held(QA, ArtifactKind.ROUTING_CATALOG))
                .thenReturn(new ConsistentRead.Verified<>(
                        new ForeignArtifactRead.Held(List.of(full("full")), REVISION), FINGERPRINT));
        PacksRoutingCatalogs adapter = new PacksRoutingCatalogs(packs);

        assertThat(adapter.list(QA, FINGERPRINT).catalogs())
                .extracting(RoutingCatalogView::id)
                .containsExactly("full");
        assertThatThrownBy(() -> adapter.list(QA, "sha256:" + "0".repeat(64)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void theTwinMapsTheCatalogTheActivationReadAndRefusesAnotherKindAsAFault() {
        PacksRoutingCatalogs adapter = new PacksRoutingCatalogs(packs);
        CharterArtifact skill = new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return ArtifactKind.SKILL;
            }

            @Override
            public PackIdentifier packId() {
                return QA;
            }

            @Override
            public String localId() {
                return "land-pr";
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.empty();
            }

            @Override
            public String label() {
                return "land-pr";
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };

        assertThat(adapter.view(full("full"), "the read")).isEqualTo(PacksRoutingCatalogs.viewOf(full("full")));
        assertThatThrownBy(() -> adapter.view(skill, "The read of the routing catalog land-pr"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "The read of the routing catalog land-pr holds skill land-pr, which is no routing catalog.");
    }
}
