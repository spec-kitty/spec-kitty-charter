package ai.speckitty.charter.kernel.transport;

/**
 * The names of the response headers the contract defines and more than one slice's inbound adapter sets. They are
 * wire vocabulary: one spelling here, so the slices cannot drift apart.
 */
public final class HeaderNames {

    /** The digest of the pack sources a verified read answered from, set once on its {@code 200}. */
    public static final String SOURCE_FINGERPRINT = "Charter-Source-Fingerprint";

    /** The version of the contract the service serves, set on every response. */
    public static final String CONTRACT_VERSION = "Charter-Contract-Version";

    private HeaderNames() {}
}
