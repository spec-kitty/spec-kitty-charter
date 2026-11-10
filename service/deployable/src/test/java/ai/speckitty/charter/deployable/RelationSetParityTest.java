package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.deployable.bdd.RelationTable;
import ai.speckitty.charter.packs.infra.api.Relation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every relation the service builds a link with is a relation of the hypermedia guide's table (R-03 revised): the
 * wire key of every {@link Relation} is a key of the table the build copies onto the test classpath.
 *
 * <p>The activations and profiles slices write their relation keys as string literals in their {@code infra.api}
 * mappers, not as {@link Relation}s (their rings may not import the packs adapter), so the check above would not see a
 * relation built there (plan fold A6). The second check reads those mappers' sources and holds each literal key of a
 * {@code links.put(...)}, a {@code Map.of(...)} link or an {@code Map.entry(...)} to the same table.
 */
class RelationSetParityTest {

    /** A relation key written as the first argument of a put or a one-entry map in a mapper of {@code infra.api}. */
    private static final Pattern LINK_KEY =
            Pattern.compile("(?:\\blinks\\.put|\\bMap\\.of|\\bMap\\.entry)\\(\\s*\"([a-z][a-z-]*)\"\\s*,");

    private static final List<String> MAPPER_ROOTS = List.of(
            "../activations/src/main/java/ai/speckitty/charter/activations/infra/api",
            "../profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api");

    @Test
    void everyRelationTheServiceBuildsIsInTheGuidesTable() {
        assertThat(RelationTable.keys())
                .as("the relation keys of %s", RelationTable.RESOURCE)
                .containsAll(
                        Arrays.stream(Relation.values()).map(Relation::wireKey).toList());
    }

    @Test
    void everyRelationAnActivationsOrProfilesMapperWritesIsInTheGuidesTable() {
        Set<String> written = writtenKeys();

        assertThat(written)
                .as("the link keys the mappers write: the scan finds the relations the slices are known to build")
                .contains("self", "artifact", "up", "first", "next", "artifacts", "effective-artifact");
        assertThat(RelationTable.keys())
                .as("the relation keys of %s", RelationTable.RESOURCE)
                .containsAll(written);
    }

    @Test
    void theScanReadsAKeyOfEveryFormAMapperWritesAndNothingElse() {
        String source = """
                links.put("self", new Link(a));
                links.put(
                        "effective-artifact",
                        new Link(b));
                return Map.of("up", new Link(c));
                Map.entry("collection", link);
                tree.put("packs", value);
                Map.of("not a key", 1);
                """;

        assertThat(keys(source)).containsExactly("self", "effective-artifact", "up", "collection");
    }

    private static Set<String> writtenKeys() {
        Set<String> written = new LinkedHashSet<>();
        for (String root : MAPPER_ROOTS) {
            Path directory = Path.of(root);
            assertThat(directory)
                    .as("the mapper sources %s, read from the module directory", root)
                    .isDirectory();
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path file :
                        files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    written.addAll(keys(Files.readString(file)));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return written;
    }

    private static List<String> keys(String source) {
        Matcher match = LINK_KEY.matcher(source);
        return match.results().map(result -> result.group(1)).toList();
    }
}
