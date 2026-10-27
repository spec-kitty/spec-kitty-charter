package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole-pack seam over the real packs (#189, SC-002): every open-packs pack and every pack of this repository's
 * {@code packs/} that has a {@code pack.yaml} loads with no violation, writes to a fresh directory and reloads with the
 * same meaning for every bound kind and every DRG document. This proves the seam, not the round-trip gate (#187): it
 * compares aggregates, not files, and says nothing about the files no kind models yet.
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout.
 */
@Tag("open-packs")
class WholePackOpenPacksRoundTripTest {

    private final WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

    private static List<Path> packDirectories() throws IOException {
        List<Path> roots = List.of(
                OpenPacksCorpus.packsDirectory(),
                Path.of(System.getProperty("charter.repositoryRoot")).resolve("packs"));
        List<Path> packs = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> dirs = Files.list(root)) {
                dirs.filter(dir -> Files.isRegularFile(dir.resolve(PackFileSet.PACK_YAML)))
                        .sorted()
                        .forEach(packs::add);
            }
        }
        return packs;
    }

    private static Map<String, Object> meaning(WholePackStore.LoadedPack loaded) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("descriptor", loaded.pack().descriptor());
        for (KindBinding<?, ?> kind : KindBindings.all()) {
            List<Object> dsos = new ArrayList<>();
            for (CharterArtifact artifact : loaded.pack().artifacts(kind.kind())) {
                dsos.add(kind.dsoOf(artifact));
            }
            out.put(kind.kind().token(), dsos);
        }
        out.put("graphs", loaded.graphs());
        out.put("layout", loaded.layout().paths());
        return out;
    }

    @Test
    void everyRealPackReloadsWithTheSameMeaningAfterAWholePackWrite(@TempDir Path host) throws IOException {
        List<Path> packs = packDirectories();
        List<String> problems = new ArrayList<>();
        int artifacts = 0;
        for (Path pack : packs) {
            WholePackStore.LoadedPack loaded = store.load(pack);
            if (!loaded.pack().violations().isEmpty()) {
                problems.add(pack + " loads with violations: " + loaded.pack().violations());
                continue;
            }
            artifacts += loaded.pack().artifacts().size();
            Path target = host.resolve(String.valueOf(packs.indexOf(pack))).resolve(pack.getFileName().toString());
            store.write(loaded, target);
            if (!meaning(store.load(target)).equals(meaning(loaded))) {
                problems.add(pack + " reloads with a different meaning");
            }
        }
        assertThat(problems).isEmpty();
        assertThat(packs).isNotEmpty();
        assertThat(artifacts).isPositive();
    }
}
