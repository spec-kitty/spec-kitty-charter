package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.mappings;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The YAML form of the paradigm. */
enum ParadigmYaml implements YamlFormat<ParadigmDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.PARADIGM;
    }

    @Override
    public Class<ParadigmDso> dsoType() {
        return ParadigmDso.class;
    }

    @Override
    public ParadigmDso read(Map<?, ?> root) {
        return new ParadigmDso(
                required(root, "id"),
                required(root, "schema_version"),
                required(root, "name"),
                required(root, "summary"),
                mappings(root, "principles").stream()
                        .map(item -> new ParadigmDso.Principle(required(item, "name"), required(item, "description")))
                        .toList());
    }

    @Override
    public String write(ParadigmDso paradigm) {
        return new YamlDumpEmitter()
                .scalar("id", paradigm.id())
                .scalar("schema_version", paradigm.schemaVersion())
                .scalar("name", paradigm.name())
                .scalar("summary", paradigm.summary())
                .mappings("principles", principles(paradigm))
                .dump();
    }

    private static List<Map<String, Object>> principles(ParadigmDso paradigm) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ParadigmDso.Principle principle : paradigm.principles()) {
            Map<String, Object> item = YamlDumpEmitter.mapping();
            item.put("name", principle.name());
            item.put("description", principle.description());
            out.add(item);
        }
        return out;
    }
}
