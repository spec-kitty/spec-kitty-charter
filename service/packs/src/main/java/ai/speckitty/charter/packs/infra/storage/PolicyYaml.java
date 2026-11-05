package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The YAML form of a pack's mutability policy file, {@code policy.yaml} (ADR-0062): read through the bundled
 * {@code policy} schema into the domain's {@link MutabilityPolicySource}. The schema is closed, so every key the file
 * holds is one this reader maps: {@code kind_defaults} by artifact kind token, {@code artifacts} by bare URN, each to a
 * level. Not a {@link YamlFormat}: the policy is a pack-level file, not a kind. Nothing writes it yet.
 */
final class PolicyYaml {

    /** The schema that validates a policy, as {@code contracts/packs/file-kinds.yaml} names it. */
    static final String SCHEMA = "policy";

    /** The policy file's pack-relative path, the one the descriptor's {@code mutability_policy} may name. */
    static final String FILE = "policy.yaml";

    /** A policy file that is not YAML or breaks its schema; the message is the store's reason for the first breach. */
    static final class Unreadable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Unreadable(String reason) {
            super(reason);
        }
    }

    private PolicyYaml() {}

    /**
     * Reads a policy file's text.
     *
     * @param text the file's text
     * @return the policy it declares
     * @throws Unreadable when the text is not YAML or breaks the {@code policy} schema
     */
    static MutabilityPolicySource read(String text) {
        ParsedYaml parsed = ParsedYaml.parse(text, FILE);
        if (parsed.failure() != null) {
            throw unreadable(new FileRead.Unreadable<>(FILE, parsed.failure(), List.of()));
        }
        Object document = parsed.document();
        List<SchemaViolation> violations = PackSchemas.bundled().validate(SCHEMA, FILE, document);
        if (!violations.isEmpty()) {
            throw unreadable(new FileRead.Unreadable<>(FILE, null, violations));
        }
        if (!(document instanceof Map<?, ?> root)) {
            throw new IllegalStateException("a policy passed the schema but is no mapping");
        }
        MutabilityPolicySource.Builder builder = MutabilityPolicySource.builder();
        levels(root, "kind_defaults", PolicyYaml::kind, builder::kindDefault);
        levels(root, "artifacts", PolicyYaml::urn, builder::entry);
        return builder.build();
    }

    /** Maps each entry of one schema-valid level map through {@code key} into {@code sink}. */
    private static <K> void levels(
            Map<?, ?> root, String field, Function<String, K> key, BiConsumer<K, MutabilityLevel> sink) {
        @Nullable Object raw = root.get(field);
        if (raw instanceof Map<?, ?> entries) {
            entries.forEach((name, level) -> sink.accept(key.apply(String.valueOf(name)), level(level)));
        }
    }

    private static NodeKind kind(String token) {
        return NodeKind.fromToken(token)
                .orElseThrow(() -> new IllegalStateException("kind '" + token + "' passed the schema but is no kind"));
    }

    private static Urn urn(String token) {
        return Urn.parse(token)
                .orElseThrow(() -> new IllegalStateException("'" + token + "' passed the schema but is no URN"));
    }

    private static MutabilityLevel level(Object token) {
        return MutabilityLevel.fromToken(String.valueOf(token))
                .orElseThrow(() -> new IllegalStateException("level '" + token + "' passed the schema but is none"));
    }

    private static Unreadable unreadable(FileRead.Unreadable<?> bad) {
        return new Unreadable(FilesystemPackStore.violationsOf(bad).stream()
                .map(PackElementViolation::reason)
                .collect(Collectors.joining("; ")));
    }
}
