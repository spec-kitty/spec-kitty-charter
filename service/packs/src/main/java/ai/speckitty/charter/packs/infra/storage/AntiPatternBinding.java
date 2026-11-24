package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.AntiPattern;
import ai.speckitty.charter.packs.domain.AntiPatternIdentifier;
import ai.speckitty.charter.packs.domain.AntiPatternSeverity;

/**
 * The {@link KindBinding} of the anti-pattern (ADR-0052 decision 8, as amended by ADR-0053 decision 30):
 * {@link AntiPatternDso} to {@link AntiPattern} and back. One file per anti-pattern at
 * {@code anti_patterns/<id>.anti-pattern.yaml}, whose stem is its id; a file anywhere else is refused by placement
 * ({@link FileStems}). The kind token stays {@code anti_pattern}; the file and schema token is {@code anti-pattern}. A
 * graph node of the same URN declares the anti-pattern and is a {@code rejects} target; it holds nothing.
 */
enum AntiPatternBinding implements KindBinding<AntiPatternDso, AntiPattern> {
    /** The binding. */
    INSTANCE;

    /** The file and schema token of an anti-pattern: {@code anti_patterns/<id>.anti-pattern.yaml}. */
    private static final String TOKEN = "anti-pattern";

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.ANTI_PATTERN;
    }

    @Override
    public Class<AntiPattern> entityType() {
        return AntiPattern.class;
    }

    @Override
    public String fileToken() {
        return TOKEN;
    }

    @Override
    public String schemaName() {
        return TOKEN;
    }

    /** An anti-pattern's place: {@code anti_patterns/<id>.anti-pattern.yaml} (ADR-0052 decision 8). */
    @Override
    public String fileName(String elementId) {
        return ArtifactKind.ANTI_PATTERN.plural() + "/" + elementId + "." + TOKEN + ".yaml";
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
    public Class<AntiPatternDso> dsoType() {
        return AntiPatternDso.class;
    }

    @Override
    public String idOf(AntiPatternDso dso) {
        return dso.id();
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException also when the severity is not a token the domain knows; the schema refuses one
     *     at load, so this is reached only by a shape that bypassed it
     */
    @Override
    public AntiPattern toEntity(PackIdentifier pack, AntiPatternDso dso) {
        return AntiPattern.builder()
                .id(new AntiPatternIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .title(dso.title())
                .description(dso.description())
                .severity(AntiPatternSeverity.fromToken(dso.severity())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "severity '" + dso.severity() + "' is none of smell, severe and breaking")))
                .badExample(dso.badExample())
                .goodExample(dso.goodExample())
                .build();
    }

    @Override
    public AntiPatternDso toDso(AntiPattern antiPattern) {
        return new AntiPatternDso(
                antiPattern.schemaVersion().orElseThrow(),
                antiPattern.localId(),
                antiPattern.title(),
                antiPattern.description().orElseThrow(),
                antiPattern.severity().token(),
                antiPattern.badExample(),
                antiPattern.goodExample());
    }
}
