package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.domain.ArtifactClass;
import ai.speckitty.charter.missions.domain.ExpectedArtifact;
import ai.speckitty.charter.missions.domain.ExpectedInput;
import ai.speckitty.charter.missions.domain.MissionReference;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.missions.domain.MissionTypeIdentifier;
import ai.speckitty.charter.missions.domain.PathConventions;
import ai.speckitty.charter.missions.domain.StepDocument;
import ai.speckitty.charter.missions.domain.StepSection;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The {@link KindBinding} of the mission type (ADR-0044): {@link MissionTypeDso} to {@link MissionType} and back. A
 * mission type lives at {@code missions/<id>/<id>.mission-type.yaml}; its companions are each step's prompt and
 * guidelines, {@code steps/<step>.prompt.md} and {@code steps/<step>.guidelines.md} beside it, Markdown validated by the
 * {@value #PROMPT_SCHEMA} and {@value #GUIDELINES_SCHEMA} structure schemas. Their paths are derived from the step id
 * (S8): the writer writes the derived path, and the load refuses any other.
 */
enum MissionTypeBinding implements KindBinding<MissionTypeDso, MissionType> {
    /** The binding. */
    INSTANCE;

    /** The structure schema of a step's prompt, as {@code file-kinds.yaml} and {@link PackSchemas} name it. */
    static final String PROMPT_SCHEMA = "mission-step-prompt";

    /** The structure schema of a step's guidelines. */
    static final String GUIDELINES_SCHEMA = "mission-step-guidelines";

    /** The one key a prompt's frontmatter holds: its detailed description (ruling Q1 as refined on 2026-10-30). */
    private static final String DETAILED_DESCRIPTION = "description";

    private static final String TOKEN = "mission-type";

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.MISSION_TYPE;
    }

    @Override
    public Class<MissionType> entityType() {
        return MissionType.class;
    }

    @Override
    public Class<MissionTypeDso> dsoType() {
        return MissionTypeDso.class;
    }

    @Override
    public String fileToken() {
        return TOKEN;
    }

    @Override
    public String schemaName() {
        return TOKEN;
    }

    @Override
    public String idOf(MissionTypeDso dso) {
        return dso.id();
    }

    /** A mission type's place: {@code missions/<id>/<id>.mission-type.yaml}. */
    @Override
    public String fileName(String elementId) {
        return "missions/" + elementId + "/" + elementId + "." + TOKEN + ".yaml";
    }

    @Override
    public boolean idIsFileStem() {
        return true;
    }

    @Override
    public boolean placedAtItsFileName() {
        return true;
    }

    @Override
    public boolean hasCompanions() {
        return true;
    }

    /** Each step's prompt then guidelines, sequenced steps first, as the shape names them. */
    @Override
    public List<Companion> companions(MissionTypeDso dso) {
        List<Companion> companions = new ArrayList<>();
        for (MissionTypeDso.Step step : allSteps(dso)) {
            step.promptPath().ifPresent(path -> companions.add(markdown(path, step.prompt(), PROMPT_SCHEMA)));
            step.guidelinesPath()
                    .ifPresent(path -> companions.add(markdown(path, step.guidelines(), GUIDELINES_SCHEMA)));
        }
        return companions;
    }

    /** Each output template a step or the type names, in the order the shape names them (ADR-0044 decision 3). */
    @Override
    public List<NamedFile> namedFiles(MissionTypeDso dso) {
        List<NamedFile> templates = new ArrayList<>();
        dso.expectedOutputArtifacts()
                .forEach(a -> a.templatePath().ifPresent(path -> templates.add(new NamedFile(path, "the type"))));
        for (MissionTypeDso.Step step : allSteps(dso)) {
            String namedBy = "step '" + step.id() + "'";
            step.expectedOutputArtifacts()
                    .forEach(a -> a.templatePath().ifPresent(path -> templates.add(new NamedFile(path, namedBy))));
        }
        return templates;
    }

    @Override
    public Set<String> companionStructures() {
        return Set.of(PROMPT_SCHEMA, GUIDELINES_SCHEMA);
    }

    /**
     * A step's guidelines hold no frontmatter; a prompt's frontmatter holds only its detailed description, which its
     * structure schema checks (ruling Q1 as refined on 2026-10-30).
     */
    @Override
    public void checkCompanion(String structureSchema, MarkdownDocument document) {
        if (structureSchema.equals(GUIDELINES_SCHEMA) && document.frontmatter().isPresent()) {
            throw new IllegalArgumentException("a mission step's " + structureSchema + " holds no frontmatter");
        }
    }

    @Override
    public MissionTypeDso withCompanions(MissionTypeDso dso, Map<String, CompanionContent> contents) {
        Function<MissionTypeDso.Step, MissionTypeDso.Step> fill = step -> step.withDocuments(
                step.promptPath().map(contents::get).map(MissionTypeBinding::markdownOf),
                step.guidelinesPath().map(contents::get).map(MissionTypeBinding::markdownOf));
        return dso.withSteps(
                dso.steps().stream().map(fill).toList(),
                dso.onDemandSteps().stream().map(fill).toList());
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException also when a step's companion path is not the one derived from its id, or the
     *     shape holds no document for a path it names
     */
    @Override
    public MissionType toEntity(PackIdentifier pack, MissionTypeDso dso) {
        MissionType.Builder builder = MissionType.builder()
                .id(new MissionTypeIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .title(dso.title())
                .description(dso.description())
                .content(dso.content().orElse(null))
                .requiresSubstantiveSpec(dso.requiresSubstantiveSpec())
                .pathConventions(conventionsOf(dso.pathConventions()));
        dso.expectedOutputArtifacts().forEach(a -> builder.expectedOutputArtifact(output(a)));
        dso.references().forEach(r -> builder.reference(reference(r)));
        dso.steps().forEach(s -> builder.step(step(dso.id(), s)));
        dso.onDemandSteps().forEach(s -> builder.onDemandStep(step(dso.id(), s)));
        return builder.build();
    }

    @Override
    public MissionTypeDso toDso(MissionType type) {
        Map<String, String> conventions = new LinkedHashMap<>();
        type.pathConventions().ifPresent(c -> {
            c.workspace().ifPresent(v -> conventions.put("workspace", v));
            c.tests().ifPresent(v -> conventions.put("tests", v));
            c.deliverables().ifPresent(v -> conventions.put("deliverables", v));
            c.documentation().ifPresent(v -> conventions.put("documentation", v));
            c.data().ifPresent(v -> conventions.put("data", v));
        });
        return new MissionTypeDso(
                type.localId(),
                type.schemaVersion().orElseThrow(),
                type.title(),
                type.description().orElseThrow(),
                type.content(),
                type.requiresSubstantiveSpec(),
                conventions,
                type.expectedOutputArtifacts().stream()
                        .map(MissionTypeBinding::artifactDso)
                        .toList(),
                type.references().stream().map(MissionTypeBinding::referenceDso).toList(),
                type.steps().stream().map(MissionTypeBinding::stepDso).toList(),
                type.onDemandSteps().stream().map(MissionTypeBinding::stepDso).toList());
    }

    /**
     * The derived place of a step's prompt (S8).
     *
     * @param stepId the step's id
     * @return {@code steps/<id>.prompt.md}
     */
    static String promptPathOf(String stepId) {
        return "steps/" + stepId + ".prompt.md";
    }

    /**
     * The derived place of a step's guidelines (S8).
     *
     * @param stepId the step's id
     * @return {@code steps/<id>.guidelines.md}
     */
    static String guidelinesPathOf(String stepId) {
        return "steps/" + stepId + ".guidelines.md";
    }

    private static List<MissionTypeDso.Step> allSteps(MissionTypeDso dso) {
        List<MissionTypeDso.Step> all = new ArrayList<>(dso.steps());
        all.addAll(dso.onDemandSteps());
        return all;
    }

    private static Companion markdown(String path, Optional<MarkdownDocument> document, String schema) {
        return new Companion(
                path, document.map(CompanionContent.Markdown::new), ContentForm.MARKDOWN, Optional.of(schema));
    }

    private static MarkdownDocument markdownOf(CompanionContent content) {
        return switch (content) {
            case CompanionContent.Markdown markdown -> markdown.document();
            case CompanionContent.Text text ->
                throw new IllegalStateException("a mission step document was read as text, not markdown");
        };
    }

    private static MissionStep step(String typeId, MissionTypeDso.Step step) {
        return new MissionStep(
                step.id(),
                step.title(),
                step.description(),
                step.role(),
                document(typeId, step, step.promptPath(), promptPathOf(step.id()), step.prompt(), true),
                document(typeId, step, step.guidelinesPath(), guidelinesPathOf(step.id()), step.guidelines(), false),
                step.expectedInputArtifacts().stream()
                        .map(MissionTypeBinding::input)
                        .toList(),
                step.expectedOutputArtifacts().stream()
                        .map(MissionTypeBinding::output)
                        .toList(),
                step.references().stream().map(MissionTypeBinding::reference).toList());
    }

    private static Optional<StepDocument> document(
            String typeId,
            MissionTypeDso.Step step,
            Optional<String> path,
            String derived,
            Optional<MarkdownDocument> document,
            boolean prompt) {
        if (path.isEmpty()) {
            return Optional.empty();
        }
        String where = "mission type '" + typeId + "' step '" + step.id() + "': ";
        if (!path.get().equals(derived)) {
            throw new IllegalArgumentException(where + "names " + path.get() + ", not its derived path " + derived);
        }
        MarkdownDocument held =
                document.orElseThrow(() -> new IllegalArgumentException(where + "holds no document " + derived));
        Optional<String> detailed = Optional.empty();
        if (held.frontmatter().isPresent()) {
            detailed = detailedDescription(where + derived, held.frontmatter().get(), prompt);
        }
        return Optional.of(new StepDocument(
                held.title(),
                detailed,
                held.preamble(),
                held.sections().stream()
                        .map(section -> new StepSection(section.title(), section.content()))
                        .toList()));
    }

    /** A prompt's frontmatter holds its detailed description and nothing else; guidelines hold none. */
    private static Optional<String> detailedDescription(String where, Map<String, Object> frontmatter, boolean prompt) {
        if (!prompt) {
            throw new IllegalArgumentException(where + " holds frontmatter");
        }
        if (!frontmatter.keySet().equals(Set.of(DETAILED_DESCRIPTION))
                || !(frontmatter.get(DETAILED_DESCRIPTION) instanceof String text)) {
            throw new IllegalArgumentException(where + ": its frontmatter holds only a description text");
        }
        return Optional.of(text);
    }

    private static ExpectedInput input(MissionTypeDso.Artifact artifact) {
        return new ExpectedInput(artifact.artifactKey(), artifact.pathPattern(), artifact.blocking());
    }

    private static ExpectedArtifact output(MissionTypeDso.Artifact artifact) {
        String token = artifact.artifactClass()
                .orElseThrow(() -> new IllegalArgumentException(
                        "expected output '" + artifact.artifactKey() + "' names no artifact class"));
        ArtifactClass artifactClass = ArtifactClass.fromToken(token)
                .orElseThrow(() -> new IllegalArgumentException("unknown artifact class '" + token + "'"));
        return new ExpectedArtifact(
                artifact.artifactKey(),
                artifactClass,
                artifact.pathPattern(),
                artifact.blocking(),
                artifact.templatePath());
    }

    private static MissionReference reference(MissionTypeDso.Reference reference) {
        ArtifactKind type = ArtifactKind.fromToken(reference.type())
                .orElseThrow(() -> new IllegalArgumentException("unknown reference type '" + reference.type() + "'"));
        return new MissionReference(type, reference.id(), reference.when(), reference.reason());
    }

    private static @Nullable PathConventions conventionsOf(Map<String, String> written) {
        if (written.isEmpty()) {
            return null;
        }
        return new PathConventions(
                Optional.ofNullable(written.get("workspace")),
                Optional.ofNullable(written.get("tests")),
                Optional.ofNullable(written.get("deliverables")),
                Optional.ofNullable(written.get("documentation")),
                Optional.ofNullable(written.get("data")));
    }

    private static MissionTypeDso.Step stepDso(MissionStep step) {
        return new MissionTypeDso.Step(
                step.id(),
                step.title(),
                step.description(),
                step.role(),
                step.prompt().map(p -> promptPathOf(step.id())),
                step.guidelines().map(g -> guidelinesPathOf(step.id())),
                step.expectedInputArtifacts().stream()
                        .map(MissionTypeBinding::inputDso)
                        .toList(),
                step.expectedOutputArtifacts().stream()
                        .map(MissionTypeBinding::artifactDso)
                        .toList(),
                step.references().stream().map(MissionTypeBinding::referenceDso).toList(),
                step.prompt().map(MissionTypeBinding::markdownOf),
                step.guidelines().map(MissionTypeBinding::markdownOf));
    }

    private static MarkdownDocument markdownOf(StepDocument document) {
        return new MarkdownDocument(
                document.description().map(text -> Map.<String, Object>of(DETAILED_DESCRIPTION, text)),
                document.title(),
                document.preamble(),
                document.sections().stream()
                        .map(section -> new MarkdownDocument.Section(section.title(), section.content()))
                        .toList());
    }

    private static MissionTypeDso.Artifact inputDso(ExpectedInput input) {
        return new MissionTypeDso.Artifact(
                input.artifactKey(), Optional.empty(), input.pathPattern(), input.blocking(), Optional.empty());
    }

    private static MissionTypeDso.Artifact artifactDso(ExpectedArtifact artifact) {
        return new MissionTypeDso.Artifact(
                artifact.artifactKey(),
                Optional.of(artifact.artifactClass().token()),
                artifact.pathPattern(),
                artifact.blocking(),
                artifact.templatePath());
    }

    private static MissionTypeDso.Reference referenceDso(MissionReference reference) {
        return new MissionTypeDso.Reference(
                reference.type().token(), reference.id(), reference.when(), reference.reason());
    }
}
