import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** What a module of ADR-0075 may declare: its allowed charter projects and external modules, nothing else (#550). */
class ModuleDependencyAllowlistTest {

    private val allowlist = ModuleDependencyAllowlist(
        projects = setOf(":kernel", ":profiles"),
        modules = setOf("org.snakeyaml:snakeyaml-engine"),
    )

    @Test
    fun anAllowedProjectAndAnAllowedModulePass() {
        assertThat(allowlist.refused(listOf(":kernel", ":profiles"), listOf("org.snakeyaml:snakeyaml-engine")))
            .isEmpty()
    }

    @Test
    fun aProjectOutsideTheListIsRefusedByItsPath() {
        assertThat(allowlist.refused(listOf(":kernel", ":packs"), emptyList())).containsExactly("project :packs")
    }

    @Test
    fun anExternalModuleOutsideTheListIsRefusedByItsCoordinates() {
        assertThat(allowlist.refused(emptyList(), listOf("org.springframework:spring-context")))
            .containsExactly("org.springframework:spring-context")
    }

    @Test
    fun everyRefusalIsReportedNotOnlyTheFirst() {
        assertThat(
            allowlist.refused(
                listOf(":packs", ":kernel", ":deployable"),
                listOf("org.springframework:spring-web", "org.snakeyaml:snakeyaml-engine"),
            ),
        ).containsExactly("project :packs", "project :deployable", "org.springframework:spring-web")
    }

    @Test
    fun noDependencyAtAllPasses() {
        assertThat(allowlist.refused(emptyList(), emptyList())).isEmpty()
    }
}
