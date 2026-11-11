package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolves which pack of a compose closure holds a reference, as an artifact writes it — bare or pack-qualified —
 * against the closure the caller resolved once for its read ({@link #over(List)}). The references read and
 * {@link ComposedRelationshipTargetCheck} ask it; graph walks do not, they run over an {@link EdgeIndex}. The pack
 * composition is handed in read-only and never reached into. This service is component-scanned; the composition root
 * needs no {@code @Bean}.
 */
@Service
public final class CompositeComponentResolver {

    /** A resolver with no state: the closure it judges against is handed in per read. */
    public CompositeComponentResolver() {
        // stateless
    }

    /**
     * The packs of one resolved compose, to name the holder of a reference in. The closure is the one the caller
     * resolved for the read it serves, so a read resolves the compose once, never once per reference.
     *
     * @param closure the compose's closure, as the offering resolved it
     * @return the compose view over those packs
     */
    public Composed over(List<PackCompositionResolver.PackUnderResolution> closure) {
        return new Composed(List.copyOf(Objects.requireNonNull(closure, "closure")));
    }

    /** The compose a read resolved once: the pack that holds a reference. */
    public static final class Composed {

        private final List<PackCompositionResolver.PackUnderResolution> closure;

        private Composed(List<PackCompositionResolver.PackUnderResolution> closure) {
            this.closure = closure;
        }

        /**
         * The pack of the compose that holds what a reference names, as an artifact writes it (FR-013, FR-014, FR-015,
         * FR-017), by the compose's one rule ({@link PackCompositionResolver#holderOf}) and judged by its one check
         * ({@link PackCompositionResolver#checkReferences}). A qualified reference is held by the pack
         * it names; a name the compose does not hold is refused {@link RefusalCode#PACK_REFERENCE_PACK_MISSING}, naming
         * the missing pack, and never falls back to the referring pack. A bare one is held by the referring pack when
         * it holds the artifact, which shadows a same-named artifact of another pack (FR-014); when only another pack
         * holds it the reference is refused {@link RefusalCode#PACK_REFERENCE_UNQUALIFIED}, naming the holding pack, so
         * it cannot resolve by load order. When no pack holds it nothing holds it.
         *
         * <p>Both verdicts are the compose resolver's, so the reference codes travel the same path the other compose
         * refusals travel. The offering already checked every reference its packs write when it resolved the closure,
         * so here the check is a defensive assertion for a closure that did not come from it.
         *
         * @param parsed the reference as the artifact writes it
         * @param referringPack the pack whose artifact writes it
         * @return the holding pack; empty when no pack of the compose holds what a bare reference names
         * @throws CharterRefusal with {@link RefusalCode#PACK_REFERENCE_PACK_MISSING} when a qualified reference names
         *     a pack outside the referring pack's {@code depends_on} closure, or with {@link RefusalCode#PACK_REFERENCE_UNQUALIFIED} when a bare reference
         *     resolves only in another pack of the compose
         */
        public Optional<PackCompositionResolver.PackUnderResolution> holder(
                PackQualifiedReference parsed, PackIdentifier referringPack) {
            return holderIn(closure, parsed, referringPack);
        }
    }

    /**
     * The holder of a reference in one resolved compose, judged by the compose's one rule and one check: what
     * {@link Composed#holder} answers, for a caller that holds the closure and no resolver.
     *
     * @param closure the compose's closure, as the offering resolved it
     * @param parsed the reference as the artifact writes it
     * @param referringPack the pack whose artifact writes it
     * @return the holding pack; empty when no pack of the compose holds what a bare reference names
     * @throws CharterRefusal as {@link Composed#holder} does
     */
    static Optional<PackCompositionResolver.PackUnderResolution> holderIn(
            List<PackCompositionResolver.PackUnderResolution> closure,
            PackQualifiedReference parsed,
            PackIdentifier referringPack) {
        Objects.requireNonNull(parsed, "parsed");
        Objects.requireNonNull(referringPack, "referringPack");
        Optional<PackCompositionResolver.PackUnderResolution> holder =
                PackCompositionResolver.holderOf(closure, referringPack, parsed);
        String referringId = closure.stream()
                .filter(pack -> pack.configured().id().equals(referringPack))
                .map(pack -> pack.descriptor().packId())
                .findFirst()
                .orElse(referringPack.value());
        checked(
                closure,
                referringId,
                new PackCompositionResolver.ResolvedReference(
                        parsed, holder.map(pack -> pack.descriptor().packId())));
        return holder;
    }

    /** The closure check of the compose resolver, its refusal mapped to the reference code the contract serves. */
    private static void checked(
            List<PackCompositionResolver.PackUnderResolution> closure,
            String referringPackId,
            PackCompositionResolver.ResolvedReference resolved) {
        try {
            PackCompositionResolver.checkReferences(closure, referringPackId, List.of(resolved));
        } catch (PackCompositionResolver.Refusal refusal) {
            throw refusal.toCharterRefusal();
        }
    }
}
