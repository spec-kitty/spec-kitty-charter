package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.packs.application.AuditLog;
import jakarta.servlet.http.HttpServletRequest;

/**
 * What the audit entry of a request that ends at the web edge knows, filled by whoever learns it: the guard says whether
 * a token came, the admission names the pack and the source, the controller says the job took the attempt over. One
 * lives in a request attribute from the audit filter to its end.
 */
final class AuditContext {

    /** The request attribute that holds the context. */
    static final String ATTRIBUTE = AuditContext.class.getName();

    /** The request attribute the guard sets: the {@link AuditLog.Credential} the request carried. */
    static final String CREDENTIAL_ATTRIBUTE = "ai.speckitty.charter.credential";

    private volatile String packId = "";
    private volatile String sourceKind = "";
    private volatile String locator = "";
    private volatile String revisionBefore = "";
    private volatile String revisionAfter = "";
    private volatile boolean handedToJob;

    /**
     * The context of a request, created on first use.
     *
     * @param request the request
     * @return its context
     */
    static AuditContext of(HttpServletRequest request) {
        Object held = request.getAttribute(ATTRIBUTE);
        if (held instanceof AuditContext context) {
            return context;
        }
        AuditContext created = new AuditContext();
        request.setAttribute(ATTRIBUTE, created);
        return created;
    }

    /**
     * The credential the guard recorded, {@code NONE} when none was.
     *
     * @param request the request
     * @return what the request carried
     */
    static AuditLog.Credential credentialOf(HttpServletRequest request) {
        return request.getAttribute(CREDENTIAL_ATTRIBUTE) instanceof AuditLog.Credential credential
                ? credential
                : AuditLog.Credential.NONE;
    }

    /**
     * Names the pack the attempt is about.
     *
     * @param value the target pack as the request named it
     */
    void packId(String value) {
        this.packId = value;
    }

    /**
     * Names the source of the attempt.
     *
     * @param kind the source kind
     * @param locatorValue the locator, with its query cut
     */
    void source(String kind, String locatorValue) {
        this.sourceKind = kind;
        this.locator = locatorValue;
    }

    /**
     * Records the stored revision before the attempt.
     *
     * @param revision the revision token, or empty when no pack was stored
     */
    void revisionBefore(String revision) {
        this.revisionBefore = revision;
    }

    /**
     * Records the revision a write left, when it changed the pack.
     *
     * @param revision the revision token after the write
     */
    void revisionAfter(String revision) {
        this.revisionAfter = revision;
    }

    /** The registration job took the attempt over and writes its entry when it ends. */
    void handedToJob() {
        this.handedToJob = true;
    }

    boolean isHandedToJob() {
        return handedToJob;
    }

    String packIdOr(String fallback) {
        return packId.isEmpty() ? fallback : packId;
    }

    String sourceKind(String fallback) {
        return sourceKind.isEmpty() ? fallback : sourceKind;
    }

    String locator() {
        return locator;
    }

    String revisionBefore() {
        return revisionBefore;
    }

    /** The revision the write left; the revision before when the attempt changed nothing. */
    String revisionAfter() {
        return revisionAfter.isEmpty() ? revisionBefore : revisionAfter;
    }
}
