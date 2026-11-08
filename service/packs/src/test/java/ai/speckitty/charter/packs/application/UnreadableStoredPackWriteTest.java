package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.infra.storage.StagedPackViewFactory;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A write into a stored pack that cannot be read whole is refused {@code pack_unreadable}, for every write that is not
 * a whole-pack replace (R7 of the pre-merge squad; DM-01M6V4WMF8CD8NBN7A065E9ZQ5). This is the deliberate asymmetry
 * with the whole-pack registration, which diffs what it can still read from the stored pack, because a replace is how
 * an unreadable pack is repaired ({@code PackRegistrationServiceTest}): a per-artifact write or a removal changes one
 * file of a pack it must understand, so it refuses. Each test runs the same write on a readable pack as the control.
 */
class UnreadableStoredPackWriteTest {

    private static final String BROKEN_ID = PackRegistrationServiceTest.ulid('E');
    private static final String SOUND_ID = PackRegistrationServiceTest.ulid('F');

    /** A directive that still carries a field ADR-0064 D5 removed: the pack that holds it cannot be read whole. */
    private static final String LEGACY_DIRECTIVE =
            Packs.directiveText("LEGACY_ONE", "Legacy") + "references:\n  - directive:X\n";

    private static Packs sound(String id, String name) {
        return Packs.of(id, name, "1.0.0", "org")
                .directive("KEPT_ONE", "Kept")
                .directive("KEPT_TWO", "Kept too")
                .registeredEdge("KEPT_ONE", "KEPT_TWO");
    }

    /** The control pack {@code sound} and the pack {@code broken}: the same files, and one directive with a legacy field. */
    private static Rig rigWithBothPacks(Path host) {
        Rig rig = new Rig(host);
        rig.install("sound", sound(SOUND_ID, "sound"));
        rig.install(
                "broken", sound(BROKEN_ID, "broken").file("directives/LEGACY_ONE.directive.yaml", LEGACY_DIRECTIVE));
        return rig;
    }

    private static void assertUnreadable(Runnable write, Rig rig) {
        Map<String, String> before = rig.filesOf(rig.packs.resolve("broken"));
        assertThatThrownBy(write::run)
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        assertThat(rig.filesOf(rig.packs.resolve("broken")))
                .as("a refused write changes nothing")
                .isEqualTo(before);
    }

    private static Directive directive(String packId, String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(new PackIdentifier(packId))
                .schemaVersion("1.0")
                .title(id)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    @Test
    void anArtifactDeleteIsRefusedPackUnreadable(@TempDir Path host) {
        Rig rig = rigWithBothPacks(host);
        ArtifactRemovalService service = new ArtifactRemovalService(
                rig.repository,
                rig.writeStore,
                new PermissiveWritePolicyGate(),
                new InboundReferences(rig.repository, rig.repository, rig.repository),
                new StagedPackViewFactory(rig.locations, new YamlSerializationStrategy()),
                rig.impact);

        assertUnreadable(() -> service.remove(new PackIdentifier("broken"), "directive", "KEPT_TWO", "*"), rig);

        service.remove(new PackIdentifier("sound"), "directive", "KEPT_ONE", "*");
        assertThat(rig.filesOf(rig.packs.resolve("sound")))
                .as("the control: the same delete on the readable pack lands")
                .doesNotContainKey("directives/KEPT_ONE.directive.yaml");
    }

    @Test
    void aRelationshipDeleteIsRefusedPackUnreadable(@TempDir Path host) {
        Rig rig = rigWithBothPacks(host);
        RelationshipRemovalService service = new RelationshipRemovalService(
                rig.repository, rig.writeStore, new PermissiveWritePolicyGate(), rig.impact);
        PackQualifiedReference from = PackQualifiedReference.parse("directive:KEPT_ONE");
        PackQualifiedReference to = PackQualifiedReference.parse("directive:KEPT_TWO");

        assertUnreadable(() -> service.remove(new PackIdentifier("broken"), from, to, "*"), rig);

        service.remove(new PackIdentifier("sound"), from, to, "*");
        assertThat(rig.writeStore.registeredRelationships(new PackIdentifier("sound")))
                .as("the control: the same delete on the readable pack lands")
                .isEmpty();
    }

    @Test
    void aPutOfAnArtifactIsRefusedPackUnreadable(@TempDir Path host) {
        Rig rig = rigWithBothPacks(host);
        DirectiveRegistrationService service =
                new DirectiveRegistrationService(rig.repository, rig.writeStore, new PermissiveWritePolicyGate());

        assertUnreadable(
                () -> service.registerDirective(
                        new PackIdentifier("broken"), directive("broken", "KEPT_ONE"), true, null),
                rig);

        assertThat(service.registerDirective(new PackIdentifier("sound"), directive("sound", "KEPT_ONE"), true, null)
                        .created())
                .as("the control: the same PUT on the readable pack replaces the directive")
                .isFalse();
    }

    @Test
    void aPostOfAnArtifactIsRefusedPackUnreadable(@TempDir Path host) {
        Rig rig = rigWithBothPacks(host);
        DirectiveRegistrationService service =
                new DirectiveRegistrationService(rig.repository, rig.writeStore, new PermissiveWritePolicyGate());

        assertUnreadable(
                () -> service.registerDirective(
                        new PackIdentifier("broken"), directive("broken", "NEW_ONE"), false, null),
                rig);

        assertThat(service.registerDirective(new PackIdentifier("sound"), directive("sound", "NEW_ONE"), false, null)
                        .created())
                .as("the control: the same POST on the readable pack creates the directive")
                .isTrue();
    }

    @Test
    void aPackRelationshipPutIsRefusedPackUnreadable(@TempDir Path host) {
        Rig rig = rigWithBothPacks(host);
        RelationshipRegistrationService service = new RelationshipRegistrationService(
                rig.repository, rig.writeStore, new PermissiveWritePolicyGate(), (source, target) -> {});
        Relationship edge = Relationship.of(
                new Urn(NodeKind.DIRECTIVE, "KEPT_TWO"),
                new Urn(NodeKind.DIRECTIVE, "KEPT_ONE"),
                RelationType.SUGGESTS);

        assertUnreadable(() -> service.registerRelationship(new PackIdentifier("broken"), edge, "*"), rig);

        service.registerRelationship(new PackIdentifier("sound"), edge, "*");
        assertThat(rig.writeStore.registeredRelationships(new PackIdentifier("sound")))
                .as("the control: the same PUT on the readable pack lands")
                .singleElement()
                .satisfies(registered -> assertThat(registered.type()).isEqualTo(RelationType.SUGGESTS));
    }
}
