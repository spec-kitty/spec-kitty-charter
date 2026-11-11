package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.domain.PackContents;
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
                PrincipleYaml.read(root));
    }

    @Override
    public List<PackContents.SchemaViolation> rules(Map<?, ?> root, String file) {
        return PrincipleYaml.rules(root, file);
    }

    @Override
    public String write(ParadigmDso paradigm) {
        return new YamlDumpEmitter()
                .scalar("id", paradigm.id())
                .scalar("schema_version", paradigm.schemaVersion())
                .scalar("name", paradigm.name())
                .scalar("summary", paradigm.summary())
                .mappings(PrincipleYaml.KEY, PrincipleYaml.write(paradigm.principles()))
                .dump();
    }
}
