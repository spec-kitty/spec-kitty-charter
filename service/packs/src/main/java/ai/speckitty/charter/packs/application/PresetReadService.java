package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Preset;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The pack-scoped reads of a pack's activation presets (#98, ADR-0074 decision 1): the list and one preset, each with
 * the pack revision it was read at, inside one {@link ConsistentRead#read verified read}, so the {@code ETag} names the
 * state the body came from and the answer carries the whole-source fingerprint that read verified.
 *
 * <p>A preset is a pack-level value, not an artifact ({@link Preset}), so the read asks nothing of the artifact kind
 * table: it answers {@link ai.speckitty.charter.packs.domain.Pack#presets()}, which holds them in id order. The pack is
 * judged first, as {@link ForeignArtifactRead} judges it: an identifier no configured pack answers to is {@link
 * RefusalCode#PACK_NOT_FOUND}, and a configured pack without a readable {@code pack.yaml} is {@link
 * RefusalCode#PACK_UNREADABLE}. A preset the pack does not hold is {@link RefusalCode#PRESET_NOT_FOUND}, one only
 * another pack holds included. A change of the inputs during the read is {@link RefusalCode#STALE_INPUT}.
 */
@Service
public final class PresetReadService {

    private static final String STALE = "The inputs changed while the presets were read; read them again.";

    private final PackRepository packs;
    private final PackDescriptorRepository descriptors;
    private final ConsistentRead read;
    private final PackRevisions revisions;

    /**
     * Every preset of a pack, with the revision they were read at.
     *
     * @param presets the pack's presets, in id order
     * @param revision the pack revision, the answer's {@code ETag}
     */
    public record Listed(List<Preset> presets, PackRevision revision) {

        /**
         * Checks and copies the fields.
         *
         * @param presets the presets
         * @param revision the revision
         */
        public Listed {
            presets = List.copyOf(Objects.requireNonNull(presets, "presets"));
            Objects.requireNonNull(revision, "revision");
        }
    }

    /**
     * One preset of a pack, with the revision it was read at.
     *
     * @param preset the preset
     * @param revision the pack revision, the answer's {@code ETag}
     */
    public record Held(Preset preset, PackRevision revision) {

        /**
         * Checks the fields.
         *
         * @param preset the preset
         * @param revision the revision
         */
        public Held {
            Objects.requireNonNull(preset, "preset");
            Objects.requireNonNull(revision, "revision");
        }
    }

    /**
     * A read over the pack ports.
     *
     * @param packs the port that loads a pack aggregate
     * @param descriptors the port that reads a pack's descriptor, judged before the presets are looked up
     * @param read the verifier that digests the inputs before and after the read
     * @param revisions the pack revision, read inside the verified read
     */
    @Autowired
    public PresetReadService(
            PackRepository packs, PackDescriptorRepository descriptors, ConsistentRead read, PackRevisions revisions) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.read = Objects.requireNonNull(read, "read");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
    }

    /**
     * Every preset the pack holds.
     *
     * @param pack the pack's identity, as the request names it
     * @return the presets in id order and the revision, with the whole-source fingerprint the read verified
     * @throws CharterRefusal with {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier, with
     *     {@link RefusalCode#PACK_UNREADABLE} when the configured pack has no readable {@code pack.yaml} or an input
     *     cannot be read, or with {@link RefusalCode#STALE_INPUT} when the inputs changed during the read
     */
    public ConsistentRead.Verified<Listed> list(PackIdentifier pack) {
        Objects.requireNonNull(pack, "pack");
        return read.read(STALE, () -> new Listed(presetsOf(pack), revisions.currentRevision(pack)));
    }

    /**
     * One preset the pack holds.
     *
     * @param pack the pack's identity, as the request names it
     * @param presetId the preset's id
     * @return the preset and the revision, with the whole-source fingerprint the read verified
     * @throws CharterRefusal as {@link #list}, and with {@link RefusalCode#PRESET_NOT_FOUND} when the pack holds no such
     *     preset
     */
    public ConsistentRead.Verified<Held> read(PackIdentifier pack, String presetId) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(presetId, "presetId");
        return read.read(STALE, () -> {
            Preset preset = presetsOf(pack).stream()
                    .filter(candidate -> candidate.id().equals(presetId))
                    .findFirst()
                    .orElseThrow(() -> new CharterRefusal(
                            RefusalCode.PRESET_NOT_FOUND,
                            "Pack " + pack.value() + " holds no preset " + presetId + "."));
            return new Held(preset, revisions.currentRevision(pack));
        });
    }

    /** The pack is judged first: its descriptor, then its presets. */
    private List<Preset> presetsOf(PackIdentifier pack) {
        if (descriptors.find(pack).isEmpty()) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + pack.value() + " has no pack.yaml");
        }
        return packs.load(pack).presets();
    }
}
