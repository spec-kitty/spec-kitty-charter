import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency

/**
 * The only dependencies a module may declare (ADR-0075 decision 3): charter projects by path and external modules by
 * `group:name`. `pack-validation` and `pack-check` pin theirs with it, so a Spring starter or the packs slice cannot
 * enter their build; `HexagonalArchitectureTest`'s `PACK_VALIDATION_IS_FRAMEWORK_FREE` pins the classes they use.
 *
 * @property projects the charter project paths allowed, such as `:kernel`
 * @property modules the external modules allowed, as `group:name`, any version
 */
class ModuleDependencyAllowlist(val projects: Set<String>, val modules: Set<String>) {

    /**
     * Every declared dependency the list does not allow, in the order given: projects first, as `project <path>`, then
     * external modules, as `group:name`.
     */
    fun refused(declaredProjects: List<String>, declaredModules: List<String>): List<String> =
        declaredProjects.filterNot { it in projects }.map { "project $it" } +
            declaredModules.filterNot { it in modules }

    /**
     * Fails the build when the `api` or `implementation` configuration of [project] declares a dependency the list
     * does not allow, naming every one.
     */
    fun enforceOn(project: Project) {
        val allowlist = this
        project.configurations.matching { it.name == "api" || it.name == "implementation" }.configureEach {
            val configuration = name
            withDependencies {
                val refused = allowlist.refused(
                    filterIsInstance<ProjectDependency>().map { it.path },
                    filterIsInstance<ExternalModuleDependency>().map { "${it.group}:${it.name}" },
                )
                if (refused.isNotEmpty()) {
                    throw GradleException(
                        "${project.path} may not declare $refused in $configuration (ADR-0075); it may depend on " +
                            "${allowlist.projects.sorted()} and ${allowlist.modules.sorted()} only",
                    )
                }
            }
        }
    }
}
