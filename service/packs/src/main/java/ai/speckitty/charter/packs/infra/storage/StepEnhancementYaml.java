package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.mappings;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The YAML form of the step enhancement (ADR-0066), in schema key order: {@code schema_version}, {@code id},
 * {@code prompt_fragments}. The schema has already refused unknown keys and wrong types before {@link #read(Map)}
 * runs. The companion bodies are not in the file; the store fills them in from the companions.
 */
enum StepEnhancementYaml implements YamlFormat<StepEnhancementDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.STEP_ENHANCEMENT;
    }

    @Override
    public Class<StepEnhancementDso> dsoType() {
        return StepEnhancementDso.class;
    }

    @Override
    public StepEnhancementDso read(Map<?, ?> root) {
        List<StepEnhancementDso.Entry> entries = new ArrayList<>();
        for (Map<?, ?> entry : mappings(root, "prompt_fragments")) {
            entries.add(
                    new StepEnhancementDso.Entry(required(entry, "slot"), required(entry, "path"), Optional.empty()));
        }
        return new StepEnhancementDso(required(root, "id"), required(root, "schema_version"), entries);
    }

    @Override
    public String write(StepEnhancementDso enhancement) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (StepEnhancementDso.Entry entry : enhancement.promptFragments()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("slot", entry.slot());
            out.put("path", entry.path());
            entries.add(out);
        }
        return new YamlDumpEmitter()
                .scalar("schema_version", enhancement.schemaVersion())
                .scalar("id", enhancement.id())
                .mappings("prompt_fragments", entries)
                .dump();
    }
}
