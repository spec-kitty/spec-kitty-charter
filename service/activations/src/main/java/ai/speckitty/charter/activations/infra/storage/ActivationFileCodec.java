package ai.speckitty.charter.activations.infra.storage;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.Jcs;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The bytes of {@code activation.json}: exactly {@link Jcs#canonical} of the document's canonical tree, so the bytes
 * stored are the bytes its revision hashes (FR-005), and a document read back is byte-identical to the one written.
 *
 * <p>Reading parses the file with Jackson into the plain {@link Map} and {@link List} tree and builds the document
 * with the domain's builder. A file that does not parse, names a member the document has not, builds with a shape
 * violation, or is not in canonical form is a corrupt store, an {@link ActivationStorageException} naming the id.
 */
final class ActivationFileCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Set<String> MEMBERS =
            Set.of("schemaVersion", "packs", "activatedKinds", "missionTypes", "selected", "deactivated", "languages");
    private static final Set<String> PACK_MEMBERS = Set.of("packId", "version");

    private ActivationFileCodec() {}

    /**
     * The file's bytes.
     *
     * @param document the document
     * @return the RFC 8785 serialization of its canonical tree, as UTF-8
     */
    static byte[] encode(ActivationDocument document) {
        return Jcs.canonical(document.toCanonicalTree());
    }

    /**
     * The document of the file's bytes.
     *
     * @param id the activation the file belongs to, named in a refusal
     * @param bytes the file's bytes
     * @return the document
     * @throws ActivationStorageException when the bytes are not a canonical activation document
     */
    static ActivationDocument decode(ActivationId id, byte[] bytes) {
        ActivationDocument.Built built;
        try {
            built = build(MAPPER.readValue(bytes, Object.class));
        } catch (JacksonException | IllegalArgumentException e) {
            throw new ActivationStorageException(id, "is not an activation document", e);
        }
        if (!built.violations().isEmpty()) {
            throw new ActivationStorageException(id, "breaks the document shape: " + built.violations());
        }
        if (!Arrays.equals(encode(built.document()), bytes)) {
            throw new ActivationStorageException(id, "is not in canonical form");
        }
        return built.document();
    }

    private static ActivationDocument.Built build(@Nullable Object parsed) {
        Map<String, @Nullable Object> tree = object(parsed, MEMBERS);
        ActivationDocument.Builder builder = ActivationDocument.builder();
        builder.schemaVersion(integer(tree.get("schemaVersion")));
        member(
                tree,
                "packs",
                p -> builder.packs(
                        list(p).stream().map(ActivationFileCodec::pack).toList()));
        member(
                tree,
                "activatedKinds",
                k -> builder.activatedKinds(
                        strings(k).stream().map(ActivationFileCodec::kind).toList()));
        member(tree, "missionTypes", m -> builder.missionTypes(strings(m)));
        member(tree, "languages", l -> builder.languages(strings(l)));
        member(tree, "selected", s -> kindLists(s, builder::selected));
        member(tree, "deactivated", d -> kindLists(d, builder::deactivated));
        return builder.build();
    }

    private static void member(Map<String, @Nullable Object> tree, String name, Consumer<@Nullable Object> use) {
        if (tree.containsKey(name)) {
            use.accept(tree.get(name));
        }
    }

    private static void kindLists(@Nullable Object lists, BiConsumer<ArtifactKind, List<String>> put) {
        object(lists, Set.of()).forEach((token, ids) -> put.accept(kind(token), strings(ids)));
    }

    private static PackRef pack(@Nullable Object parsed) {
        Map<String, @Nullable Object> pack = object(parsed, PACK_MEMBERS);
        Optional<String> version =
                pack.containsKey("version") ? Optional.of(string(pack.get("version"))) : Optional.empty();
        return new PackRef(string(pack.get("packId")), version);
    }

    private static ArtifactKind kind(String token) {
        return ArtifactKind.fromToken(token)
                .orElseThrow(() -> new IllegalArgumentException("no kind has the token '" + token + "'"));
    }

    /**
     * A JSON object as a map.
     *
     * @param parsed the parsed value
     * @param allowed the member names allowed, or empty for any
     */
    private static Map<String, @Nullable Object> object(@Nullable Object parsed, Set<String> allowed) {
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("expected an object");
        }
        if (!allowed.isEmpty() && !allowed.containsAll(map.keySet())) {
            throw new IllegalArgumentException("unexpected members " + map.keySet());
        }
        Map<String, @Nullable Object> members = new LinkedHashMap<>();
        map.forEach((name, value) -> members.put(string(name), value));
        return members;
    }

    private static List<?> list(@Nullable Object parsed) {
        if (parsed instanceof List<?> list) {
            return list;
        }
        throw new IllegalArgumentException("expected an array");
    }

    private static List<String> strings(@Nullable Object parsed) {
        return list(parsed).stream().map(ActivationFileCodec::string).toList();
    }

    private static String string(@Nullable Object parsed) {
        if (parsed instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("expected a string");
    }

    private static int integer(@Nullable Object parsed) {
        if (parsed instanceof Integer number) {
            return number;
        }
        throw new IllegalArgumentException("expected an integer");
    }
}
