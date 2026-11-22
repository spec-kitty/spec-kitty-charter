package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.Map;

/**
 * The YAML form of the anti-pattern (ADR-0052 decision 8, as amended by ADR-0053 decision 30), in schema key order:
 * {@code schema_version}, {@code id}, {@code title}, {@code description}, {@code severity}, {@code bad_example},
 * {@code good_example}. The closed schema has already refused unknown keys, wrong types and an unknown severity before
 * {@link #read(Map)} runs.
 */
enum AntiPatternYaml implements YamlFormat<AntiPatternDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.ANTI_PATTERN;
    }

    @Override
    public Class<AntiPatternDso> dsoType() {
        return AntiPatternDso.class;
    }

    @Override
    public AntiPatternDso read(Map<?, ?> root) {
        return new AntiPatternDso(
                required(root, "schema_version"),
                required(root, "id"),
                required(root, "title"),
                required(root, "description"),
                required(root, "severity"),
                required(root, "bad_example"),
                optional(root, "good_example"));
    }

    @Override
    public String write(AntiPatternDso antiPattern) {
        return new YamlDumpEmitter()
                .scalar("schema_version", antiPattern.schemaVersion())
                .scalar("id", antiPattern.id())
                .scalar("title", antiPattern.title())
                .scalar("description", antiPattern.description())
                .scalar("severity", antiPattern.severity())
                .scalar("bad_example", antiPattern.badExample())
                .scalar("good_example", antiPattern.goodExample())
                .dump();
    }
}
