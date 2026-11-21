package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.ImpactCheck;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The removal of a registered pack whole, {@code DELETE /packs/{packId}} (ADR-0081 D1, D5 to D9): the pack is removed,
 * or nothing changes. Everything runs under the registry lock and then the pack's own, in this order: the pack is
 * resolved by its configured name or its declared {@code pack_id} ({@code 404 pack_not_found}), {@code If-Match} is
 * required ({@code 428}), the pack must be a removable org or project pack ({@code 409 registration_conflict}), the
 * {@code If-Match} must name its revision ({@code 412}, {@code *} passes), nothing may depend on it
 * ({@code 409 pack_has_dependents}), and every other closure that holds it must compose without it ({@code 422}). A dry
 * run judges the same steps on the same session and stops before the removal. A pack whose own {@code pack.yaml} cannot
 * be read is still removed: it is judged by its configured name, and any other pack the judgement needs fails it
 * closed ({@code 500 pack_unreadable}).
 */
@Service
public final class PackUninstallService {

    /**
     * What a removal did, or would do.
     *
     * @param pack the configured name of the pack
     * @param version the {@code pack_version} of the stored pack; empty when its {@code pack.yaml} cannot be read
     * @param revision the revision of the stored pack, the one the {@code If-Match} matched
     * @param notices what the removal takes off the floor and the locks, and the activations whose in-force set changes;
     *     none when it is refused
     * @param dryRun whether the removal was only judged
     * @param refused the refusal a dry run judged the removal to meet: the {@code 409 pack_has_dependents} or a
     *     {@code 422} of the compose, the very refusal the removal answers; empty when the removal would succeed, and
     *     always empty when the pack was removed
     */
    public record Outcome(
            PackIdentifier pack,
            Optional<String> version,
            PackRevision revision,
            ImpactCheck.Notices notices,
            boolean dryRun,
            Optional<CharterRefusal> refused) {

        /**
         * Checks the fields.
         *
         * @param pack the pack
         * @param version its version
         * @param revision its revision
         * @param notices the notices
         * @param dryRun whether it was only judged
         * @param refused the refusal a dry run met
         */
        public Outcome {
            Objects.requireNonNull(refused, "refused");
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(notices, "notices");
        }
    }

    private final PackRemovalStore store;
    private final WholePackWriteStore packs;
    private final WholePackWriteStore.Loader loader;
    private final StagedPackViews views;
    private final ClosureImpactCheck impact;

    /**
     * A removal over its ports.
     *
     * @param store the locks, the revision and the removal of a pack
     * @param packs the live directory of a stored pack
     * @param loader reads what a stored pack holds, or what can still be read of it
     * @param views the stored packs, read through one session per removal
     * @param impact the impact check: the dependents, then the compose of the closures left (#556, D3)
     */
    @Autowired
    public PackUninstallService(
            PackRemovalStore store,
            WholePackWriteStore packs,
            WholePackWriteStore.Loader loader,
            StagedPackViews views,
            ClosureImpactCheck impact) {
        this.store = Objects.requireNonNull(store, "store");
        this.packs = Objects.requireNonNull(packs, "packs");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.views = Objects.requireNonNull(views, "views");
        this.impact = Objects.requireNonNull(impact, "impact");
    }

