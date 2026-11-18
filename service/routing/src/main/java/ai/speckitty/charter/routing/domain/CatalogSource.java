package ai.speckitty.charter.routing.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Where a catalog's content came from (ADR-0080 decision 5): provenance the currency pipeline keeps, which no rule
 * reads.
 *
 * @param name the source's name
 * @param url where it is
 * @param accessMethod how it was reached
 * @param snapshotAt when it was read, as written
 * @param licenseNotes its licence notes, when written
 */
public record CatalogSource(
        String name, String url, AccessMethod accessMethod, String snapshotAt, Optional<String> licenseNotes) {

    /**
     * Checks the texts.
     *
     * @param name the name
     * @param url the url
     * @param accessMethod the access method
     * @param snapshotAt the snapshot time
     * @param licenseNotes the licence notes, possibly empty
     */
    public CatalogSource {
        Checks.text(Objects.requireNonNull(name, "name"));
        Checks.text(Objects.requireNonNull(url, "url"));
        Objects.requireNonNull(accessMethod, "accessMethod");
        Checks.text(Objects.requireNonNull(snapshotAt, "snapshotAt"));
        Objects.requireNonNull(licenseNotes, "licenseNotes").ifPresent(Checks::text);
    }
}
