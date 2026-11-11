package ai.speckitty.charter.deployable.bdd;

import ai.speckitty.charter.packs.infra.storage.Locations;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import ai.speckitty.charter.packs.infra.storage.StorageRootLocations;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The pack locations of a scenario: the packs its steps wrote, a set fixed by name and level, and the pack domain of its
 * directory, {@code <root>/packs}, where a whole-pack registration writes and reads what it stored.
 */
final class ScenarioLocations implements Locations {

    private static final String DOMAIN = "packs";

    private final ScenarioPacks packs;

    ScenarioLocations(ScenarioPacks packs) {
        this.packs = packs;
    }

    @Override
    public List<PackLocation> all() {
        List<PackLocation> all = new ArrayList<>(packs.locations());
        domain().ifPresent(domain -> all.addAll(domain.all()));
        return all;
    }

    @Override
    public Optional<Path> writeRoot() {
        return domain().map(StorageRootLocations::writableRoot);
    }

    private Optional<StorageRootLocations> domain() {
        return packs.rootDirectory().map(root -> new StorageRootLocations(root.resolve(DOMAIN)));
    }
}
