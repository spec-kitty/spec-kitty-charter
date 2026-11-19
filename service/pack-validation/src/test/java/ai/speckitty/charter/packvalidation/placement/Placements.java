package ai.speckitty.charter.packvalidation.placement;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import java.util.List;
import java.util.Optional;

/** Test fixtures: a placement of a pack named {@code qa}, its descriptor and fragment written by hand. */
final class Placements {

    static final FileRead<RawGraph> FRAGMENT = new FileRead.Read<>(
            "drg/fragment.yaml", new RawGraph("drg/fragment.yaml", RawGraph.Shape.FRAGMENT, List.of(), List.of()));

    static final FileRead<RawGraph> NO_FRAGMENT = new FileRead.Absent<>("drg/fragment.yaml");

    static final FileRead<RawGraph> BROKEN_FRAGMENT = new FileRead.Unreadable<>(
            "drg/fragment.yaml", new ParseFailure("drg/fragment.yaml", "while parsing"), List.of());

    static final FileRead<PackDescriptor> NO_DESCRIPTOR = new FileRead.Absent<>("pack.yaml");

    static final FileRead<PackDescriptor> BROKEN_DESCRIPTOR =
            new FileRead.Unreadable<>("pack.yaml", new ParseFailure("pack.yaml", "while parsing"), List.of());

    private Placements() {}

    static FileRead<PackDescriptor> declaring(Optional<PackLevel> level) {
        return new FileRead.Read<>(
                "pack.yaml",
                new PackDescriptor(
                        "01ARWG13C00000000000001ABC", "1.0.0", Optional.empty(), "qa", level, Optional.empty()));
    }

    static Placement of(
            Optional<PackLevel> configured, FileRead<PackDescriptor> descriptor, FileRead<RawGraph> fragment) {
        PackContents contents = new PackContents(
                new PackRoot("qa", configured, "loc"),
                descriptor,
                List.of(),
                fragment,
                List.of(),
                List.of(),
                StepEnhancementFacts.none(),
                List.of(),
                List.of(),
                List.of());
        // Hand-written facts carry no assembly input: the one reader answers such a read pack_unreadable.
        PackReadout.Unreadable facts = new PackReadout.Unreadable(
                RefusalCode.PACK_UNREADABLE, "the pack qa cannot be read: it has no assembly");
        return new Placement(
                new PackReadout(contents, List.of(), List.of(), new Rationale(List.of()), facts), configured);
    }
}