    /**
     * Removes a pack whole, or judges its removal.
     *
     * @param pack the pack, by its configured name or its declared {@code pack_id}
     * @param ifMatch the {@code If-Match} field value, or null when the request carried none
     * @param mode whether the pack is removed or the removal only judged
     * @param trail told the stored revision once it is read, and the move when the pack was removed
     * @return what was done, or would be done
     * @throws CharterRefusal {@code pack_not_found}, {@code registration_conflict}, {@code pack_has_dependents},
     *     {@code pack_unreadable} or a composition code
     * @throws PreconditionException {@code MISSING} (428) or {@code STALE} (412)
     * @throws RegistryWriteLock.RegistryBusyException when the registry lock was not free in time (503)
     */
    public Outcome uninstall(
            PackIdentifier pack, @Nullable String ifMatch, PackRegistrationService.Mode mode, RevisionTrail trail) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(trail, "trail");
        return store.exclusively(pack, () -> judgeAndRemove(store.configuredName(pack), ifMatch, mode, trail));
    }

    private Outcome judgeAndRemove(
            PackIdentifier name, @Nullable String ifMatch, PackRegistrationService.Mode mode, RevisionTrail trail) {
        if (ifMatch == null) {
            throw new PreconditionException(
                    PreconditionException.Kind.MISSING,
                    "A removal requires an If-Match precondition on the pack revision.");
        }
        StagedPackViews.Session session = views.session();
        StagedPackViews.View stored = session.stored();
        ConfiguredPack configured = configuredOf(stored, name);
        if (configured.level() == PackLevel.BUILT_IN) {
            throw new CharterRefusal(
                    RefusalCode.REGISTRATION_CONFLICT,
                    "A built-in pack is read-only; only org and project packs are removed.");
        }
        store.requireRemovable(name);
        PackRevision revision = RemovalPrecondition.require(store, name, ifMatch, trail);
        Optional<PackDescriptor> descriptor = descriptorOf(stored, name);
        Optional<String> version = descriptor.map(PackDescriptor::packVersion);
        boolean dryRun = mode == PackRegistrationService.Mode.DRY_RUN;
        ImpactCheck.Notices notices;
        try {
            notices = judge(session, configured, descriptor, name);
        } catch (CharterRefusal refused) {
            if (!dryRun || !isJudgement(refused)) {
                throw refused;
            }
            return new Outcome(name, version, revision, ImpactCheck.Notices.NONE, true, Optional.of(refused));
        }
        if (!dryRun) {
            store.removeWhole(name);
        }
        return new Outcome(name, version, revision, notices, dryRun, Optional.empty());
    }

    /**
     * The dependents, then the closures left: the judgement a dry run answers as a body (ADR-0081 D5, P6), made by the
     * impact check in this removal's own session (#556, D3, squad A3), so each stored pack is read once per delete.
     */
    private ImpactCheck.Notices judge(
            StagedPackViews.Session session,
            ConfiguredPack configured,
            Optional<PackDescriptor> descriptor,
            PackIdentifier name) {
        return PackRefusals.noticesOrRefuse(
                impact.judge(session, new ImpactCheck.Removal(configured, descriptor, heldBy(name))));
    }

    /**
     * Whether a refusal is the removal's judgement, which a dry run answers as a body: the dependents and the compose. A
     * pack that cannot be read is no judgement and keeps its status; a failure of the service is no refusal at all.
     */
    private static boolean isJudgement(CharterRefusal refused) {
        return refused.code() != RefusalCode.PACK_UNREADABLE;
    }

    private static ConfiguredPack configuredOf(StagedPackViews.View stored, PackIdentifier name) {
        return stored.catalog().configured().stream()
                .filter(candidate -> candidate.id().equals(name))
                .findFirst()
                .orElseThrow(() ->
                        new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + name.value() + " was not found."));
    }

    /**
     * The removed pack's descriptor, or empty when its own {@code pack.yaml} cannot be read (ADR-0081 D6): the pack is
     * then judged by its configured name, and the removal is how the operator clears it.
     */
    private static Optional<PackDescriptor> descriptorOf(StagedPackViews.View stored, PackIdentifier name) {
        try {
            return Optional.of(stored.descriptors().get(name));
        } catch (CharterRefusal unreadable) {
            if (unreadable.code() != RefusalCode.PACK_UNREADABLE) {
                throw unreadable;
            }
            return Optional.empty();
        }
    }

    /**
     * Every artifact and term the pack holds, as URNs: what another pack's artifact may refer to. A pack that cannot be
     * loaded whole is read for what can still be read of it, as a replace reads it.
     */
    private Set<Urn> heldBy(PackIdentifier name) {
        Path directory = packs.liveDirectory(name);
        Set<Urn> held = new LinkedHashSet<>();
        try {
            WholePackWriteStore.Contents contents = loader.loadStored(name, directory);
            PackCompositionFacts facts = contents.facts();
            facts.defined().forEach(urn -> Urn.parse(urn).ifPresent(held::add));
            facts.terms().forEach(term -> Urn.parse(term).ifPresent(held::add));
            held.addAll(contents.digests().keySet());
        } catch (CharterRefusal unreadable) {
            WholePackWriteStore.Remains remains = loader.salvageStored(name, directory);
            remains.defined().forEach(urn -> Urn.parse(urn).ifPresent(held::add));
            held.addAll(remains.digests().keySet());
        }
        return Set.copyOf(held);
    }
}
