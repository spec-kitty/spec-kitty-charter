package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.list;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The YAML form of the glossary pack. A term's {@code confidence} is read and written as a number, never as text: the
 * schema guarantees a number, and {@code 1.0} written as {@code "1.0"} would change its meaning (ADR-0036).
 */
enum GlossaryPackYaml implements YamlFormat<GlossaryPackDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.GLOSSARY_PACK;
    }

    @Override
    public Class<GlossaryPackDso> dsoType() {
        return GlossaryPackDso.class;
    }

    @Override
    public GlossaryPackDso read(Map<?, ?> root) {
        List<GlossaryPackDso.Term> terms = new ArrayList<>();
        for (Object entry : list(root, "terms")) {
            if (entry instanceof Map<?, ?> t) {
                terms.add(new GlossaryPackDso.Term(
                        required(t, "surface"),
                        required(t, "definition"),
                        confidence(t),
                        required(t, "status"),
                        strings(t, "see_also"),
                        optional(t, "introduced_in_mission"),
                        strings(t, "synonyms_to_avoid"),
                        strings(t, "aliases"),
                        strings(t, "banned_synonyms")));
            }
        }
        return new GlossaryPackDso(
                required(root, "id"), required(root, "provenance"), optional(root, "description"), terms);
    }

    @Override
    public String write(GlossaryPackDso pack) {
        List<Map<String, Object>> terms = new ArrayList<>();
        for (GlossaryPackDso.Term term : pack.terms()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("surface", term.surface());
            out.put("definition", term.definition());
            out.put("confidence", term.confidence());
            out.put("status", term.status());
            YamlDumpEmitter.stringsInto(out, "see_also", term.seeAlso());
            YamlDumpEmitter.scalarInto(out, "introduced_in_mission", term.introducedInMission());
            YamlDumpEmitter.stringsInto(out, "synonyms_to_avoid", term.synonymsToAvoid());
            YamlDumpEmitter.stringsInto(out, "aliases", term.aliases());
            YamlDumpEmitter.stringsInto(out, "banned_synonyms", term.bannedSynonyms());
            terms.add(out);
        }
        return new YamlDumpEmitter()
                .scalar("id", pack.id())
                .scalar("provenance", pack.provenance())
                .scalar("description", pack.description())
                .mappings("terms", terms)
                .dump();
    }

    /** A term's {@code confidence}; the schema has checked it is a number. */
    private static double confidence(Map<?, ?> term) {
        if (term.get("confidence") instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalStateException("'confidence' passed the schema but is no number");
    }
}
