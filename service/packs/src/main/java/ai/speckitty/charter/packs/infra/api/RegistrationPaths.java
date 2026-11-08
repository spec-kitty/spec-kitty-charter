package ai.speckitty.charter.packs.infra.api;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which requests are registration or {@code DELETE} attempts that NFR-010 audits, by method and path below the API base
 * path: {@code PUT /packs/{packId}}, {@code POST /packs}, and the two {@code DELETE}s under a pack. Path segments are
 * taken as the request carries them, undecoded, so what is audited is what was sent.
 */
final class RegistrationPaths {

    /** A registration or DELETE attempt. */
    record Attempt(String operation, String sourceKind, String packId) {}

    private static final Pattern PACK = Pattern.compile("^/packs/([^/]*)$");
    private static final Pattern ARTIFACT = Pattern.compile("^/packs/([^/]*)/artifacts/[^/]+/[^/]+$");
    private static final Pattern RELATIONSHIP = Pattern.compile("^/packs/([^/]*)/relationships$");

    private final String basePath;

    /**
     * Paths below a base path.
     *
     * @param basePath the API base path, for example {@code /api/v1}
     */
    RegistrationPaths(String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * Classifies a request.
     *
     * @param method the HTTP method
     * @param requestUri the request URI, as sent
     * @return the attempt it is; empty for any other request
     */
    Optional<Attempt> classify(String method, String requestUri) {
        if (!requestUri.startsWith(basePath + "/")) {
            return Optional.empty();
        }
        String path = requestUri.substring(basePath.length());
        if (method.equals("POST") && path.equals("/packs")) {
            return Optional.of(new Attempt("registerPackFromSource", "", ""));
        }
        if (method.equals("PUT")) {
            return match(PACK, path, "registerPackFromArchive", "archive");
        }
        if (method.equals("DELETE")) {
            return match(ARTIFACT, path, "deletePackArtifact", "")
                    .or(() -> match(RELATIONSHIP, path, "deletePackRelationship", ""));
        }
        return Optional.empty();
    }

    private static Optional<Attempt> match(Pattern pattern, String path, String operation, String sourceKind) {
        Matcher matcher = pattern.matcher(path);
        return matcher.matches() ? Optional.of(new Attempt(operation, sourceKind, matcher.group(1))) : Optional.empty();
    }
}
