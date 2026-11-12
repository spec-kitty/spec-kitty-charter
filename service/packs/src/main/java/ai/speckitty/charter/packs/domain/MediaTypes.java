package ai.speckitty.charter.packs.domain;

import org.jspecify.annotations.Nullable;

/**
 * The one media-type rule of an asset's {@code mime} (research R6 of the slice-a2 cutover reads): well-formed is a
 * slash with text on both sides. Validate reports a {@code mime} that breaks it as {@code asset_mime_invalid}
 * ({@link PackPolicy}), and the payload read serves a {@code mime} that keeps it as the payload's
 * {@code Content-Type}, {@code application/octet-stream} otherwise. It is deliberately not RFC 6838: tightening it would
 * refuse packs that register today, which ADR-0074 rejected.
 */
public final class MediaTypes {

    private MediaTypes() {}

    /**
     * Whether a media type is a well-formed {@code type/subtype}: its first slash has text before and after it.
     *
     * @param mime the media type as written; may be null
     * @return true when the first {@code /} is neither the first nor the last character
     */
    public static boolean wellFormed(@Nullable String mime) {
        if (mime == null) {
            return false;
        }
        int slash = mime.indexOf('/');
        return slash > 0 && slash < mime.length() - 1;
    }
}
