package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.missions.domain.GuardEntry;
import ai.speckitty.charter.missions.domain.Lifecycle;
import ai.speckitty.charter.missions.domain.LifecycleTransition;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.Guard;
import ai.speckitty.charter.packs.domain.GuardImplementation;
import ai.speckitty.charter.packs.domain.Pack;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The charter's load-time checks of guards and lifecycles (ADR-0054 decision 4): the one place the pack store enforces
 * them, at assembly ({@code DomainRepositoryService.assembly}) and before a whole-pack write. Each check fails closed:
 * what does not resolve is left out of the pack with a {@code reference} violation of its file, and whatever names it
 * is refused in turn.
 *
 * <ul>
 *   <li>An asset's {@code executable} entry names a payload file the pack holds under {@code assets/}, its own or
 *       another's (ADR-0054 decision 5 as amended on 2026-11-03).
 *   <li>A guard's {@code implemented_by} asset is an asset of the pack that closes and declares an {@code executable}
 *       block; a built-in needs nothing more than the schema's closed list.
 *   <li>A guard's {@code config_schema} is a Draft 2020-12 schema that compiles offline.
 *   <li>A lifecycle's guard entries name guards of the pack that resolve, and each entry's {@code config} fits its
 *       guard's {@code config_schema}; a {@code config} for a guard that declares none is refused, since nothing can
 *       check it.
 *   <li>A reference to another pack ({@code <pack>::asset:<id>}, {@code <pack>::guard:<id>}, ADR-0053 decision 7) is
 *       refused until the pack dependency closure (ADR-0048) can resolve it (work breakdown item 3).
 * </ul>
 */
final class GuardClosure {

    private static final String CROSS_PACK =
            ", a pack-qualified reference; it is refused until the pack dependency closure resolves it";

    private final Set<String> payloads;
    private final Map<String, Asset> assets;
    private final Map<String, Guard> guards;

    private GuardClosure(Collection<Asset> assets, Collection<AssetFile> files, Collection<Guard> candidates) {
        this.payloads = files.stream().map(AssetFile::path).collect(Collectors.toSet());
        Map<String, Asset> byAssetId = new LinkedHashMap<>();
        assets.stream()
                .filter(asset -> unresolved(asset).isEmpty())
                .forEach(asset -> byAssetId.putIfAbsent(asset.localId(), asset));
        this.assets = byAssetId;
        Map<String, Guard> resolved = new LinkedHashMap<>();
        for (Guard guard : candidates) {
            if (unresolved(guard).isEmpty()) {
                resolved.putIfAbsent(guard.localId(), guard);
            }
        }
        this.guards = resolved;
    }

    /**
     * The closure over what a pack read holds: its assets and guards that build, the guards kept only when they
     * resolve in turn.
     *
     * @param pack the pack the read belongs to
     * @param read what the store read
     * @return the closure
     */
    static GuardClosure of(PackIdentifier pack, FilesystemPackStore.PackRead read) {
        return new GuardClosure(
                entities(pack, read, AssetBinding.INSTANCE),
                read.assetFiles(),
                entities(pack, read, GuardBinding.INSTANCE));
    }

    /**
     * The closure over an assembled pack.
     *
     * @param pack the pack
     * @return the closure
     */
    static GuardClosure of(Pack pack) {
        return new GuardClosure(pack.assets(), pack.assetFiles(), pack.artifacts(ArtifactKind.GUARD, Guard.class));
    }

    /**
     * Why an artifact does not close over its references.
     *
     * @param artifact any artifact; only a guard or a lifecycle is checked
     * @return the reason, a sentence naming the artifact; empty when it closes or is of another kind
     */
    Optional<String> firstUnresolved(CharterArtifact artifact) {
        return switch (artifact) {
            case Asset asset -> unresolved(asset);
            case Guard guard -> unresolved(guard);
            case Lifecycle lifecycle -> unresolved(lifecycle);
            default -> Optional.empty();
        };
    }

    /**
     * Refuses a pack that holds a guard or a lifecycle that does not close, before a whole-pack write.
     *
     * @param pack the pack about to be written
     * @throws CharterRefusal with {@code VALIDATION_FAILED} naming the first that does not close
     */
    static void requireResolved(Pack pack) {
        GuardClosure closure = of(pack);
        for (CharterArtifact artifact : pack.artifacts()) {
            closure.firstUnresolved(artifact).ifPresent(reason -> {
                throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, reason);
            });
        }
    }

    /** An executable asset's entry names a payload the pack holds (ADR-0054 decision 5 as amended 2026-11-03). */
    private Optional<String> unresolved(Asset asset) {
        return asset.executable()
                .filter(executable -> !payloads.contains(executable.entry()))
                .map(executable -> "asset '" + asset.localId() + "' declares executable entry '" + executable.entry()
                        + "', which is not a payload file the pack holds under assets/");
    }

    private Optional<String> unresolved(Guard guard) {
        String named = "guard '" + guard.localId() + "'";
        if (guard.configSchema().isPresent()) {
            List<String> problems =
                    EmbeddedSchemas.schemaProblems(guard.configSchema().get());
            if (!problems.isEmpty()) {
                return Optional.of(
                        named + ": its config_schema is not a valid JSON Schema: " + String.join("; ", problems));
            }
        }
        if (!(guard.implementation() instanceof GuardImplementation.AssetBacked asset)) {
            return Optional.empty();
        }
        String implemented = named + " is implemented_by " + asset.spelling();
        if (asset.pack().isPresent()) {
            return Optional.of(implemented + CROSS_PACK);
        }
        Asset held = assets.get(asset.assetId());
        if (held == null) {
            return Optional.of(implemented + ", which the pack does not hold");
        }
        if (held.executable().isEmpty()) {
            return Optional.of(implemented + ", which declares no executable block");
        }
        return Optional.empty();
    }

    private Optional<String> unresolved(Lifecycle lifecycle) {
        for (LifecycleTransition transition : lifecycle.transitions()) {
            String where = "lifecycle '" + lifecycle.localId() + "' transition " + transition.describe();
            for (GuardEntry entry : transition.guards()) {
                if (entry.ref().crossPack()) {
                    return Optional.of(where + " names guard " + entry.ref().spelling() + CROSS_PACK);
                }
                Guard guard = guards.get(entry.ref().id());
                if (guard == null) {
                    return Optional.of(where + " names guard '" + entry.ref().id() + "', which the pack does not hold");
                }
                Optional<String> config = configProblem(guard, entry);
                if (config.isPresent()) {
                    return Optional.of(where + ": " + config.get());
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> configProblem(Guard guard, GuardEntry entry) {
        String named = "guard '" + guard.localId() + "'";
        if (guard.configSchema().isEmpty()) {
            return entry.config().isPresent()
                    ? Optional.of(named + " declares no config_schema, so its config cannot be checked")
                    : Optional.empty();
        }
        List<String> problems =
                EmbeddedSchemas.valueProblems(guard.configSchema().get(), entry.config());
        return problems.isEmpty()
                ? Optional.empty()
                : Optional.of(
                        "the config of " + named + " does not fit its config_schema: " + String.join("; ", problems));
    }

    private static <D, E extends CharterArtifact> List<E> entities(
            PackIdentifier pack, FilesystemPackStore.PackRead read, KindBinding<D, E> kind) {
        return read.elements(kind).stream()
                .map(stored -> {
                    try {
                        return Optional.of(kind.toEntity(pack, stored.dso()));
                    } catch (ConstraintBreachException | IllegalArgumentException refused) {
                        return Optional.<E>empty();
                    }
                })
                .flatMap(Optional::stream)
                .toList();
    }
}
