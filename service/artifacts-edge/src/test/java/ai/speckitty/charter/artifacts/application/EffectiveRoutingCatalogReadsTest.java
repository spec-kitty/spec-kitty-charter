package ai.speckitty.charter.artifacts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The routing catalog reads under an activation: the item maps the kernel artifact the activation read through the
 * packs port and reads no pack; the list reads each winning pack once at the activation's fingerprint and pairs each
 * winner with its pack's copy, and a winner its pack no longer holds is {@code stale_input}; a {@code 304} reads no
 * pack.
 */
class EffectiveRoutingCatalogReadsTest {

    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final PackIdentifier CORE = new PackIdentifier("core");
    private static final PackIdentifier ORG_BASE = new PackIdentifier("org-base");
    private static final Scope SCOPE = new Scope("a-1", "sha256:rev", "sha256:closure", FINGERPRINT);
    private static final String ETAG = "sha256:" + "e".repeat(64);
    private static final Held COST_LED = new Held("cost-led", ORG_BASE, PackLevel.ORG, "open");
    private static final Held DEFAULT = new Held("default", ORG_BASE, PackLevel.ORG, "open");
    private static final Held TRIAGE = new Held("triage", CORE, PackLevel.BUILT_IN, "open");

    private final List<String> asked = new ArrayList<>();

    private static <T> T found(Outcome<T> outcome) {
        assertThat(outcome).isInstanceOf(Outcome.Found.class);
        return ((Outcome.Found<T>) outcome).value();
    }

    private static RoutingCatalogView view(String id, PackIdentifier pack) {
        return new RoutingCatalogView(
                id,
                pack,
                Optional.of(id + " of " + pack.value()),
                "2026-11-18T00:00:00Z",
                List.of(new RoutingCatalogView.TaskTypeView(
                        "review", "Review", Optional.empty(), Optional.empty(), Optional.empty())),
                List.of(new RoutingCatalogView.ModelView(
                        "model-a",
                        "example",
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new RoutingCatalogView.FitView(
                                "review", new BigDecimal("0.5"), Optional.empty(), Optional.empty())),
                        new RoutingCatalogView.CostView(
                                "low", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                        Optional.empty())),
                new RoutingCatalogView.PolicyView(
                        "balanced",
                        new BigDecimal("1"),
                        new BigDecimal("0.2"),
                        new BigDecimal("0"),
                        new BigDecimal("0"),
                        List.of()),
                List.of(new RoutingCatalogView.SourceView(
                        "example",
                        "https://example.invalid/catalog",
                        "manual",
                        "2026-11-18T00:00:00Z",
                        Optional.empty())));
    }

    /** The kernel artifact the activation holds: org-base's {@code default} catalog. */
    private static CharterArtifact defaultCatalog() {
        return new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return ArtifactKind.ROUTING_CATALOG;
            }

            @Override
            public PackIdentifier packId() {
                return ORG_BASE;
            }

            @Override
            public String localId() {
                return "default";
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.empty();
            }

