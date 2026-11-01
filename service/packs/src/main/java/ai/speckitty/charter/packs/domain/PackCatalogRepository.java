package ai.speckitty.charter.packs.domain;

import java.util.List;

/** The port through which the configured packs are listed, whatever the storage. */
public interface PackCatalogRepository {

    /**
     * The packs the service is configured with, each with the level storage serves it at, in the storage's own order. A caller
     * that needs an order sorts them itself.
     *
     * @return every configured pack; empty when none is configured
     */
    List<ConfiguredPack> configured();
}
