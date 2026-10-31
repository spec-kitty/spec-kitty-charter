package ai.speckitty.charter.packs.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Classifies a pack-relative file path against the bundled {@code file-kinds.yaml}: the file is mapped to a kind (with
 * the schema that validates it, or none for a schema-less payload) and its {@link ContentForm}, excluded for a reviewed
 * reason, retired (a file of a layout the service no longer reads, refused by name, ADR-0044 decision 6), or not classified at
 * all. The {@code files} rules are consulted first, then the {@code exclusions}, then the {@code retired} rules, then the
 * {@code payloads}. A top-level key the table does not define is refused when it loads, never ignored.
 *
 * <p>The rule's content form decides how the store holds the file. A table is refused when it loads if a {@code files}
 * rule declares a form other than {@code yaml} or {@code markdown} (its schema validates the parsed YAML document, or
 * the parsed Markdown model), a {@code markdown} rule's schema does not load, a {@code payloads} rule declares a form
 * other than {@code bytes} (the only form the store holds a schema-less file in), or a {@code bytes} rule names a kind
 * outside {@link #BYTES_KINDS}, the kinds the store holds a {@code bytes} file as.
 *
 * <p>A rule's {@code match} is a glob over the path relative to the directory that holds the pack directories, with
 * forward slashes. {@code *} matches within one path segment, {@code **}{@code /} matches zero or more whole
 * segments, and every other character is literal.
 */
final class PackFileKind {

    /** The table's classpath location, named in every failure to read it. */
    static final String RESOURCE = "packs/file-kinds.yaml";

    private static final String SCHEMA_DIR = "packs/schemas/";
    private static final String SCHEMA_SUFFIX = ".schema.json";

    private static final String MATCH = "match";
    private static final String CONTENT_FORM = "content_form";
    private static final Set<String> FILE_KEYS = Set.of(MATCH, "schema", CONTENT_FORM);
    private static final Set<String> EXCLUSION_KEYS = Set.of(MATCH, "reason");
    private static final Set<String> PAYLOAD_KEYS = Set.of(MATCH, "kind", CONTENT_FORM);
    private static final Set<String> RETIRED_KEYS = Set.of(MATCH, "reason");
    private static final String RETIRED = "retired";
    private static final String PAYLOADS = "payloads";

    /** The table's top-level keys; any other is refused when the table loads (#194). */
    private static final Set<String> TOP_LEVEL_KEYS =
            Set.of("node_kinds", "artifact_kinds", "fragment_kinds", "files", "exclusions", PAYLOADS, RETIRED);

    /**
     * The content forms the table admits. A rule naming another form fails when the table loads (C-004). Admitting a
     * form is one entry here plus its codec; holding it also takes the store's read and write path, the digest and
     * validate mode, each an exhaustive switch over {@link ContentForm}. {@code markdown} is admitted and classified,
     * and the store reads it as a bound kind's companion (a toolguide's guide, #194) or as the pack document
     * ({@code pack.md}, #260); any other file of it fails loudly ({@code PackFileSet.notStored}) until the rule that
     * adds it wires its own store path.
     */
    private static final Set<ContentForm> BUILT_FORMS =
            EnumSet.of(ContentForm.YAML, ContentForm.MARKDOWN, ContentForm.BYTES);

    /** The {@code payloads} rule kind of an asset file. */
    static final String ASSET_PAYLOAD = "asset-payload";

    /** The {@code payloads} rule kind of a template, the {@code template} artifact kind's token. */
    static final String TEMPLATE = "template";

    /**
     * The kinds the store holds a {@code bytes} file as: an asset file or a template. A {@code bytes} rule of any other
     * kind fails when the table loads; a new one is one entry here plus where the store holds it.
     */
    static final Set<String> BYTES_KINDS = Set.of(ASSET_PAYLOAD, TEMPLATE);

    private static final PackFileKind BUNDLED = load();

    /** The outcome of classifying one path. */
    sealed interface Classification permits Mapped, Excluded, Retired, Unclassified {}

    /**
     * A file of a known kind.
     *
     * @param kind for a {@code files} rule the schema's kind name, as {@link PackSchemas#validate} takes it; for a
     *     {@code payloads} rule the rule's {@code kind}
     * @param schema the classpath location of the schema document; empty for a payload, which has no schema
     * @param contentForm how the file is read, held, written and compared
     */
    record Mapped(String kind, Optional<String> schema, ContentForm contentForm) implements Classification {}

    /**
     * A file that is not pack content or has no schema, for a reviewed reason.
     *
     * @param exclusionClass the exclusion's {@code match} glob, which names the class of files it covers
     * @param reason why the class has no schema
     */
    record Excluded(String exclusionClass, String reason) implements Classification {}

    /**
     * A file of a retired family (ADR-0044 decision 6): the store's load and validate mode refuse it by name, the gate reports it
     * in its own bucket, and the fingerprint digests it, so a stale file cannot hide.
     *
     * @param match the {@code retired} rule's glob, which names the family
     * @param reason why the family is retired
     */
    record Retired(String match, String reason) implements Classification {}

    /** A file that no rule covers. */
    enum Unclassified implements Classification {
        /** The only value. */
        INSTANCE
    }

    /**
     * A {@code files} rule.
     *
     * @param match the glob
     * @param schema the schema kind it maps to
     * @param contentForm the declared content form; {@link ContentForm#YAML} when the rule names none
     */
    record FileRule(String match, String schema, ContentForm contentForm) {}

    /**
     * An {@code exclusions} rule.
     *
     * @param match the glob
     * @param reason the reviewable reason
     */
    record ExclusionRule(String match, String reason) {}

    /**
     * A {@code payloads} rule: files with no schema, consulted only when no {@code files} or {@code exclusions} rule
     * matches.
     *
     * @param match the glob
     * @param kind the kind the file is mapped to
     * @param contentForm the declared content form
     */
    record PayloadRule(String match, String kind, ContentForm contentForm) {}

    /**
     * A {@code retired} rule: a family of files the service no longer reads, refused by name.
     *
     * @param match the glob
     * @param reason why the family is retired
     */
    record RetiredRule(String match, String reason) {}

    private record Compiled(String match, Pattern pattern) {}

    private final String schemaDir;
    private final List<String> nodeKinds;
    private final Map<String, String> fragmentKinds;
    private final List<FileRule> fileRules;
    private final List<ExclusionRule> exclusionRules;
    private final List<PayloadRule> payloadRules;
    private final List<RetiredRule> retiredRules;
    private final List<Compiled> compiled;
    private final List<Compiled> payloadsCompiled;

    /**
     * A table read from the text of a {@code file-kinds.yaml}, which the tests use to classify with a changed copy.
     *
     * @param yaml the table's YAML text
     * @throws IllegalStateException when the text is not a well-formed table
     */
    PackFileKind(String yaml) {
        this(yaml, SCHEMA_DIR);
    }

    /**
     * A table read from the text of a {@code file-kinds.yaml} whose schemas live in {@code schemaDir}, which the tests
     * use to classify with a rule whose structure schema is a test fixture.
     *
     * @param yaml the table's YAML text
     * @param schemaDir the classpath directory (with a trailing slash) that holds {@code <schema>.schema.json}
     * @throws IllegalStateException when the text is not a well-formed table, or a {@code markdown} rule's schema does
     *     not load
     */
    PackFileKind(String yaml, String schemaDir) {
        this(parse(yaml), schemaDir);
    }

    private PackFileKind(Table table, String schemaDir) {
        requireLoadableStructureSchemas(table.fileRules(), schemaDir);
        this.schemaDir = schemaDir;
        this.nodeKinds = List.copyOf(table.nodeKinds());
        this.fragmentKinds = Map.copyOf(table.fragmentKinds());
        this.fileRules = List.copyOf(table.fileRules());
        this.exclusionRules = List.copyOf(table.exclusionRules());
        this.payloadRules = List.copyOf(table.payloadRules());
        this.retiredRules = List.copyOf(table.retiredRules());
        List<Compiled> all = new ArrayList<>();
        fileRules.forEach(r -> all.add(new Compiled(r.match(), glob(r.match()))));
        exclusionRules.forEach(r -> all.add(new Compiled(r.match(), glob(r.match()))));
        retiredRules.forEach(r -> all.add(new Compiled(r.match(), glob(r.match()))));
        this.compiled = List.copyOf(all);
        this.payloadsCompiled = payloadRules.stream()
                .map(r -> new Compiled(r.match(), glob(r.match())))
                .toList();
    }

    private record Table(
            List<String> nodeKinds,
            Map<String, String> fragmentKinds,
            List<FileRule> fileRules,
            List<ExclusionRule> exclusionRules,
            List<PayloadRule> payloadRules,
            List<RetiredRule> retiredRules) {}

    /** The table bundled with this adapter, read once. */
    static PackFileKind bundled() {
        return BUNDLED;
    }

    /** The service's DRG node kind tokens, in schema order. */
    List<String> nodeKinds() {
        return nodeKinds;
    }

    /** The fragment node kind plurals, each mapped to its node kind. */
    Map<String, String> fragmentKinds() {
        return fragmentKinds;
    }

    /** The rules that map a file to a schema, in table order. */
    List<FileRule> fileRules() {
        return fileRules;
    }

    /** The reviewed exclusions, in table order. */
    List<ExclusionRule> exclusionRules() {
        return exclusionRules;
    }

    /** The rules for files with no schema, in table order. */
    List<PayloadRule> payloadRules() {
        return payloadRules;
    }

    /** The retired families, in table order; empty until the re-pin gives the bundled table its rules (#194). */
    List<RetiredRule> retiredRules() {
        return retiredRules;
    }

    /**
     * Every {@code files}, {@code exclusions} or {@code retired} rule glob that matches {@code path}; the table
     * promises at most one.
     * The payload rules are left out: they overlap the other two by design (a sidecar under {@code assets/} matches a
     * {@code files} rule and a payload rule), and only the first match counts.
     */
    List<String> matchingRules(String path) {
        return matching(compiled, path);
    }

    /** Every {@code payloads} rule glob that matches {@code path}; the table promises at most one. */
    List<String> matchingPayloadRules(String path) {
        return matching(payloadsCompiled, path);
    }

    private static List<String> matching(List<Compiled> rules, String path) {
        return rules.stream()
                .filter(c -> c.pattern().matcher(path).matches())
                .map(Compiled::match)
                .toList();
    }

    /**
     * Classifies a pack-relative path.
     *
     * @param path the path relative to the directory that holds the pack directories, with forward slashes
     * @return how the path is handled
     */
    Classification classify(String path) {
        // `compiled` holds the files rules, then the exclusions, then the retired rules, each compiled once when the
        // table was built.
        for (int i = 0; i < compiled.size(); i++) {
            if (compiled.get(i).pattern().matcher(path).matches()) {
                return ruleAt(i);
            }
        }
        for (int i = 0; i < payloadsCompiled.size(); i++) {
            if (payloadsCompiled.get(i).pattern().matcher(path).matches()) {
                PayloadRule rule = payloadRules.get(i);
                return new Mapped(rule.kind(), Optional.empty(), rule.contentForm());
            }
        }
        return Unclassified.INSTANCE;
    }

    /** The classification of the {@code i}th rule of {@link #compiled}. */
    private Classification ruleAt(int i) {
        if (i < fileRules.size()) {
            FileRule rule = fileRules.get(i);
            return new Mapped(
                    rule.schema(), Optional.of(schemaDir + rule.schema() + SCHEMA_SUFFIX), rule.contentForm());
        }
        int exclusion = i - fileRules.size();
        if (exclusion < exclusionRules.size()) {
            ExclusionRule rule = exclusionRules.get(exclusion);
            return new Excluded(rule.match(), rule.reason());
        }
        RetiredRule rule = retiredRules.get(exclusion - exclusionRules.size());
        return new Retired(rule.match(), rule.reason());
    }

    private static Pattern glob(String match) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < match.length()) {
            if (match.startsWith("**/", i)) {
                regex.append("(?:.+/)?");
                i += 3;
            } else if (match.charAt(i) == '*') {
                regex.append("[^/]*");
                i++;
            } else {
                int next = i;
                while (next < match.length() && match.charAt(next) != '*') {
                    next++;
                }
                regex.append(Pattern.quote(match.substring(i, next)));
                i = next;
            }
        }
        return Pattern.compile(regex.toString());
    }

    private static PackFileKind load() {
        return new PackFileKind(readResource());
    }

    private static Table parse(String yaml) {
        Object root = Yaml.load(yaml);
        Map<String, Object> table = map(root, RESOURCE);
        for (String key : table.keySet()) {
            if (!TOP_LEVEL_KEYS.contains(key)) {
                throw new IllegalStateException(
                        RESOURCE + ": the unknown top-level key '" + key + "'; the table takes only "
                                + TOP_LEVEL_KEYS.stream().sorted().toList());
            }
        }
        List<String> nodeKinds = strings(table.get("node_kinds"), "node_kinds");
        Map<String, String> fragments = new LinkedHashMap<>();
        map(table.get("fragment_kinds"), "fragment_kinds").forEach((k, v) -> fragments.put(k, String.valueOf(v)));
        List<FileRule> files = new ArrayList<>();
        for (Object entry : list(table.get("files"), "files")) {
            Map<String, Object> rule = rule(entry, "files", FILE_KEYS);
            ContentForm form = rule.containsKey(CONTENT_FORM) ? contentForm(rule) : ContentForm.YAML;
            if (form == ContentForm.BYTES) {
                // A schema validates a parsed document: a YAML tree, or the model of a Markdown document.
                throw new IllegalStateException(RESOURCE + ": the files rule for '" + string(rule, MATCH)
                        + "' declares content_form '" + form.token()
                        + "'; a files rule has a schema and its files are yaml or markdown");
            }
            files.add(new FileRule(string(rule, MATCH), string(rule, "schema"), form));
        }
        List<ExclusionRule> exclusions = new ArrayList<>();
        for (Object entry : list(table.get("exclusions"), "exclusions")) {
            Map<String, Object> rule = rule(entry, "exclusions", EXCLUSION_KEYS);
            exclusions.add(new ExclusionRule(string(rule, MATCH), string(rule, "reason")));
        }
        return new Table(nodeKinds, fragments, files, exclusions, payloads(table), retired(table));
    }

    /** The optional {@code payloads} section: a table without schema-less files has none. */
    private static List<PayloadRule> payloads(Map<String, Object> table) {
        List<PayloadRule> payloads = new ArrayList<>();
        for (Object entry : section(table, PAYLOADS)) {
            Map<String, Object> rule = rule(entry, PAYLOADS, PAYLOAD_KEYS);
            ContentForm form = contentForm(rule);
            String kind = string(rule, "kind");
            if (form != ContentForm.BYTES) {
                // A schema-less file is held only as bytes; any other form would be held nowhere, a silent drop.
                throw new IllegalStateException(RESOURCE + ": the payloads rule for '" + string(rule, MATCH)
                        + "' declares content_form '" + form.token()
                        + "'; a payloads rule has no schema and its files are bytes");
            }
            if (!BYTES_KINDS.contains(kind)) {
                throw new IllegalStateException(RESOURCE + ": the payloads rule for '" + string(rule, MATCH)
                        + "' declares content_form 'bytes' for kind '" + kind
                        + "', which the store does not hold; a bytes file is one of "
                        + BYTES_KINDS.stream().sorted().toList());
            }
            payloads.add(new PayloadRule(string(rule, MATCH), kind, form));
        }
        return payloads;
    }

    /**
     * The optional {@code retired} section (ADR-0044 decision 6): each rule a {@code match} and a {@code reason}, both required.
     * A table without retired families has none.
     */
    private static List<RetiredRule> retired(Map<String, Object> table) {
        List<RetiredRule> retired = new ArrayList<>();
        for (Object entry : section(table, RETIRED)) {
            Map<String, Object> rule = rule(entry, RETIRED, RETIRED_KEYS);
            retired.add(new RetiredRule(string(rule, MATCH), string(rule, "reason")));
        }
        return retired;
    }

    /** The entries of an optional list section; none when the table leaves it out. */
    private static List<?> section(Map<String, Object> table, String name) {
        Object section = table.get(name);
        return section == null ? List.of() : list(section, name);
    }

    /**
     * A {@code markdown} rule's schema validates the parsed model, so it must load as strictly as every bundled schema
     * ({@link PackSchemas}: standard 2020-12 keywords only, offline); one that does not is refused when the table
     * loads, naming the rule, instead of when the first file is validated.
     */
    private static void requireLoadableStructureSchemas(List<FileRule> rules, String schemaDir) {
        for (FileRule rule : rules) {
            if (rule.contentForm() != ContentForm.MARKDOWN) {
                continue;
            }
            try {
                new PackSchemas(schemaDir, List.of(rule.schema()));
            } catch (RuntimeException notLoadable) {
                throw new IllegalStateException(
                        RESOURCE + ": the files rule for '" + rule.match() + "' declares content_form 'markdown' with"
                                + " schema '" + rule.schema() + "', which does not load: " + notLoadable.getMessage(),
                        notLoadable);
            }
        }
    }

    /** A rule of {@code section}; a key the section does not define is refused, never ignored. */
    private static Map<String, Object> rule(Object entry, String section, Set<String> keys) {
        Map<String, Object> rule = map(entry, section);
        for (String key : rule.keySet()) {
            if (!keys.contains(key)) {
                throw new IllegalStateException(RESOURCE + ": a rule in " + section + " has the unknown key '" + key
                        + "'; it takes only " + keys.stream().sorted().toList());
            }
        }
        return rule;
    }

    /** The rule's {@code content_form}; a form the store does not build yet is refused when the table loads. */
    private static ContentForm contentForm(Map<String, Object> rule) {
        ContentForm form = ContentForm.fromToken(string(rule, CONTENT_FORM));
        if (!BUILT_FORMS.contains(form)) {
            throw new IllegalStateException(RESOURCE + ": content_form '" + form.token()
                    + "' is declared but not built yet (the mission that first needs it builds it); the rule for '"
                    + string(rule, MATCH)
                    + "' cannot be read");
        }
        return form;
    }

    private static String readResource() {
        try (InputStream in = PackFileKind.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("The bundled " + RESOURCE + " is missing from the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(@Nullable Object value, String where) {
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new IllegalStateException(RESOURCE + ": " + where + " must be a mapping");
    }

    private static List<?> list(@Nullable Object value, String where) {
        if (value instanceof List<?> l) {
            return l;
        }
        throw new IllegalStateException(RESOURCE + ": " + where + " must be a list");
    }

    private static List<String> strings(@Nullable Object value, String where) {
        return list(value, where).stream().map(String::valueOf).toList();
    }

    private static String string(Map<String, Object> rule, String key) {
        Object value = rule.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s.strip();
        }
        throw new IllegalStateException(RESOURCE + ": every rule needs a non-blank '" + key + "'");
    }
}
