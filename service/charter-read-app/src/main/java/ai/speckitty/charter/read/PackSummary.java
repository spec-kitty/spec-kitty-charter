package ai.speckitty.charter.read;

import ai.speckitty.charter.domain.Diagnostic;
import ai.speckitty.charter.domain.PackLevel;
import java.util.List;
import java.util.Optional;

/** One loaded pack and the size of its graph. */
public record PackSummary(String name, PackLevel level, Optional<String> packId, Optional<String> packVersion,
                          Optional<String> parentPack, int nodeCount, int edgeCount,
                          List<Diagnostic> diagnostics) {
}
