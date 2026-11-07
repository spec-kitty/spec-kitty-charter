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
 * Every write is at {@code schemaVersion} 2.
 *
 * <p>Reading parses the file with Jackson into the plain {@link Map} and {@link List} tree and builds the document
 * with the domain's builder, by the file's {@code schemaVersion}:
 *
 * <ul>
 *   <li><b>Version 2</b> is read strictly: a file that names a member the document has not ({@code activatedKinds} and
 *       {@code missionTypes} included), builds with a shape violation, or is not in canonical form (an empty allow
 *       list included) is a corrupt store.
 *   <li><b>Version 1</b> is upgraded in memory (IC-20, FR-041): its {@code missionTypes} become
 *       {@code selected.mission_type}, every selection is kept and {@code activatedKinds} is discarded (decision
 *       {@code DM-01M6RMJHDKNTXAM055Q8WR6SDN}: the gate was never enforced, so the in-force set does not move). The
 *       file must still have the version 1 shape: its members only, an {@code activatedKinds} of the kinds version 1
 *       gated, and no {@code mission_type} list, which version 1 never held. Its byte form is not judged, since the
 *       file is never answered or hashed as written: the upgrade is. Reading never writes; the repository's next write
 *       stores version 2 (decision {@code DM-01M6RFGMDH859CJP3RHHF9YC3H}).
 * </ul>
 *
 * <p>Every refusal is an {@link ActivationStorageException} naming the id; any other {@code schemaVersion} is one too.
 */
final class ActivationFileCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Set<String> MEMBERS = Set.of("schemaVersion", "packs", "selected", "deactivated", "languages");
    private static final Set<String> VERSION_ONE_MEMBERS =
            Set.of("schemaVersion", "packs", "activatedKinds", "missionTypes", "selected", "deactivated", "languages");
    private static final Set<String> PACK_MEMBERS = Set.of("packId", "version");
    private static final int VERSION_ONE = 1;

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
     * The document of the file's bytes: a version 2 document as stored, or a version 1 document's upgrade.
     *
     * @param id the activation the file belongs to, named in a refusal
     * @param bytes the file's bytes
     * @return the document, at version 2
     * @throws ActivationStorageException when the bytes are not a canonical version 2 document or a version 1 one
     */
    static ActivationDocument decode(ActivationId id, byte[] bytes) {
        boolean versionOne;
        ActivationDocument.Built built;
        try {
            Map<String, @Nullable Object> tree = object(MAPPER.readValue(bytes, Object.class), Set.of());
            versionOne = integer(tree.get("schemaVersion")) == VERSION_ONE;
            built = versionOne ? upgrade(tree) : build(tree);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new ActivationStorageException(id, "is not an activation document", e);
        }
        if (!built.violations().isEmpty()) {
            throw new ActivationStorageException(id, "breaks the document shape: " + built.violations());
        }
        if (!versionOne && !Arrays.equals(encode(built.document()), bytes)) {
            throw new ActivationStorageException(id, "is not in canonical form");
        }
        return built.document();
    }

    private static ActivationDocument.Built build(Map<String, @Nullable Object> tree) {
        allowed(tree, MEMBERS);
        ActivationDocument.Builder builder = ActivationDocument.builder();
        builder.schemaVersion(integer(tree.get("schemaVersion")));
        common(tree, builder);
        member(tree, "selected", s -> kindLists(s, builder::selected));
        member(tree, "deactivated", d -> kindLists(d, builder::deactivated));
        return builder.build();
    }

    /** A version 1 document at version 2: its mission types selected, its kind gate discarded. */
    private static ActivationDocument.Built upgrade(Map<String, @Nullable Object> tree) {
        allowed(tree, VERSION_ONE_MEMBERS);
        ActivationDocument.Builder builder = ActivationDocument.builder();
        common(tree, builder);
        member(tree, "activatedKinds", k -> strings(k).forEach(token -> versionOneKind(kind(token))));
        member(tree, "selected", s -> kindLists(s, (kind, ids) -> builder.selected(versionOneKind(kind), ids)));
        member(tree, "deactivated", d -> kindLists(d, (kind, ids) -> builder.deactivated(versionOneKind(kind), ids)));
        member(tree, "missionTypes", m -> builder.selected(ArtifactKind.MISSION_TYPE, strings(m)));
        return builder.build();
    }

    /** The members both versions share and read alike: {@code packs} and {@code languages}. */
    private static void common(Map<String, @Nullable Object> tree, ActivationDocument.Builder builder) {
        member(
                tree,
                "packs",
                p -> builder.packs(
                        list(p).stream().map(ActivationFileCodec::pack).toList()));
        member(tree, "languages", l -> builder.languages(strings(l)));
    }

    /** A kind version 1 gated, selected or denied: an activatable kind other than {@code mission_type}. */
    private static ArtifactKind versionOneKind(ArtifactKind kind) {
        if (!kind.activatable() || kind == ArtifactKind.MISSION_TYPE) {
            throw new IllegalArgumentException("a version 1 document holds no '" + kind.token() + "' list");
        }
        return kind;
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

    private static void allowed(Map<String, @Nullable Object> tree, Set<String> members) {
        if (!members.containsAll(tree.keySet())) {
            throw new IllegalArgumentException("unexpected members " + tree.keySet());
        }
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
        Map<String, @Nullable Object> members = new LinkedHashMap<>();
        map.forEach((name, value) -> members.put(string(name), value));
        if (!allowed.isEmpty()) {
            allowed(members, allowed);
        }
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
