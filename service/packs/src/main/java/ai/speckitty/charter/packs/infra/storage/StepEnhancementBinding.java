package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PromptFragment;
import ai.speckitty.charter.packs.domain.StepEnhancement;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The {@link KindBinding} of the step enhancement (ADR-0066): {@link StepEnhancementDso} to {@link StepEnhancement}
 * and back. An enhancement lives at {@code missions/<type>/enhancements/<id>.step-enhancement.yaml}, with one
 * Markdown companion per {@code prompt_fragments} entry beside it. The type directory is not derivable from the id,
 * so the binding cannot place a new file: {@link #fileName} refuses, and a whole-pack write puts the file back where
 * it was read.
 */
enum StepEnhancementBinding implements KindBinding<StepEnhancementDso, StepEnhancement> {
    /** The binding. */
    INSTANCE;

    /** The structure schema of a fragment companion. */
    static final String FRAGMENT_SCHEMA = "prompt-fragment";

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.STEP_ENHANCEMENT;
    }

    @Override
    public Class<StepEnhancement> entityType() {
        return StepEnhancement.class;
    }

    @Override
    public Class<StepEnhancementDso> dsoType() {
        return StepEnhancementDso.class;
    }

    @Override
    public String idOf(StepEnhancementDso dso) {
        return dso.id();
    }

    @Override
    public String fileToken() {
        return "step-enhancement";
    }

    @Override
    public String schemaName() {
        return "step-enhancement";
    }

    /**
     * {@inheritDoc}
     *
     * <p>A step enhancement is never placed by its id: the type directory of {@code missions/<type>/enhancements/}
     * is the target step's, which only the pack's {@code enhances} edge names. The default would put the file at the
     * pack root, a path no files rule classifies, so this refuses.
     *
     * @throws IllegalArgumentException always
     */
    @Override
    public String fileName(String elementId) {
        throw new IllegalArgumentException("a step-enhancement is not placed by its id ('" + elementId
                + "'): its file sits at missions/<type>/enhancements/" + elementId
                + ".step-enhancement.yaml, a place only the pack that holds it knows");
    }

    @Override
    public boolean idIsFileStem() {
        return true;
    }

    @Override
    public boolean hasCompanions() {
        return true;
    }

    /** One Markdown companion per {@code prompt_fragments} entry, in entry order, at the entry's path. */
    @Override
    public List<Companion> companions(StepEnhancementDso dso) {
        return dso.promptFragments().stream()
                .map(entry -> new Companion(
                        entry.path(),
                        entry.body().map(CompanionContent.Markdown::new),
                        ContentForm.MARKDOWN,
                        Optional.of(FRAGMENT_SCHEMA)))
                .toList();
    }

    @Override
    public Set<String> companionStructures() {
        return Set.of(FRAGMENT_SCHEMA);
    }

    /** Each entry's body is the companion the store read at the entry's path; an entry whose companion is absent keeps none. */
    @Override
    public StepEnhancementDso withCompanions(StepEnhancementDso dso, Map<String, CompanionContent> contents) {
        return new StepEnhancementDso(
                dso.id(),
                dso.schemaVersion(),
                dso.promptFragments().stream()
                        .map(entry -> new StepEnhancementDso.Entry(
                                entry.slot(),
                                entry.path(),
                                Optional.ofNullable(contents.get(entry.path()))
                                        .map(StepEnhancementBinding::markdownOf)))
                        .toList());
    }

    private static MarkdownDocument markdownOf(CompanionContent content) {
        return switch (content) {
            case CompanionContent.Markdown markdown -> markdown.document();
            case CompanionContent.Text text ->
                throw new IllegalStateException("a prompt fragment was read as text, not markdown");
        };
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException when the shape holds no body for a fragment it names
     */
    @Override
    public StepEnhancement toEntity(PackIdentifier pack, StepEnhancementDso dso) {
        return new StepEnhancement(
                dso.id(),
                pack,
                dso.schemaVersion(),
                dso.promptFragments().stream()
                        .map(entry -> new StepEnhancement.Entry(
                                entry.slot(),
                                new PromptFragment(
                                        entry.path(),
                                        entry.body()
                                                .orElseThrow(() -> new IllegalArgumentException("step enhancement '"
                                                        + dso.id() + "' holds no body for " + entry.path())))))
                        .toList());
    }

    @Override
    public StepEnhancementDso toDso(StepEnhancement enhancement) {
        return new StepEnhancementDso(
                enhancement.localId(),
                enhancement.schemaVersion().orElseThrow(),
                enhancement.promptFragments().stream()
                        .map(entry -> new StepEnhancementDso.Entry(
                                entry.slot(),
                                entry.path(),
                                Optional.of(entry.fragment().body())))
                        .toList());
    }
}
