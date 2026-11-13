import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency

/**
 * The only dependencies a module may declare (ADR-0075 decision 3): charter projects by path and external modules by
 * `group:name`. `pack-validation` and `pack-check` pin theirs with it, on every configuration that puts a jar on the
 * module's main compile or runtime classpath ([CONFIGURATIONS]), so neither a Spring starter nor the packs slice can
 * enter their build, at compile time or riding along at run time. `HexagonalArchitectureTest`'s
 * `PACK_VALIDATION_IS_FRAMEWORK_FREE` pins the classes they use.
 *
 * @property projects the charter project paths allowed, such as `:kernel`
 * @property modules the external modules allowed, as `group:name`, any version; [CONVENTION_MODULES] are allowed too
 */
class ModuleDependencyAllowlist(val projects: Set<String>, val modules: Set<String>) {

    /**
     * Every declared dependency the list does not allow, in the order given: projects first, as `project <path>`, then
     * external modules, as `group:name`, then any other kind of dependency (a file collection), as given.
     */
    fun refused(declaredProjects: List<String>, declaredModules: List<String>, other: List<String> = emptyList()): List<String> =
        declaredProjects.filterNot { it in projects }.map { "project $it" } +
            declaredModules.filterNot { it in modules || it in CONVENTION_MODULES } +
            other

    /**
     * The refusals among [dependencies], as [refused] names them. A dependency that is neither a project nor an external
     * module (such as `files(...)`) is always refused, since nothing about it can be checked.
     */
    fun refusedAmong(dependencies: Collection<Dependency>): List<String> = refused(
        dependencies.filterIsInstance<ProjectDependency>().map { it.path },
        dependencies.filterIsInstance<ExternalModuleDependency>().map { "${it.group}:${it.name}" },
        dependencies.filterNot { it is ProjectDependency || it is ExternalModuleDependency }.map { "$it" },
    )

    /**
     * Fails the build when a configuration in [CONFIGURATIONS] of [project] declares a dependency the list does not
     * allow, naming every one. The check runs when the configuration's dependencies are first read, so a resolution of
     * the module's classpath fails with this message as its cause.
     */
    fun enforceOn(project: Project) {
        val allowlist = this
        project.configurations.matching { it.name in CONFIGURATIONS }.configureEach {
            val configuration = name
            withDependencies {
                val refused = allowlist.refusedAmong(this)
                if (refused.isNotEmpty()) {
                    throw GradleException(
                        "${project.path} may not declare $refused in $configuration (ADR-0075); it may depend on " +
                            "${allowlist.projects.sorted()} and ${allowlist.modules.sorted()} only",
                    )
                }
            }
        }
    }

    companion object {
        /** The declarable configurations that feed the main compile or runtime classpath of a `java-library` module. */
        val CONFIGURATIONS = setOf("api", "implementation", "compileOnly", "compileOnlyApi", "runtimeOnly")

        /** What `charter.hand-written` itself declares on every hand-written module: JSpecify, `compileOnly`. */
        val CONVENTION_MODULES = setOf("org.jspecify:jspecify")
    }
}
