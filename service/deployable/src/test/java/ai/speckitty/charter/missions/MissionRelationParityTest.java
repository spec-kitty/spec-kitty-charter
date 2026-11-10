package ai.speckitty.charter.missions;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.deployable.bdd.RelationTable;
import ai.speckitty.charter.missions.infra.api.MissionRelation;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Every relation the mission-type reads build a link with is a relation of the hypermedia guide's table (R-03 revised),
 * as {@code RelationSetParityTest} checks for the packs slice: the wire key of every {@link MissionRelation} is a key of
 * the table the build copies onto the test classpath.
 */
class MissionRelationParityTest {

    @Test
    void everyMissionRelationIsInTheGuidesTable() {
        assertThat(RelationTable.keys())
                .as("the relation keys of %s", RelationTable.RESOURCE)
                .containsAll(Arrays.stream(MissionRelation.values())
                        .map(MissionRelation::wireKey)
                        .toList());
    }
}
