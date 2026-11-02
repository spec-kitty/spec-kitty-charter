package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.Guard;
import ai.speckitty.charter.packs.domain.GuardIdentifier;
import ai.speckitty.charter.packs.domain.GuardImplementation;
import ai.speckitty.charter.packs.domain.GuardLimits;
import ai.speckitty.charter.packs.domain.OnIndeterminate;

/**
 * The {@link KindBinding} of the guard (ADR-0054): {@link GuardDso} to {@link Guard} and back. A guard lives at
 * {@code guards/<id>.guard.yaml}; a file anywhere else is refused by placement. Whether its {@code implemented_by}
 * resolves is checked when the pack is assembled ({@link GuardClosure}), where the pack's assets are known.
 */
enum GuardBinding implements KindBinding<GuardDso, Guard> {
    /** The binding. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.GUARD;
    }

    @Override
    public Class<Guard> entityType() {
        return Guard.class;
    }

    @Override
    public Class<GuardDso> dsoType() {
        return GuardDso.class;
    }

    @Override
    public String idOf(GuardDso dso) {
        return dso.id();
    }

    /** A guard's place: {@code guards/<id>.guard.yaml}. */
    @Override
    public String fileName(String elementId) {
        return "guards/" + elementId + ".guard.yaml";
    }

    @Override
    public boolean idIsFileStem() {
        return true;
    }

    @Override
    public boolean placedAtItsFileName() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException also when {@code implemented_by} or {@code on_indeterminate} is not a spelling
     *     the domain knows
     */
    @Override
    public Guard toEntity(PackIdentifier pack, GuardDso dso) {
        return new Guard(
                new GuardIdentifier(dso.id()),
                pack,
                dso.schemaVersion(),
                dso.title(),
                dso.description(),
                dso.interfaceName(),
                GuardImplementation.parse(dso.implementedBy()),
                dso.configSchema(),
                dso.limits().map(l -> new GuardLimits(l.timeoutSeconds(), l.network())),
                dso.onIndeterminate()
                        .map(token -> OnIndeterminate.fromToken(token)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "on_indeterminate '" + token + "' is neither block nor pass"))));
    }

    @Override
    public GuardDso toDso(Guard guard) {
        return new GuardDso(
                guard.localId(),
                guard.schemaVersion().orElseThrow(),
                guard.title(),
                guard.description(),
                guard.interfaceName(),
                guard.implementation().spelling(),
                guard.configSchema(),
                guard.limits().map(l -> new GuardDso.Limits(l.timeoutSeconds(), l.network())),
                guard.onIndeterminate().map(OnIndeterminate::token));
    }
}
