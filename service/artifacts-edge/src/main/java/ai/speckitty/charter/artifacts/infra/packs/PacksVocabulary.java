package ai.speckitty.charter.artifacts.infra.packs;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts;
import ai.speckitty.charter.artifacts.application.ContextsInForce;
import ai.speckitty.charter.artifacts.application.GlossaryView;
import ai.speckitty.charter.artifacts.application.VocabularyReads;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.packs.domain.GlossaryContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@link VocabularyReads.WinningContexts} over the packs slice (research R6, NFR-005): the winning copies of the
 * glossary contexts in force under an activation, each winning pack read once, however many of its contexts are in
 * force. Each read is one verified read of the packs slice's {@link ForeignArtifactRead}, checked against the source
 * fingerprint of the activation's read as {@link Fingerprints#require} checks the skill and glossary lists, so the
 * copies are of the inputs the activation judged or the read is {@code stale_input}. Of each pack, only the contexts
 * it wins are kept: a pack also holds the copies another level replaced, and those are not in force.
 */
@Component
public final class PacksVocabulary implements VocabularyReads.WinningContexts {

    private final ForeignArtifactRead packs;

    /**
     * Reads over the packs slice's read of the artifacts another slice renders.
     *
     * @param packs the verified reads of a kind's artifacts
     */
    @Autowired
    public PacksVocabulary(ForeignArtifactRead packs) {
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    @Override
    public Map<String, GlossaryView> read(ContextsInForce inForce) {
        String fingerprint = inForce.scope().sourceFingerprint();
        Map<PackIdentifier, Set<String>> wonByPack = inForce.contexts().stream()
                .collect(Collectors.groupingBy(
                        ActivationArtifacts.Held::packId,
                        LinkedHashMap::new,
                        Collectors.mapping(ActivationArtifacts.Held::id, Collectors.toUnmodifiableSet())));
        Map<String, GlossaryView> copies = new HashMap<>();
        wonByPack.forEach((pack, won) -> {
            ConsistentRead.Verified<ForeignArtifactRead.Held> verified = packs.held(pack, ArtifactKind.GLOSSARY);
            Fingerprints.require(verified.fingerprint(), fingerprint);
            verified.value().artifacts().stream()
                    .map(GlossaryContext.class::cast)
                    .filter(context -> won.contains(context.localId()))
                    .forEach(context -> copies.put(context.localId(), PacksGlossaries.viewOf(context)));
        });
        return Map.copyOf(copies);
    }
}
