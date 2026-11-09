package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.list;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

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
        List<ParadigmDso.Reference> references = new ArrayList<>();
        for (Object entry : list(root, "references")) {
            if (entry instanceof Map<?, ?> r) {
                references.add(new ParadigmDso.Reference(required(r, "type"), required(r, "id")));
            }
        }
        return new ParadigmDso(
                required(root, "id"),
                required(root, "schema_version"),
                required(root, "name"),
                required(root, "summary"),
                strings(root, "directive_refs"),
                references);
    }

    @Override
    public String write(ParadigmDso paradigm) {
        List<Map<String, Object>> references = new ArrayList<>();
        for (ParadigmDso.Reference reference : paradigm.references()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("type", reference.type());
            out.put("id", reference.id());
            references.add(out);
        }
        return new YamlDumpEmitter()
                .scalar("id", paradigm.id())
                .scalar("schema_version", paradigm.schemaVersion())
                .scalar("name", paradigm.name())
                .scalar("summary", paradigm.summary())
                .strings("directive_refs", paradigm.directiveRefs())
                .mappings("references", references)
                .dump();
    }
}
