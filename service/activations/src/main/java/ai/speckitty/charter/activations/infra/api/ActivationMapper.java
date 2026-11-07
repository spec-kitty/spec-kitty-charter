package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.api.model.Activation;
import ai.speckitty.charter.api.model.ActivationConfig;
import ai.speckitty.charter.api.model.ActivationPackList;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.OfferingPack;
import ai.speckitty.charter.api.model.PackPin;
import ai.speckitty.charter.api.model.ResolvedDependency;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps the activation set's wire shapes to the activation domain and back.
 *
 * <ul>
 *   <li>A request's {@link ActivationConfig} becomes an {@link ActivationDocument.Built}: the document and its shape
 *       violations. An absent list and {@code []} stay apart: the generated DTO leaves an absent list {@code null}
 *       ({@code containerDefaultToNull}), and only a {@code null} is left unset on the builder.
 *   <li>A stored document becomes its {@link ActivationConfig} in canonical form, through
 *       {@link ActivationDocument#toCanonicalTree()}, whose members and spellings are the schema's own.
 *   <li>A closure becomes an {@link ActivationPackList} of {@link OfferingPack}s, each with the {@code artifacts} link
 *       the offering gives it, built here the same way: activations may not name the packs adapter's link builder.
 * </ul>
 */
@Component
public class ActivationMapper {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, @Nullable List<String>>> KIND_LISTS = new TypeReference<>() {};

    private final String basePath;

    /**
     * A mapper whose links start at the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    public ActivationMapper(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * The document a request body states.
     *
     * @param config the body, bound and bean-validated
     * @return the document and its shape violations
     */
    public ActivationDocument.Built toDocument(ActivationConfig config) {
        ActivationDocument.Builder builder = ActivationDocument.builder().schemaVersion(config.getSchemaVersion());
        ifPresent(
                config.getPacks(),
                packs -> builder.packs(
                        packs.stream().map(ActivationMapper::packRef).toList()));
        ifPresent(config.getLanguages(), builder::languages);
        kindLists(config.getSelected()).forEach((kind, ids) -> {
            if (kind == ArtifactKind.MISSION_TYPE) {
                builder.missionTypes(ids);
            } else {
                builder.selected(kind, ids);
            }
        });
        kindLists(config.getDeactivated()).forEach(builder::deactivated);
        return builder.build();
    }

    /**
     * The canonical config of a document.
     *
     * @param document the document
     * @return its config
     */
    public ActivationConfig toConfig(ActivationDocument document) {
        Map<String, Object> tree = new LinkedHashMap<>(document.toCanonicalTree());
        tree.remove("activatedKinds");
        if (tree.remove("missionTypes") instanceof List<?> missionTypes) {
            Map<String, Object> selected = new LinkedHashMap<>();
            if (tree.get("selected") instanceof Map<?, ?> lists) {
                lists.forEach((kind, ids) -> selected.put(String.valueOf(kind), ids));
            }
            selected.put(ArtifactKind.MISSION_TYPE.token(), missionTypes);
            tree.put("selected", selected);
        }
        return JSON.convertValue(tree, ActivationConfig.class);
    }

    /**
     * The answer for a stored activation, its {@code self} link included.
     *
     * @param stored the activation
     * @return the answer
     */
    public Activation toActivation(StoredActivation stored) {
        String id = stored.id().value();
        return new Activation(
                id, stored.revision().token(), toConfig(stored.document()), Map.of("self", link(self(id))));
    }

    /**
     * The path of an activation, which its {@code self} link and the {@code Location} of its creation name.
     *
     * @param activationId the activation's id
     * @return the absolute path
     */
    public String self(String activationId) {
        return href("activations", activationId);
    }

    /**
     * The answer of the closure read.
     *
     * @param activationId the activation's id
     * @param snapshot its closure
     * @return the packs in dependency order, with the read's {@code self} link
     */
    public ActivationPackList toPackList(String activationId, ClosureSnapshot snapshot) {
        List<OfferingPack> packs =
                snapshot.packs().stream().map(this::offeringPack).toList();
        return new ActivationPackList(packs, Map.of("self", link(href("activations", activationId, "packs"))));
    }

    private OfferingPack offeringPack(ClosureSnapshot.PackEntry entry) {
        return new OfferingPack(
                entry.packId(),
                entry.descriptorPackId(),
                OfferingPack.LevelEnum.fromValue(entry.level()),
                entry.name(),
                entry.version(),
                entry.dependsOn().stream()
                        .map(edge -> new ResolvedDependency(edge.descriptorPackId(), edge.range(), edge.version()))
                        .toList(),
                Map.of("artifacts", link(href("packs", entry.packId(), "artifacts"))));
    }

    private static PackRef packRef(PackPin pin) {
        return new PackRef(pin.getPackId(), Optional.ofNullable(pin.getVersion()));
    }

    private static ArtifactKind kindOf(String token) {
        return ArtifactKind.fromToken(token)
                .orElseThrow(() -> new IllegalArgumentException("The contract names no kind '" + token + "'."));
    }

    /** The lists of a {@code selected} or {@code deactivated} member, by kind; a {@code null} list is left out. */
    private static Map<ArtifactKind, List<String>> kindLists(@Nullable Object member) {
        if (member == null) {
            return Map.of();
        }
        Map<ArtifactKind, List<String>> lists = new LinkedHashMap<>();
        JSON.convertValue(member, KIND_LISTS).forEach((token, ids) -> {
            if (ids != null) {
                lists.put(kindOf(token), ids);
            }
        });
        return lists;
    }

    private static <T> void ifPresent(@Nullable T value, Consumer<T> use) {
        if (value != null) {
            use.accept(value);
        }
    }

    private String href(String... segments) {
        return basePath
                + Arrays.stream(segments)
                        .map(segment -> "/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                        .collect(Collectors.joining());
    }

    private static Link link(String href) {
        return new Link(href);
    }
}
