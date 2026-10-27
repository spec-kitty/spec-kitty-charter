package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.list;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.domain.ConfigValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The YAML form of the styleguide. */
enum StyleguideYaml implements YamlFormat<StyleguideDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.STYLEGUIDE;
    }

    @Override
    public Class<StyleguideDso> dsoType() {
        return StyleguideDso.class;
    }

    @Override
    public StyleguideDso read(Map<?, ?> root) {
        List<StyleguideDso.Pattern> patterns = new ArrayList<>();
        for (Object entry : list(root, "patterns")) {
            if (entry instanceof Map<?, ?> p) {
                patterns.add(new StyleguideDso.Pattern(
                        required(p, "name"),
                        required(p, "description"),
                        optional(p, "bad_example"),
                        optional(p, "good_example")));
            }
        }
        List<StyleguideDso.AntiPattern> antiPatterns = new ArrayList<>();
        for (Object entry : list(root, "anti_patterns")) {
            if (entry instanceof Map<?, ?> a) {
                antiPatterns.add(new StyleguideDso.AntiPattern(
                        required(a, "name"),
                        required(a, "description"),
                        required(a, "bad_example"),
                        required(a, "good_example")));
            }
        }
        Optional<Map<String, String>> tooling = Optional.empty();
        if (root.get("tooling") instanceof Map<?, ?> t) {
            Map<String, String> ordered = new LinkedHashMap<>();
            t.forEach((key, value) -> ordered.put(String.valueOf(key), String.valueOf(value)));
            tooling = Optional.of(ordered);
        }
        Optional<ConfigValue> lint = root.get("structural_lint_config") instanceof Map<?, ?> m
                ? Optional.of(ConfigValue.of(m))
                : Optional.empty();
        return new StyleguideDso(
                required(root, "id"),
                required(root, "schema_version"),
                required(root, "title"),
                required(root, "scope"),
                strings(root, "principles"),
                patterns,
                antiPatterns,
                tooling,
                optional(root, "quality_test"),
                lint,
                strings(root, "applies_to_languages"),
                strings(root, "references"));
    }

    @Override
    public String write(StyleguideDso styleguide) {
        List<Map<String, Object>> patterns = new ArrayList<>();
        for (StyleguideDso.Pattern pattern : styleguide.patterns()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("name", pattern.name());
            out.put("description", pattern.description());
            YamlDumpEmitter.scalarInto(out, "bad_example", pattern.badExample());
            YamlDumpEmitter.scalarInto(out, "good_example", pattern.goodExample());
            patterns.add(out);
        }
        List<Map<String, Object>> antiPatterns = new ArrayList<>();
        for (StyleguideDso.AntiPattern antiPattern : styleguide.antiPatterns()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("name", antiPattern.name());
            out.put("description", antiPattern.description());
            out.put("bad_example", antiPattern.badExample());
            out.put("good_example", antiPattern.goodExample());
            antiPatterns.add(out);
        }
        YamlDumpEmitter emitter = new YamlDumpEmitter()
                .scalar("id", styleguide.id())
                .scalar("schema_version", styleguide.schemaVersion())
                .scalar("title", styleguide.title())
                .scalar("scope", styleguide.scope())
                .strings("principles", styleguide.principles())
                .mappings("patterns", patterns)
                .mappings("anti_patterns", antiPatterns);
        styleguide.tooling().ifPresent(tooling -> emitter.present("tooling", new LinkedHashMap<>(tooling)));
        emitter.scalar("quality_test", styleguide.qualityTest());
        styleguide
                .structuralLintConfig()
                .ifPresent(config -> emitter.present("structural_lint_config", config.toParsed()));
        return emitter.strings("applies_to_languages", styleguide.appliesToLanguages())
                .strings("references", styleguide.references())
                .dump();
    }
}
