package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The per-artifact writes run the registration impact check (IC-15; operator ruling DM-01M6VG6GC40AKYYFRR35SV8HVW,
 * FR-025): a {@code PUT} or {@code POST} of one artifact, and a relationship write, is judged over every closure the
 * pack joins as it will be after the write, under the registry lock, as a registration of the whole pack is. Each
 * refusal is paired with a control on the same fixture and is shown to change nothing on disk.
 */
class PerArtifactImpactTest {

    private static final String MID_ID = PackRegistrationServiceTest.ulid('Q');

    /** The registration services over the rig's real stores and the gate the deployment wires. */
    private static final class Writes {
        final DirectiveRegistrationService directives;
        final RelationshipRegistrationService relationships;
        final Rig rig;

        Writes(Rig rig) {
            this.rig = rig;
            ConsistentRead read = new ConsistentRead(new FileSystemPackSources(rig.locations::all), rig.generation);
            PackMutabilityPolicy gate = new PackMutabilityPolicy(
                    new OfferingReadService(rig.repository, rig.repository, rig.repository, read),
                    rig.repository,
                    rig.repository,
                    read);
            this.directives = new DirectiveRegistrationService(rig.repository, rig.writeStore, gate);
            this.relationships = new RelationshipRegistrationService(
                    rig.repository, rig.writeStore, gate, (source, target) -> {});
        }

        String revisionOf(String pack) {
            return rig.writeStore.currentRevision(new PackIdentifier(pack)).token();
        }
    }

    private static Directive directive(String pack, String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(new PackIdentifier(pack))
                .schemaVersion("1.0")
                .title(id)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    private static Urn directiveUrn(String id) {
        return Urn.parse("directive:" + id).orElseThrow();
    }

    /** The org pack {@code mid}, built on the org pack {@code base}. */
    private static Packs mid() {
        return Packs.of(MID_ID, "mid", "1.0.0", "org", dependsOnBase()).directive("MID_ONE", "Own");
    }

    private static String dependsOnBase() {
        return "depends_on:\n  - pack_id: " + RegistrationImpactTest.BASE_ID + "\n    range: '>=1.0'\n";
    }

    @Test
    void aPutOfAnIdThatAPackBelowHoldsAtTheSameLevelIsRefusedAndAnIdNobodyHoldsIsNot(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(RegistrationImpactTest.base("1.0.0")), Preconditions.none());
        rig.register("mid", rig.stage(mid()), Preconditions.none());
        Writes writes = new Writes(rig);
        PackIdentifier mid = new PackIdentifier("mid");
        Map<String, String> before = rig.filesOf(rig.packs.resolve("mid"));

        CharterRefusal refused = refusal(() -> writes.directives.registerDirective(
                mid, directive("mid", "OPEN_ONE"), false, null));

        assertThat(refused.code())
                .as("base and mid are both org packs, so two packs of one level would define OPEN_ONE")
                .isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);
        assertThat(refused.packs())
                .as("the packs involved, so the caller knows whom to talk to")
                .extracting(RefusalPack::packId)
                .contains(RegistrationImpactTest.BASE_ID, MID_ID);
        assertThat(rig.filesOf(rig.packs.resolve("mid"))).as("nothing changed").isEqualTo(before);

        writes.directives.registerDirective(mid, directive("mid", "MID_TWO"), false, null);
        assertThat(rig.filesOf(rig.packs.resolve("mid")))
                .as("the control: an id no other pack holds is written")
                .containsKey("MID_TWO.directive.yaml")
                .doesNotContainKey("OPEN_ONE.directive.yaml");
    }

    @Test
    void anOverridesEdgeOntoANonOpenTargetThroughTheRelationshipWriteIsRefusedAndAnOpenOneIsNot(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(RegistrationImpactTest.basePolicy("1.0.0")), Preconditions.none());
        rig.register("derived", rig.stage(RegistrationImpactTest.derived("1.0.0")), Preconditions.none());
        Writes writes = new Writes(rig);
        PackIdentifier derived = new PackIdentifier("derived");
        Map<String, String> before = rig.filesOf(rig.packs.resolve("derived"));

        CharterRefusal refused = refusal(() -> writes.relationships.registerRelationship(
                derived, overrides("LOCKED_ONE"), writes.revisionOf("derived")));

        assertThat(refused.code()).isEqualTo(RefusalCode.OVERRIDE_NOT_PERMITTED);
        assertThat(refused.packs())
                .as("the pack that breaks the rule first, then the pack that holds the level")
                .extracting(RefusalPack::packId)
                .startsWith(RegistrationImpactTest.DERIVED_ID)
                .contains(RegistrationImpactTest.BASE_ID);
        assertThat(rig.filesOf(rig.packs.resolve("derived")))
                .as("nothing changed: no registered shard was written")
                .isEqualTo(before)
                .doesNotContainKey("registered.graph.yaml");

        writes.relationships.registerRelationship(derived, overrides("OPEN_ONE"), writes.revisionOf("derived"));
        assertThat(rig.filesOf(rig.packs.resolve("derived")))
                .as("the control: the target is open")
                .containsKey("registered.graph.yaml");
    }

    private static Relationship overrides(String target) {
        return new Relationship(
                directiveUrn("DERIVED_ONE"),
                Optional.empty(),
                directiveUrn(target),
                Optional.of("base"),
                RelationType.OVERRIDES,
                Optional.empty(),
                Optional.empty());
    }

    @Test
    void aWriteThatLeavesEveryClosureComposingStillSucceeds(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(RegistrationImpactTest.base("1.0.0")), Preconditions.none());
        rig.register("derived", rig.stage(RegistrationImpactTest.derived("1.0.0")), Preconditions.none());
        Writes writes = new Writes(rig);
        PackIdentifier derived = new PackIdentifier("derived");

        writes.directives.registerDirective(derived, directive("derived", "DERIVED_TWO"), false, null);
        writes.relationships.registerRelationship(
                derived,
                new Relationship(
                        directiveUrn("DERIVED_ONE"),
                        directiveUrn("DERIVED_TWO"),
                        RelationType.SUGGESTS,
                        Optional.empty(),
                        Optional.empty()),
                writes.revisionOf("derived"));

        assertThat(rig.filesOf(rig.packs.resolve("derived")))
                .containsKey("DERIVED_TWO.directive.yaml")
                .containsKey("registered.graph.yaml");
    }
}
