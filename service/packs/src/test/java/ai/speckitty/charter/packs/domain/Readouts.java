package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.rules.DescriptorChecks;
import ai.speckitty.charter.packs.domain.rules.Wording;
import java.util.ArrayList;
import java.util.List;

/**
 * Test fixtures: the readout the one reader would give for a pack whose facts a test writes by hand (#554, S6). Its
 * read faults are worded as the reader words them ({@link Wording}): the descriptor's (its read, or its format
 * prechecks), each graph document's, then each other file the test names as unreadable.
 */
public final class Readouts {

    private Readouts() {}

    /**
     * The readout of hand-written facts, with no other unreadable file, no classified file and no file not held.
     *
     * @param contents the facts
     * @return the readout
     */
    public static PackReadout of(PackContents contents) {
        return of(contents, List.of(), List.of(), List.of());
    }

    /**
     * The readout of hand-written facts.
     *
     * @param contents the facts
     * @param files the classified files
     * @param unreadable the other files the read refused, in read order
     * @param notHeld the files not held
     * @return the readout
     */
    public static PackReadout of(
            PackContents contents,
            List<ClassifiedFile> files,
            List<FileRead.Unreadable<?>> unreadable,
            List<NotHeldFile> notHeld) {
        String name = contents.root().name();
        List<PackFinding> faults = new ArrayList<>();
        switch (contents.descriptor()) {
            case FileRead.Unreadable<PackDescriptor> bad -> faults.addAll(Wording.unreadable(name, bad));
            case FileRead.Read<PackDescriptor> good -> faults.addAll(DescriptorChecks.findings(name, good.value()));
            case FileRead.Absent<PackDescriptor> absent -> {}
        }
        List<FileRead<RawGraph>> documents = new ArrayList<>(contents.shards());
        documents.add(contents.fragment());
        for (FileRead<RawGraph> document : documents) {
            if (document instanceof FileRead.Unreadable<RawGraph> bad) {
                faults.addAll(Wording.unreadable(name, bad));
            }
        }
        unreadable.forEach(bad -> faults.addAll(Wording.unreadable(name, bad)));
        List<Reason> reasons = faults.stream().map(PackFinding::reason).toList();
        return new PackReadout(contents, files, notHeld, new Rationale(reasons));
    }
}
