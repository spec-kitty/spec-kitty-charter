package ai.speckitty.charter.kernel.transport;

/** The request-body budget every JSON write of the service is held to (#310, #323). */
public final class BodyLimit {

    /**
     * The largest request body without a guide, in bytes: 1 MiB (#310). It is the toolguide's room for the fields
     * beside its guide, so every registration holds its fields to the same budget; the largest pack artifact the
     * service ships is about 14 KiB. A relationship registration, a few hundred bytes, is held to it too (#323).
     */
    public static final long MAX_BYTES = 1024L * 1024;

    private BodyLimit() {}
}
