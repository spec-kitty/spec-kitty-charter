package ai.speckitty.charter.contracts.closure;

import java.util.regex.Pattern;

/**
 * The dialect the checker reads: an OpenAPI 3.1 document whose schemas are the OAS 3.1 default dialect (JSON Schema
 * 2020-12 with the OpenAPI vocabulary). Under any other version or dialect the keywords may mean something else, so
 * such a document is reported rather than judged by the wrong semantics.
 */
final class Dialect {

    private static final Pattern OPENAPI_31 = Pattern.compile("3\\.1\\.\\d+");
    private static final String READS_ONLY = "the checker reads OpenAPI 3.1 (JSON Schema 2020-12) only, not openapi ";

    private Dialect() {}

    static void check(Node root, Findings findings) {
        root.child("openapi")
                .ifPresentOrElse(
                        version -> {
                            if (!(version.value() instanceof String text
                                    && OPENAPI_31.matcher(text).matches())) {
                                findings.malformed(version.pointer(), READS_ONLY + version.value());
                            }
                        },
                        () -> findings.malformed(
                                root.pointer(),
                                "the document declares no openapi version; the checker reads OpenAPI 3.1 only"));
        root.child("jsonSchemaDialect")
                .filter(dialect -> !ClosureChecker.DEFAULT_DIALECT.equals(dialect.value()))
                .ifPresent(dialect -> findings.malformed(
                        dialect.pointer(),
                        "the checker reads the OAS 3.1 default" + " dialect " + ClosureChecker.DEFAULT_DIALECT
                                + " only, not " + dialect.value()));
    }
}
