package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;

/**
 * Why a source was refused: the fixed set of reasons of {@code pack_source_refused} (NFR-012). The detail of the
 * refusal is the reason's token and its fixed sentence and nothing else: no part of the URL, the path, a header or
 * upstream text reaches it, so an untrusted value is never echoed and a refused path reads like a missing one.
 */
public enum SourceRefusal {
    /** The host is not on the allowlist of the source kind. */
    HOST_NOT_ALLOWED("The host is not on the allowlist of this kind of source."),
    /** The host resolves to an address that is not global unicast. */
    ADDRESS_NOT_GLOBAL("The host resolves to an address that is not a global address."),
    /** The URL is not an https URL to a plain host name on port 443 without user information. */
    URL_REFUSED("The URL is not an https URL to a plain host name on port 443 without user information."),
    /** A redirect was refused, or there were more than the limit. */
    REDIRECT_REFUSED("A redirect was refused, or there were too many."),
    /** The response is not an archive of a type the service reads. */
    WRONG_MEDIA_TYPE("The response is not a gzip or zip archive."),
    /** The archive is over the size limit. */
    TOO_LARGE("The archive is larger than the size limit."),
    /** The archive's sha256 is not the one the request named. */
    DIGEST_MISMATCH("The archive's sha256 is not the one the request named."),
    /** The descriptor's name is not the target pack id. */
    NAME_MISMATCH("The name the pack declares is not the target pack id."),
    /** The request names a member this kind of source does not take, or lacks one it needs. */
    SOURCE_MALFORMED("The request names a member this kind of source does not take, or lacks one it needs."),
    /** The path is not a pack directory the service reads. */
    PATH_REFUSED("The path is not a pack directory this service reads."),
    /** The git ref is not a commit id, nor a refs/heads or refs/tags name of the grammar, or names nothing in the repository. */
    BAD_REF("The git ref is not a commit id or a branch or tag name of the allowed form, or names nothing."),
    /** The commit the ref resolved to is not the commit the request named. */
    COMMIT_MISMATCH("The commit the ref resolved to is not the commit the request named.");

    private final String sentence;

    SourceRefusal(String sentence) {
        this.sentence = sentence;
    }

    /**
     * The reason on the wire: the first word of the refusal's detail.
     *
     * @return the lower-case, underscore-separated token
     */
    public String wire() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The refusal this reason ends a registration with.
     *
     * @return {@code pack_source_refused}, whose detail is the token, a colon and the fixed sentence
     */
    public CharterRefusal refusal() {
        return new CharterRefusal(RefusalCode.PACK_SOURCE_REFUSED, wire() + ": " + sentence);
    }
}
