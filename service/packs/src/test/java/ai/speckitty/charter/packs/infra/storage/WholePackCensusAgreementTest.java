package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.examples.GoldenSubjects;
import ai.speckitty.charter.packs.examples.GoldenSubjects.Subject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The whole-pack gate's census is the one read's (#554, WP13 review m4): over every golden subject (each planted pack,
 * each descriptor-less plant again with a descriptor, and the example packs), every path a walk of the pack meets (each
 * regular file and each link, never followed) has a gate kind in {@link WholePackCensus#of}, and no other path does; and
 * the companions {@link WholePackCensus#companionsOf} lists for an artifact the store holds are the companions the
 * store's read claims for it.
 *
 * <p>Scope: the scenario packs are built inside Cucumber steps in {@code :deployable}, out of this module's reach (as
 * {@code HeldSetAgreementTest} records); the golden subjects plant every class of file they plant.
 */
@DisplayName("the gate census lists every walked path, and its companions are the store's claims (#554, m4)")
class WholePackCensusAgreementTest {

    static List<Subject> subjects() {
        return GoldenSubjects.subjects();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subjects")
    @DisplayName("every walked path has a gate kind, and only those")
    void everyWalkedPathHasAKind(Subject subject, @TempDir Path host) throws IOException {
        Path pack = GoldenSubjects.copy(subject, host.resolve("store"));

        SortedMap<String, String> kinds = WholePackCensus.of(pack);

        assertThat(kinds.keySet()).as("%s: the census's paths", subject).containsExactlyElementsOf(walked(pack));
        assertThat(kinds.values())
                .as("%s: each path's kind", subject)
                .allSatisfy(kind -> assertThat(kind).isNotBlank());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subjects")
    @DisplayName("the census's companions of a held artifact are the store's claims for it")
    void theCensusCompanionsAreTheStoresClaims(Subject subject, @TempDir Path host) throws IOException {
        Path pack = GoldenSubjects.copy(subject, host.resolve("store"));
        Optional<Map<String, Set<String>>> claims = storeClaims(pack);

        // A pack the store cannot describe holds no artifact, so it claims no companion.
        if (claims.isEmpty()) {
            return;
        }
        Map<String, Set<String>> census = WholePackCensus.companionsOf(pack).entrySet().stream()
                .filter(entry -> claims.get().containsKey(entry.getValue().owner()))
                .collect(Collectors.groupingBy(
                        entry -> entry.getValue().owner(),
                        Collectors.mapping(Map.Entry::getKey, Collectors.toCollection(TreeSet::new))));
        Map<String, Set<String>> claimed = claims.get().entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(census).as("%s: owner to companions", subject).isEqualTo(claimed);
    }

    /** Every regular file and every link under the pack, pack relative and sorted; a link is never followed. */
    private static List<String> walked(Path pack) throws IOException {
        try (Stream<Path> paths = Files.walk(pack)) {
            return paths.filter(path -> !path.equals(pack))
                    .filter(path -> Files.isSymbolicLink(path) || Files.isRegularFile(path))
                    .map(path -> pack.relativize(path)
                            .toString()
                            .replace(path.getFileSystem().getSeparator(), "/"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * The companions the store's read claims, by the file of the artifact it holds: each companion of a held artifact
     * resolved in the pack as the read resolves it. Empty when the store cannot read the pack's descriptor.
     */
    private static Optional<Map<String, Set<String>>> storeClaims(Path pack) {
        FilesystemPackStore store = new FilesystemPackStore(pack.getParent(), new YamlSerializationStrategy());
        FilesystemPackStore.PackRead read;
        try {
            read = store.read(new PackIdentifier(String.valueOf(pack.getFileName())));
        } catch (CharterRefusal refused) {
            return Optional.empty();
        }
        Map<String, Set<String>> claims = new java.util.HashMap<>();
        read.elements().forEach((kind, stored) -> {
            KindBinding<?, ?> binding = KindBindings.of(kind).orElseThrow();
            for (FilesystemPackStore.Stored<?> element : stored) {
                claims.put(element.file(), claimsOf(binding, pack, element));
            }
        });
        return Optional.of(claims);
    }

    @SuppressWarnings("unchecked")
    private static <D> Set<String> claimsOf(
            KindBinding<D, ?> binding, Path pack, FilesystemPackStore.Stored<?> element) {
        Set<String> claims = new TreeSet<>();
        for (KindBinding.Companion companion : binding.companions((D) element.dso())) {
            if (Companions.resolve(Companions.Rules.bundled(), pack, element.file(), companion)
                    instanceof Companions.Inside inside) {
                claims.add(inside.packRelative());
            }
        }
        return claims;
    }
}
