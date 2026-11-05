package ai.speckitty.charter.activations.infra.packs.badfixture;

import ai.speckitty.charter.packs.infra.storage.ConfigRoot;

/**
 * The packs adapter of the activations slice naming the packs storage adapter: the known-bad of the rule that it names
 * only the packs application ring.
 */
public final class AdapterNamesPacksStorage {

    private AdapterNamesPacksStorage() {}

    /**
     * A packs storage type the adapter must not name.
     *
     * @param root the type named
     * @return the same value
     */
    public static Object hold(ConfigRoot root) {
        return root;
    }
}
