package ai.speckitty.charter.yaml;

import java.util.ArrayList;
import java.util.List;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/** Loading YAML text into plain values, with the settings every reader in this adapter shares. */
final class Yaml {

    private Yaml() {}

    static LoadSettings settings() {
        return LoadSettings.builder()
                .setAllowDuplicateKeys(false)
                .setCodePointLimit(Integer.MAX_VALUE)
                .setLabel("charter")
                .build();
    }

    /** Every document of a YAML stream, in order. */
    static List<Object> loadAll(String text) {
        List<Object> documents = new ArrayList<>();
        new Load(settings()).loadAllFromString(text).forEach(documents::add);
        return documents;
    }

    static Object load(String text) {
        return new Load(settings()).loadFromString(text);
    }
}
