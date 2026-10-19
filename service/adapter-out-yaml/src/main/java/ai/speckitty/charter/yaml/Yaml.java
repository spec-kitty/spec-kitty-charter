package ai.speckitty.charter.yaml;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
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

    /** The single document of {@code text}; {@code null} for an empty stream or a document that is a YAML null. */
    static @Nullable Object load(String text) {
        return new Load(settings()).loadFromString(text);
    }
}
