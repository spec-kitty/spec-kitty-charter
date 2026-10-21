package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.packs.domain.PackContents.ClassifiedFile;
import java.util.List;

/**
 * What {@link PackPolicy} concludes about a pack.
 *
 * @param files the classified files of the pack; a YAML file no rule classifies is left out, as core leaves it out
 *     of its verdict (#115)
 * @param findings every finding, in precedence order
 */
public record PackVerdict(List<ClassifiedFile> files, List<PackFinding> findings) {
    /**
     * Copies the lists.
     *
     * @param files the classified files
     * @param findings the findings, in precedence order
     */
    public PackVerdict {
        files = List.copyOf(files);
        findings = List.copyOf(findings);
    }
}