            @Override
            public String label() {
                return "default";
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };
    }

    private static ActivationArtifacts.Facts defaultFacts() {
        return new ActivationArtifacts.Facts(DEFAULT, "active", false, "selected", List.of(), Optional.empty());
    }

    /** The activations port: it names the given winners of the kind, or answers that the client holds them. */
    private ActivationArtifacts activation(boolean notModified, List<Held> winners) {
        return new ActivationArtifacts() {
            @Override
            public Outcome<Winners> winners(String activationId, ArtifactKind kind, @Nullable String ifNoneMatch) {
                asked.add("winners " + activationId + " of " + kind.token());
                return notModified
                        ? new Outcome.NotModified<>(ETAG)
                        : new Outcome.Found<>(new Winners(SCOPE, winners), ETAG);
            }

            @Override
            public Outcome<Winner> asset(String activationId, String assetId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException("no asset is read here");
            }

            @Override
            public Outcome<Winner> assetPayload(String activationId, String assetId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException("no asset is read here");
            }

            @Override
            public Referrer referrer(String activationId, String kindToken, String artifactId) {
                throw new UnsupportedOperationException("no reference is read here");
            }

            @Override
            public Outcome<SkillWinner> skill(String activationId, String skillId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException("no skill is read here");
            }

            @Override
            public Outcome<GlossaryWinner> glossary(
                    String activationId, String contextId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException("no glossary is read here");
            }

            @Override
            public Outcome<ContextsInForce> contextsInForce(
                    String activationId, ContextsInForce.Vocabulary vocabulary, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException("no vocabulary is read here");
            }

            @Override
            public Outcome<GlossaryWinner> glossaryTerm(
                    String activationId, String contextId, String termId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException("no glossary is read here");
            }
        };
    }

    /** The catalog twin of the activations port: it answers the default catalog, or that the client holds it. */
    private ActivationRoutingCatalogs catalogs(boolean notModified) {
        return (activationId, catalogId, ifNoneMatch) -> {
            asked.add("winner " + catalogId + " under " + activationId);
            return notModified
                    ? new Outcome.NotModified<>(ETAG)
                    : new Outcome.Found<>(
                            new ActivationRoutingCatalogs.CatalogWinner(SCOPE, defaultFacts(), defaultCatalog()), ETAG);
        };
    }

    /**
     * The packs port: core holds triage, org-base holds cost-led and default, each read only against the activation's
     * fingerprint; {@code refusal}, when given, is what each pack read throws.
     */
    private RoutingCatalogReads packs(@Nullable CharterRefusal refusal) {
        return new RoutingCatalogReads() {
            @Override
            public Listed list(PackIdentifier pack) {
                throw new UnsupportedOperationException("the twin reads against the activation's fingerprint");
            }

            @Override
            public Read catalog(PackIdentifier pack, String catalogId) {
                throw new UnsupportedOperationException("the item maps the activation's read and reads no pack");
            }

            @Override
            public Listed list(PackIdentifier pack, String expectedFingerprint) {
                asked.add("list " + pack.value() + " at " + expectedFingerprint);
                if (refusal != null) {
                    throw refusal;
                }
                return new Listed(
                        pack.equals(CORE)
                                ? List.of(EffectiveRoutingCatalogReadsTest.view("triage", pack))
                                : List.of(
                                        EffectiveRoutingCatalogReadsTest.view("cost-led", pack),
                                        EffectiveRoutingCatalogReadsTest.view("default", pack)),
                        "sha256:revision",
                        expectedFingerprint);
            }

            @Override
            public RoutingCatalogView view(CharterArtifact catalog, String read) {
                asked.add("view " + catalog.localId() + " for " + read);
                return EffectiveRoutingCatalogReadsTest.view(catalog.localId(), catalog.packId());
            }
        };
    }

    @Test
    void theItemMapsTheKernelArtifactTheActivationReadThroughThePacksPort() {
        EffectiveRoutingCatalogReads reads =
                new EffectiveRoutingCatalogReads(activation(false, List.of()), catalogs(false), packs(null));

        Outcome<EffectiveRoutingCatalogReads.Read> outcome = reads.catalog("a-1", "default", null);

        EffectiveRoutingCatalogReads.Read read = found(outcome);
        assertThat(((Outcome.Found<EffectiveRoutingCatalogReads.Read>) outcome).etag())
                .isEqualTo(ETAG);
        assertThat(read.scope()).isEqualTo(SCOPE);
        assertThat(read.facts()).isEqualTo(defaultFacts());
        assertThat(read.catalog()).isEqualTo(view("default", ORG_BASE));
        assertThat(asked)
                .containsExactly(
                        "winner default under a-1",
                        "view default for The read of the routing catalog default under activation a-1");
    }

    @Test
    void theListIsEachWinnerWithItsPacksCopyEachPackReadOnceAtTheActivationsFingerprint() {
        EffectiveRoutingCatalogReads reads = new EffectiveRoutingCatalogReads(
                activation(false, List.of(COST_LED, DEFAULT, TRIAGE)), catalogs(false), packs(null));

        Outcome<EffectiveRoutingCatalogReads.Listed> outcome = reads.list("a-1", null);

        EffectiveRoutingCatalogReads.Listed listed = found(outcome);
        assertThat(((Outcome.Found<EffectiveRoutingCatalogReads.Listed>) outcome).etag())
                .isEqualTo(ETAG);
        assertThat(listed.scope()).isEqualTo(SCOPE);
        assertThat(listed.items())
                .extracting(EffectiveRoutingCatalogReads.Item::winner)
                .containsExactly(COST_LED, DEFAULT, TRIAGE);
        assertThat(listed.items())
                .extracting(EffectiveRoutingCatalogReads.Item::catalog)
                .containsExactly(view("cost-led", ORG_BASE), view("default", ORG_BASE), view("triage", CORE));
        assertThat(asked)
                .containsExactly(
                        "winners a-1 of routing_catalog",
                        "list org-base at " + FINGERPRINT,
                        "list core at " + FINGERPRINT);
    }

    @Test
    void aWinnerItsPacksListDoesNotHoldIsStaleInput() {
        Held gone = new Held("gone", CORE, PackLevel.BUILT_IN, "open");
        EffectiveRoutingCatalogReads reads = new EffectiveRoutingCatalogReads(
                activation(false, List.of(TRIAGE, gone)), catalogs(false), packs(null));

        assertThatThrownBy(() -> reads.list("a-1", null)).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT);
            assertThat(refusal).hasMessage(WinnerReads.stale().getMessage());
        });
    }

    @Test
    void aWinnersPackThatIsGoneIsStaleInputAndAnotherRefusalIsTheAnswerUnchanged() {
        for (RefusalCode code : new RefusalCode[] {RefusalCode.PACK_NOT_FOUND, RefusalCode.ARTIFACT_NOT_FOUND}) {
            EffectiveRoutingCatalogReads reads = new EffectiveRoutingCatalogReads(
                    activation(false, List.of(TRIAGE)), catalogs(false), packs(new CharterRefusal(code, "gone since")));

            assertThatThrownBy(() -> reads.list("a-1", null))
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
        }
        EffectiveRoutingCatalogReads unreadable = new EffectiveRoutingCatalogReads(
                activation(false, List.of(TRIAGE)),
                catalogs(false),
                packs(new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack.yaml")));

        assertThatThrownBy(() -> unreadable.list("a-1", null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    void aNotModifiedPassesThroughAndReadsNoPack() {
        EffectiveRoutingCatalogReads reads =
                new EffectiveRoutingCatalogReads(activation(true, List.of(DEFAULT)), catalogs(true), packs(null));

        assertThat(reads.list("a-1", "*"))
                .isEqualTo(new Outcome.NotModified<EffectiveRoutingCatalogReads.Listed>(ETAG));
        assertThat(reads.catalog("a-1", "default", "*"))
                .isEqualTo(new Outcome.NotModified<EffectiveRoutingCatalogReads.Read>(ETAG));
        assertThat(asked).containsExactly("winners a-1 of routing_catalog", "winner default under a-1");
    }
}
