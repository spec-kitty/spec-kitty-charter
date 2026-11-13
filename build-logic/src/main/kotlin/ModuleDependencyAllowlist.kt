import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency

/**
 * The only dependencies a module may declare (ADR-0075 decision 3): charter projects by path and external modules by
 * `group:name`. `pack-validation` and `pack-check` pin theirs with it, judging every configuration in the hierarchy of
 * the module's main compile and runtime classpaths ([CLASSPATHS]), custom ones they extend from included, so a module
 * cannot declare a Spring starter, or any project or library its list leaves out, at compile time or riding along at
 * run time. Only the dependencies a module declares are judged: what they bring in transitively is not, so
 * `pack-validation`'s allowed `:packs` brings Spring onto its classpath. `HexagonalArchitectureTest`'s
 * `PACK_VALIDATION_IS_FRAMEWORK_FREE` pins, directly and transitively, that the classes the modules use never touch it.
 *
 * @property projects the charter project paths allowed, such as `:kernel`
 * @property modules the external modules allowed, as `group:name`, any version; [CONVENTION_MODULES] are allowed too
 */
class ModuleDependencyAllowlist(val projects: Set<String>, val modules: Set<String>) {

    /**
     * Every declared dependency the list does not allow, in the order given: projects first, as `project <path>`, then
     * external modules, as `group:name`, then any other kind of dependency (a file collection, as `files [name.jar]`), by its kind and name.
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
        dependencies.filterNot { it is ProjectDependency || it is ExternalModuleDependency }.map { describe(it) },
    )

    private fun describe(dependency: Dependency): String = when (dependency) {
        is FileCollectionDependency -> "files ${dependency.files.files.map { it.name }}"
        else -> "${dependency.javaClass.simpleName} ${dependency.name}"
    }

    /**
     * Fails the build when a dependency the list does not allow reaches a configuration in [CLASSPATHS] of [project],
     * naming every one and the configuration that declares it. That is every configuration in the classpath's
     * hierarchy, so a custom configuration the main ones `extendsFrom` is judged too. The check runs when the
     * classpath's dependencies are first read, so a resolution of it fails with this message as its cause.
     */
    fun enforceOn(project: Project) {
        val allowlist = this
        project.configurations.matching { it.name in CLASSPATHS }.configureEach {
            val classpath = this
            withDependencies {
                val refusals = classpath.hierarchy.mapNotNull { declaring ->
                    allowlist.refusedAmong(declaring.dependencies).takeIf { it.isNotEmpty() }?.let { declaring.name to it }
                }
                if (refusals.isNotEmpty()) {
                    throw GradleException(
                        refusals.joinToString("; ") { (declaring, refused) ->
                            "${project.path} may not declare $refused in $declaring (ADR-0075)"
                        } + "; it may depend on ${allowlist.projects.sorted()} and ${allowlist.modules.sorted()} only",
                    )
                }
            }
        }
    }

    companion object {
        /** The resolvable configurations that are the main compile and runtime classpath of a `java-library` module. */
        val CLASSPATHS = setOf("compileClasspath", "runtimeClasspath")

        /** What `charter.hand-written` itself declares on every hand-written module: JSpecify, `compileOnly`. */
        val CONVENTION_MODULES = setOf("org.jspecify:jspecify")
    }
}
