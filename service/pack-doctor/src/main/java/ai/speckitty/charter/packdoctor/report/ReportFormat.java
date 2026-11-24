package ai.speckitty.charter.packdoctor.report;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

/**
 * The formats of the report file {@code --format} names (FR-008, ruling R-i): YAML, the default, and XML. Each writes
 * the one report tree standard output holds as JSON.
 */
public enum ReportFormat {
    /** YAML in block style ({@link YamlReport}), the default of {@code --report}. */
    YAML("yaml"),
    /** The XML mapping of the tree ({@link XmlReport}), its own shape (ruling R-n). */
    XML("xml");

    private final String token;

    ReportFormat(String token) {
        this.token = token;
    }

    /**
     * The format's name on the command line.
     *
     * @return {@code yaml} or {@code xml}
     */
    public String token() {
        return token;
    }

    /**
     * The tree in this format.
     *
     * @param tree the report's tree
     * @return the text of the file
     * @throws IllegalArgumentException when the format cannot carry a value of the tree
     */
    public String write(JsonNode tree) {
        return switch (this) {
            case YAML -> YamlReport.write(tree);
            case XML -> XmlReport.write(tree);
        };
    }

    /**
     * The format a token names, exactly (lower case).
     *
     * @param token the value of {@code --format}
     * @return the format, or empty for no format
     */
    public static Optional<ReportFormat> fromToken(String token) {
        return Arrays.stream(values())
                .filter(format -> format.token.equals(token))
                .findFirst();
    }

    /**
     * The tokens, as a usage fault lists them.
     *
     * @return {@code yaml, xml}
     */
    public static String tokens() {
        return Arrays.stream(values()).map(ReportFormat::token).collect(Collectors.joining(", "));
    }
}
