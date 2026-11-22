package ai.speckitty.charter.packdoctor.report;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where {@code --report} writes the report, and in which format (FR-008).
 *
 * @param path the file, as given; written whole, replacing any file there
 * @param format its format, {@link ReportFormat#YAML} unless {@code --format} names another
 */
public record ReportFile(Path path, ReportFormat format) {
    /**
     * Checks the fields.
     *
     * @param path the file
     * @param format its format
     */
    public ReportFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(format, "format");
    }
}
