// Aggregation only, no sources of its own: one Javadoc site (spec FR-011, FR-012) and one JaCoCo report over the
// hand-written service modules. Both are part of `check`, so `./gradlew verifyAll` builds them.
//
// The module set is declared here, by name, rather than selected by `charter.hand-written`: :contracts applies
// that plugin too (it is hand-written Java), but it is the contract's own checker, not part of the service, and
// charter-api is generated (spec C-005). Neither belongs in the service's API reference or its coverage.
//
// Configuration-cache safe: the other projects are reached only through the variants they publish (their sources
// variant from `java { withSourcesJar() }` in charter.hand-written, their runtime classpath, their coverage data),
// never through their tasks, source sets or configurations.
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemOperations
import javax.inject.Inject

plugins {
    id("charter.java-base")
    `jacoco-report-aggregation`
}

/** Each hand-written service module and the one package it holds, in the order the Javadoc overview lists them. */
val serviceModules = linkedMapOf(
    "kernel" to "ai.speckitty.charter.kernel",
    "packs" to "ai.speckitty.charter.packs",
    "deployable" to "ai.speckitty.charter.deployable",
)

// Fail closed when the list and the service disagree: every project under service/ that applies
// charter.hand-written must be listed, and every listed one must apply it. Checked once all projects are
// configured, by reading their paths and applied plugins (no task, no cross-project mutation), so a module
// added without updating this list fails the build instead of silently dropping out of both reports.
val serviceDirectory = rootProject.layout.projectDirectory.dir("service").asFile
gradle.projectsEvaluated {
    val handWrittenServiceProjects = rootProject.subprojects
        .filter { it.projectDir.parentFile == serviceDirectory && it.pluginManager.hasPlugin("charter.hand-written") }
        .map { it.name }
        .toSortedSet()
    val missing = handWrittenServiceProjects - serviceModules.keys
    val notHandWritten = serviceModules.keys - handWrittenServiceProjects
    if (missing.isNotEmpty() || notHandWritten.isNotEmpty()) {
        throw GradleException(
            "service/charter-reports: `serviceModules` must list exactly the hand-written projects under service/. " +
                (if (missing.isEmpty()) "" else "Not listed: $missing (add each with its package). ") +
                (if (notHandWritten.isEmpty()) "" else "Listed but not a hand-written service project: $notHandWritten."),
        )
    }
}

// The modules whose sources are documented: exactly these, not what they depend on (charter-api would follow
// packs otherwise).
val apiDocs = configurations.dependencyScope("apiDocs")
// What the documented sources compile against: the modules' runtime classpath, which carries their
// `implementation` dependencies too, plus what they use at compile time only.
val apiDocsClasspath = configurations.dependencyScope("apiDocsClasspath")

dependencies {
    serviceModules.keys.forEach { name ->
        "apiDocs"(project(":$name")) { isTransitive = false }
        "apiDocsClasspath"(project(":$name"))
        "jacocoAggregation"(project(":$name")) { isTransitive = false }
    }
    // compileOnly in every hand-written module, so it is not on their runtime variants; @NullMarked needs it.
    "apiDocsClasspath"(libs.jspecify)
}

val javadocSources = configurations.resolvable("javadocSources") {
    extendsFrom(apiDocs.get())
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}

val javadocClasspath = configurations.resolvable("javadocClasspath") {
    extendsFrom(apiDocsClasspath.get())
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
    }
}

/** Unpacks a sources jar into a directory, without its manifest, so javadoc can read the files. */
abstract class UnpackSources : TransformAction<TransformParameters.None> {
    @get:Inject abstract val archives: ArchiveOperations
    @get:Inject abstract val files: FileSystemOperations
    @get:InputArtifact abstract val sourcesJar: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val jar = sourcesJar.get().asFile
        val dir = outputs.dir(jar.nameWithoutExtension)
        files.copy {
            from(archives.zipTree(jar))
            exclude("META-INF/**")
            into(dir)
        }
    }
}

val unpackedSources = "java-sources-directory"
dependencies.registerTransform(UnpackSources::class) {
    from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
    to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, unpackedSources)
}

val sourceDirectories = javadocSources.get().incoming.artifactView {
    attributes { attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, unpackedSources) }
}.files

// The docsite's colors and type over the JDK stylesheet, light and dark (src/javadoc/charter-docsite.css).
val docsiteStylesheet = layout.projectDirectory.file("src/javadoc/charter-docsite.css")

val aggregateJavadoc = tasks.register<Javadoc>("aggregateJavadoc") {
    group = "documentation"
    description = "Builds one Javadoc site over the hand-written service modules, grouped per module, doclint as errors."
    source(sourceDirectories.asFileTree.matching { include("**/*.java") })
    classpath = javadocClasspath.get()
    javadocTool = javaToolchains.javadocToolFor { languageVersion = JavaLanguageVersion.of(charterJavaVersion) }
    setDestinationDir(layout.buildDirectory.dir("docs/javadoc").get().asFile)
    inputs.file(docsiteStylesheet).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("docsiteStylesheet")
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        docEncoding = "UTF-8"
        charSet = "UTF-8"
        windowTitle = "Charter service API"
        docTitle = "Charter service API"
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Werror", true)
        addBooleanOption("notimestamp", true)
        // Report every finding, not the first hundred.
        addStringOption("Xmaxwarns", "10000")
        serviceModules.forEach { (module, packageName) -> group(module, listOf(packageName)) }
        // Gradle prefixes one `-`, so this is javadoc's `--add-stylesheet`.
        addFileOption("-add-stylesheet", docsiteStylesheet.asFile)
        // Back to the docsite page that introduces this reference. Relative through {@docRoot}, which javadoc
        // replaces per page, so it resolves under the site's base path from every depth.
        bottom = "<a class=\"charter-docsite-back\" href=\"{@docRoot}/../api/javadoc/\">Back to the charter docs</a>"
    }
}

// Coverage over the same modules, from each module's `test` suite.
reporting {
    reports {
        named<JacocoCoverageReport>("testCodeCoverageReport") {
            reportTask {
                reports {
                    xml.required = true
                    html.required = true
                }
            }
        }
    }
}

// Copies the Javadoc to where the docsite publishes it unchanged (site/public/javadoc, ignored by git).
// Used locally and by the docs-site workflow.
tasks.register<Sync>("stageJavadocForSite") {
    group = "documentation"
    description = "Copies the aggregated Javadoc into site/public/javadoc for the docsite build."
    from(aggregateJavadoc)
    into(rootProject.layout.projectDirectory.dir("site/public/javadoc"))
}

tasks.named("check") {
    dependsOn(aggregateJavadoc, tasks.named("testCodeCoverageReport"))
}
