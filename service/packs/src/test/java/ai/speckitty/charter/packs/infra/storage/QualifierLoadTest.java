package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.packs.application.ActivationImpact;
import ai.speckitty.charter.packs.application.ClosureImpactCheck;
import ai.speckitty.charter.packs.application.InboundReferences;
import ai.speckitty.charter.packs.application.PackRegistrationService.Mode;
import ai.speckitty.charter.packs.application.PackUninstallService;
import ai.speckitty.charter.packs.application.RevisionTrail;
import ai.speckitty.charter.packs.domain.PackEdge;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A graph edge whose pack qualifier is no pack id is refused when the pack is read, on every read path (#582, FR-011).
 * The pack schemas write the qualifier as the slug and cannot cap it at {@value PackIdentifier#MAX_LENGTH} characters, so
 * a 64-character qualifier passes the schema: the strict whole-pack load and the stored pack's edge read refuse it as
 * the registration's staging does. Each test pairs the refused qualifier with one at the cap, on the same pack.
 */
@DisplayName("A qualifier that is no pack id is refused at load (#582)")
class QualifierLoadTest {

    private static final String SHARD = "tactic.graph.yaml";
    private static final String DESCRIPTOR_ONLY = "pack-descriptor-only";
    private static final String AT_CAP = "p".repeat(PackIdentifier.MAX_LENGTH);
    private static final String OVER_CAP = AT_CAP + "p";

    private final WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

    @Test
    @DisplayName("the strict load refuses a 64-character qualifier as drg_schema_error, and holds one of 63")
    void theStrictLoadRefusesAQualifierOverTheCap(@TempDir Path host) throws IOException {
        Path refused = WholePackStoreTest.richPack(host.resolve("over"));
        WholePackStoreTest.write(refused, SHARD, shard(OVER_CAP));

        assertThatThrownBy(() -> store.load(refused)).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR);
            assertThat(refusal.getMessage()).contains(SHARD).contains(PackIdentifier.GRAMMAR_DETAIL);
        });

        Path held = WholePackStoreTest.richPack(host.resolve("at"));
        WholePackStoreTest.write(held, SHARD, shard(AT_CAP));

        assertThat(store.load(held).graphs())
                .anySatisfy(document -> assertThat(document.edges())
                        .anySatisfy(edge -> assertThat(edge.target()).startsWith(AT_CAP + "::")));
    }

    @Test
    @DisplayName("a stored pack whose edge has a 64-character qualifier is unreadable; one of 63 is read")
    void theStoredEdgeReadRefusesAQualifierOverTheCap(@TempDir Path host) throws IOException {
        Path refused = PackFixtures.copyOf(DESCRIPTOR_ONLY, host.resolve("over"));
        WholePackStoreTest.write(refused, SHARD, shard(OVER_CAP));

        assertThatThrownBy(() -> over(refused).edges(new PackIdentifier(DESCRIPTOR_ONLY)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(refusal.getMessage()).contains(SHARD).contains(PackIdentifier.GRAMMAR_DETAIL);
                    assertThat(refusal.violations())
                            .as("the edge read lists the violation a registration lists (#617)")
                            .extracting(RefusalViolation::pointer, RefusalViolation::code)
                            .containsExactly(tuple(SHARD + "#/edges/0", "drg_schema_error"));
                });

        Path held = PackFixtures.copyOf(DESCRIPTOR_ONLY, host.resolve("at"));
        WholePackStoreTest.write(held, SHARD, shard(AT_CAP));

        assertThat(over(held).edges(new PackIdentifier(DESCRIPTOR_ONLY)))
                .containsExactly(new PackEdge(
                        "tactic:gamma-tactic", AT_CAP + "::directive:DIRECTIVE_043", RelationType.REQUIRES));
    }

    @Test
    @DisplayName("a pack stored with a qualifier no pack id names, as before #582, is still uninstalled whole")
    void aPackStoredBeforeTheGrammarIsRecoveredByUninstallingIt(@TempDir Path host) throws IOException {
        Path stored = PackFixtures.copyOf(DESCRIPTOR_ONLY, host);
        WholePackStoreTest.write(stored, SHARD, shard("a_b"));
        PackIdentifier pack = new PackIdentifier(DESCRIPTOR_ONLY);
        StorageRootLocations locations = new StorageRootLocations(host);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        PackCommitStore commits = new PackCommitStore(locations, strategy);
        CommitStorePackWriteStore writes = new CommitStorePackWriteStore(locations, strategy, commits);
        DomainRepositoryService repository = new DomainRepositoryService(locations, strategy);
        StagedPackViewFactory views = new StagedPackViewFactory(locations, strategy);
        PackUninstallService uninstall = new PackUninstallService(
                writes,
                commits,
                store,
                views,
                new ClosureImpactCheck(
                        views,
                        ActivationImpact.NONE,
                        new InboundReferences(repository, repository, repository)));

        assertThatThrownBy(() -> writes.authored(pack))
                .as("the single-relationship and single-artifact removals read this, and refuse the pack")
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));

        PackUninstallService.Outcome outcome = uninstall.uninstall(pack, "*", Mode.COMMIT, RevisionTrail.NONE);

        assertThat(outcome.refused()).isEmpty();
        assertThat(stored).doesNotExist();
    }

    private static String shard(String qualifier) {
        return """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: 'tactic:gamma-tactic', target: '%s::directive:DIRECTIVE_043', relation: requires}
                """.formatted(qualifier);
    }

    private static DomainRepositoryService over(Path directory) {
        PackLocation location = new PackLocation(DESCRIPTOR_ONLY, PackLevel.ORG, directory);
        return new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy());
    }
}
