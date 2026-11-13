import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

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
    fun everyRefusalIsReportedNotOnlyTheFirst() {
        assertThat(
            allowlist.refused(
                listOf(":packs", ":kernel", ":deployable"),
                listOf("org.springframework:spring-web", "org.snakeyaml:snakeyaml-engine"),
            ),
        ).containsExactly("project :packs", "project :deployable", "org.springframework:spring-web")
    }
}

/** The allowlist wired into a project: a refused dependency fails the resolution of the module's classpath (#550). */
class ModuleDependencyAllowlistEnforcementTest {

    @TempDir
    lateinit var root: File

    private fun module(): Project {
        val repository = ProjectBuilder.builder().withProjectDir(root).withName("root").build()
        val kernel = ProjectBuilder.builder().withParent(repository).withName("kernel")
            .withProjectDir(File(root, "kernel")).build()
        kernel.pluginManager.apply("java-library")
        val packs = ProjectBuilder.builder().withParent(repository).withName("packs")
            .withProjectDir(File(root, "packs")).build()
        packs.pluginManager.apply("java-library")
        val module = ProjectBuilder.builder().withParent(repository).withName("pack-validation")
            .withProjectDir(File(root, "pack-validation")).build()
        module.pluginManager.apply("java-library")
        ModuleDependencyAllowlist(projects = setOf(":kernel"), modules = emptySet()).enforceOn(module)
        return module
    }

    private fun resolve(module: Project, classpath: String) =
        module.configurations.getByName(classpath).resolve()

    @Test
    fun anAllowedProjectResolves() {
        val module = module()
        module.dependencies.add("implementation", module.project(":kernel"))
        assertThat(resolve(module, "compileClasspath")).hasSize(1).allSatisfy { assertThat(it.path).contains("kernel") }
    }

    @Test
    fun aRefusedProjectFailsTheCompileClasspathNamingIt() {
        val module = module()
        module.dependencies.add("implementation", module.project(":packs"))
        assertThatThrownBy { resolve(module, "compileClasspath") }
            .hasStackTraceContaining(":pack-validation may not declare [project :packs] in implementation (ADR-0075)")
    }

    @Test
    fun aRuntimeOnlyStarterFailsTheRuntimeClasspath() {
        val module = module()
        module.dependencies.add("runtimeOnly", "org.springframework.boot:spring-boot-starter:4.0.0")
        assertThatThrownBy { resolve(module, "runtimeClasspath") }
            .hasStackTraceContaining("[org.springframework.boot:spring-boot-starter] in runtimeOnly (ADR-0075)")
    }

    @Test
    fun aCustomConfigurationTheClasspathExtendsFromIsJudged() {
        val module = module()
        val extras = module.configurations.create("extras")
        extras.dependencies.add(module.dependencies.create("org.springframework:spring-web:7.0.0"))
        module.configurations.getByName("implementation").extendsFrom(extras)
        assertThatThrownBy { resolve(module, "compileClasspath") }
            .hasStackTraceContaining("[org.springframework:spring-web] in extras (ADR-0075)")
    }

    @Test
    fun aCompileOnlyModuleAndAFileCollectionAreRefusedToo() {
        val module = module()
        module.dependencies.add("compileOnly", "org.springframework:spring-context:7.0.0")
        module.dependencies.add("compileOnly", module.files("spring.jar"))
        assertThatThrownBy { resolve(module, "compileClasspath") }
            .hasStackTraceContaining("org.springframework:spring-context")
            .hasStackTraceContaining("files [spring.jar]")
            .hasStackTraceContaining("in compileOnly (ADR-0075)")
    }

    @Test
    fun theConventionsJSpecifyIsAllowed() {
        val module = module()
        module.dependencies.add("compileOnly", "org.jspecify:jspecify:1.0.0")
        // No repository is declared, so the classpath is never resolved; only the allowlist's own failure is judged.
        assertThatCode { module.configurations.getByName("compileClasspath").incoming.resolutionResult.root }
            .doesNotThrowAnyException()
    }
}
