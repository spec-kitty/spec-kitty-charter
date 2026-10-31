package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.deployable.bdd.RelationTable;
import ai.speckitty.charter.packs.infra.api.Relation;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Every relation the service builds a link with is a relation of the hypermedia guide's table (R-03 revised): the
 * wire key of every {@link Relation} is a key of the table the build copies onto the test classpath.
 */
class RelationSetParityTest {

    @Test
    void everyRelationTheServiceBuildsIsInTheGuidesTable() {
        assertThat(RelationTable.keys())
                .as("the relation keys of %s", RelationTable.RESOURCE)
                .containsAll(
                        Arrays.stream(Relation.values()).map(Relation::wireKey).toList());
    }
}
