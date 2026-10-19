import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** The shared version catalog, as precompiled script plugins cannot use the generated `libs` accessor. */
val Project.catalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalStateException("No library '$alias' in gradle/libs.versions.toml") }

/**
 * The Java release the service targets. The design fixes Java 25 (docs/architecture/hexagonal-service.md);
 * `-Pcharter.javaVersion=<n>` exists only for machines that cannot provision that toolchain, and CI never sets it.
 */
val Project.charterJavaVersion: Int
    get() = (findProperty("charter.javaVersion") as String?)?.toInt() ?: 25

fun VersionCatalog.version(alias: String): String =
    findVersion(alias).orElseThrow { IllegalStateException("No version '$alias' in gradle/libs.versions.toml") }.requiredVersion
