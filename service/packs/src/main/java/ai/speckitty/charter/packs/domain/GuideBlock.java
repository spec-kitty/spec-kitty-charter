package ai.speckitty.charter.packs.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * The fixed blocks of a toolguide's {@link Guide} (#194, research D5), in write order. Each is an H2 section with a
 * canonical heading. The freeform {@linkplain GuideSection sections} sit between {@link #USAGE} and {@link #CAUTIONS}:
 * the blocks before them {@linkplain #leading() lead}, the others trail. Only {@link #OVERVIEW} is required.
 */
public enum GuideBlock {
    /** What the tool or practice is, its scope, and what the guide does not cover. Required. */
    OVERVIEW("Overview"),
    /** When to reach for it. */
    WHEN_TO_USE("When to use"),
    /** What must be installed, resolved, authenticated or configured before first use. */
    SETUP("Setup"),
    /** How to invoke or operate it. */
    USAGE("Usage"),
    /** Things to avoid, failure modes, false positives. */
    CAUTIONS("Cautions"),
    /** What the tool or the guide does not handle. */
    KNOWN_LIMITATIONS("Known limitations"),
    /** External references, as a Markdown list of links. */
    REFERENCES("References");

    private final String heading;

    GuideBlock(String heading) {
        this.heading = heading;
    }

    /**
     * The block's canonical H2 heading text, exactly as it is written.
     *
     * @return the heading
     */
    public String heading() {
        return heading;
    }

    /**
     * Whether a guide must hold the block.
     *
     * @return {@code true} only for {@link #OVERVIEW}
     */
    public boolean required() {
        return this == OVERVIEW;
    }

    /**
     * Whether the block comes before the freeform sections.
     *
     * @return {@code true} for {@link #OVERVIEW} to {@link #USAGE}, {@code false} for the trailing blocks
     */
    public boolean leading() {
        return compareTo(CAUTIONS) < 0;
    }

    /**
     * The block whose canonical heading is exactly {@code heading}.
     *
     * @param heading an H2 heading text
     * @return the block, or empty when the heading is not one exactly
     */
    public static Optional<GuideBlock> byHeading(String heading) {
        for (GuideBlock block : values()) {
            if (block.heading.equals(heading)) {
                return Optional.of(block);
            }
        }
        return Optional.empty();
    }

    /**
     * The block whose canonical heading equals {@code heading} with letter case ignored: a heading a freeform section
     * may not take.
     *
     * @param heading an H2 heading text
     * @return the block, or empty when the heading is free
     */
    public static Optional<GuideBlock> reserving(String heading) {
        String folded = heading.toLowerCase(Locale.ROOT);
        for (GuideBlock block : values()) {
            if (block.heading.toLowerCase(Locale.ROOT).equals(folded)) {
                return Optional.of(block);
            }
        }
        return Optional.empty();
    }
}
