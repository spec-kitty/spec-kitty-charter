package ai.speckitty.charter.artifacts.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.artifacts.application.SkillReads;
import ai.speckitty.charter.artifacts.application.SkillView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.domain.Skill;
import ai.speckitty.charter.packs.domain.SkillExpansion;
import ai.speckitty.charter.packs.domain.SkillForm;
import ai.speckitty.charter.packs.domain.SkillIdentifier;
import ai.speckitty.charter.packs.domain.SkillInvocation;
import ai.speckitty.charter.packs.domain.SkillParameter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The packs adapter maps the packs slice's skill into the edge's view, every field but the body path and the edges,
 * the body unchanged, and passes the revision and the verified fingerprint through; the list is in id order.
 */
class PacksSkillsTest {

    private static final PackIdentifier QA = new PackIdentifier("qa");
    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final PackRevision REVISION = PackRevision.compute(List.of("qa"));
    private static final String BODY = "Land it — über.\n";

    private static Skill prompt(String id) {
        return Skill.builder()
                .id(new SkillIdentifier(id))
                .packId(QA)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .description("When " + id + " applies.")
                .triggers(List.of("go"))
                .form(SkillForm.PROMPT)
                .bodyPath(Optional.of(id + ".skill.md"))
                .body(Optional.of(BODY))
                .parameters(
                        List.of(new SkillParameter("pr", Optional.of(true), Optional.of("The PR."), Optional.of("1"))))
                .invocation(Optional.of(new SkillInvocation(Optional.of(true), Optional.empty(), List.of("gh-write"))))
                .tools(List.of("Bash"))
                .version(Optional.of("1.0.0"))
                .maintainers(List.of("@core"))
                .overrides(Optional.of("other"))
                .build();
    }

    private static Skill wrapper(String id) {
        return Skill.builder()
                .id(new SkillIdentifier(id))
                .packId(QA)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .description("When " + id + " applies.")
                .form(SkillForm.WRAPPER)
                .expandsTo(Optional.of(new SkillExpansion("builtin:status", Optional.of("--all"))))
                .enhances(Optional.of("land-pr"))
                .build();
    }

    private final ForeignArtifactRead packs = mock(ForeignArtifactRead.class);

    @Test
    void theListIsInIdOrder() {
        when(packs.held(QA, ArtifactKind.SKILL))
                .thenReturn(new ConsistentRead.Verified<>(
                        new ForeignArtifactRead.Held(List.of(wrapper("zeta"), prompt("alpha")), REVISION),
                        FINGERPRINT));

        SkillReads.Listed listed = new PacksSkills(packs).list(QA);

        assertThat(listed.skills()).extracting(SkillView::id).containsExactly("alpha", "zeta");
        assertThat(listed.skills()).extracting(SkillView::form).containsExactly("prompt", "wrapper");
        assertThat(listed.revision()).isEqualTo(REVISION.token());
        assertThat(listed.sourceFingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void thePromptSkillMapsEveryFieldAndItsBodyUnchanged() {
        when(packs.read(QA, ArtifactKind.SKILL, "land-pr", 0))
                .thenReturn(new ConsistentRead.Verified<>(
                        new ForeignArtifactRead.Foreign(prompt("land-pr"), REVISION, Optional.empty(), List.of()),
                        FINGERPRINT));

        SkillReads.Read read = new PacksSkills(packs).skill(QA, "land-pr");

        assertThat(read.skill())
                .isEqualTo(new SkillView(
                        "land-pr",
                        QA,
                        "1.0",
                        "Title of land-pr",
                        "When land-pr applies.",
                        "prompt",
                        List.of("go"),
                        List.of(new SkillView.Parameter(
                                "pr", Optional.of(true), Optional.of("The PR."), Optional.of("1"))),
                        Optional.of(new SkillView.Invocation(Optional.of(true), Optional.empty(), List.of("gh-write"))),
                        Optional.empty(),
                        List.of("Bash"),
                        Optional.of("1.0.0"),
                        List.of("@core"),
                        Optional.of(BODY)));
        assertThat(read.revision()).isEqualTo(REVISION.token());
        assertThat(read.sourceFingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void theWrapperSkillHasItsExpansionAndNoBody() {
        when(packs.read(QA, ArtifactKind.SKILL, "status", 0))
                .thenReturn(new ConsistentRead.Verified<>(
                        new ForeignArtifactRead.Foreign(wrapper("status"), REVISION, Optional.empty(), List.of()),
                        FINGERPRINT));

        SkillView skill = new PacksSkills(packs).skill(QA, "status").skill();

        assertThat(skill.body()).isEmpty();
        assertThat(skill.expandsTo()).contains(new SkillView.Expansion("builtin:status", Optional.of("--all")));
        assertThat(skill.invocation()).isEmpty();
        assertThat(skill.version()).isEmpty();
        assertThat(skill.maintainers()).isEmpty();
    }

    @Test
    void theTwinMapsTheSkillTheActivationReadAndRefusesAnotherKindAsAFault() {
        PacksSkills adapter = new PacksSkills(packs);

        assertThat(adapter.view(prompt("land-pr"), "the read").body())
                .isEqualTo(prompt("land-pr").body());
        assertThat(adapter.view(prompt("land-pr"), "the read")).isEqualTo(PacksSkills.viewOf(prompt("land-pr")));
        assertThatThrownBy(() -> adapter.view(
                        ai.speckitty.charter.packs.domain.Asset.builder()
                                .id(new ai.speckitty.charter.packs.domain.AssetIdentifier("logo"))
                                .packId(QA)
                                .mime("image/png")
                                .path("logo.png")
                                .build(),
                        "The read of the skill logo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The read of the skill logo holds asset logo");
    }

    @Test
    void theTwinsListReadAtAnotherFingerprintIsStaleInput() {
        when(packs.held(QA, ArtifactKind.SKILL))
                .thenReturn(new ConsistentRead.Verified<>(
                        new ForeignArtifactRead.Held(List.of(prompt("land-pr")), REVISION), FINGERPRINT));
        PacksSkills adapter = new PacksSkills(packs);

        assertThat(adapter.list(QA, FINGERPRINT).skills())
                .extracting(SkillView::id)
                .containsExactly("land-pr");
        assertThatThrownBy(() -> adapter.list(QA, "sha256:" + "0".repeat(64)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }
}
