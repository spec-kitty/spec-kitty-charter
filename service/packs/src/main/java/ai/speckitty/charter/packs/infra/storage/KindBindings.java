package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.List;
import java.util.Optional;

/**
 * The registry of stored kinds (ADR-0036): the one list of the kinds the store loads, writes and the source
 * fingerprint digests. A kind joins by adding its {@link KindBinding} here and its format to {@link YamlFormats}; no
 * other storage class names a kind.
 *
 * <p>The order is the load and digest order. New kinds are appended, so the digest of a pack that holds none of them
 * does not change.
 */
final class KindBindings {

    private static final List<KindBinding<?, ?>> ALL = List.of(
            TacticBinding.INSTANCE,
            DirectiveBinding.INSTANCE,
            StyleguideBinding.INSTANCE,
            ToolguideBinding.INSTANCE,
            ParadigmBinding.INSTANCE);

    private KindBindings() {}

    /**
     * Every bound kind, in load and digest order.
     *
     * @return an immutable list
     */
    static List<KindBinding<?, ?>> all() {
        return ALL;
    }

    /**
     * The bound kinds, in load and digest order.
     *
     * @return an immutable list
     */
    static List<ArtifactKind> kinds() {
        return ALL.stream().<ArtifactKind>map(KindBinding::kind).toList();
    }

    /**
     * The binding of a kind.
     *
     * @param kind the kind
     * @return the binding; empty when the kind is not stored
     */
    static Optional<KindBinding<?, ?>> of(ArtifactKind kind) {
        return ALL.stream().filter(binding -> binding.kind() == kind).findFirst();
    }
}
