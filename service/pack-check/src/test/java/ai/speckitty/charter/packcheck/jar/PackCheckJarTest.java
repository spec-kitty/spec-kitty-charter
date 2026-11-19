package ai.speckitty.charter.packcheck.jar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.examples.GoldenSubjects;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The pack-check jar as it ships (#478, WP06 T023 to T026): run in a child JVM on every path, and read entry by entry.
 * Tagged {@code jar}: only {@code jarTest} runs it, after {@code packCheckJar} built the jar; {@code test} and PIT leave
 * it out. The child JVM is the toolchain's {@code java}, never the one on {@code PATH}, started in a temporary working
 * directory with no {@code JAVA_TOOL_OPTIONS}, {@code JDK_JAVA_OPTIONS}, {@code _JAVA_OPTIONS} or {@code CLASSPATH}.
 */
@Tag("jar")
class PackCheckJarTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** What a run's standard error never holds, on any path. */
    private static final List<String> NEVER_ON_STDERR =
            List.of("SLF4J", "Exception", "NoClassDefFoundError", "ClassNotFoundException");

    /** The entry prefixes the jar refuses: Spring, Tomcat, Jakarta, Logback and the service's web and API layers. */
    private static final List<String> REFUSED_PREFIXES = List.of(
            "org/springframework/",
            "org/apache/tomcat/",
            "jakarta/",
            "ch/qos/logback/",
            "ai/speckitty/charter/api/",
            "ai/speckitty/charter/web/");

    /** The groups the jar's classpath may leave out of {@code runtimeClasspath}: what the three excludes reach. */
    private static final List<String> DROPPED_GROUPS = List.of(
            "org.springframework",
            "org.springframework.boot",
            "org.apache.tomcat.embed",
            "jakarta.annotation",
            "jakarta.validation",
            "io.micrometer",
            "ch.qos.logback",
            "org.apache.logging.log4j",
            "org.yaml",
            "commons-logging",
            "org.jspecify");

    /** The modules of other groups the excludes reach: the logging bridges of spring-boot-starter-logging. */
    private static final List<String> DROPPED_MODULES = List.of("org.slf4j:jul-to-slf4j");

    private static final long MAX_BYTES = 40L * 1024 * 1024;

    @TempDir
    Path temp;

    /** One run of the jar. */
    private record Run(int exit, String out, String err) {
        JsonNode report() {
            return JSON.readTree(out);
        }

        List<String> checks() {
            List<String> checks = new ArrayList<>();
            report().get("checks").forEach(check -> checks.add(check.asString()));
            return checks;
        }

        List<String> codes() {
            List<String> codes = new ArrayList<>();
            report().get("problems")
                    .forEach(problem -> codes.add(problem.get("code").asString()));
            return codes;
        }

        List<String> violationCodes() {
            List<String> codes = new ArrayList<>();
            report().get("problems").forEach(problem -> codes.addAll(violationCodes(problem)));
            return codes;
        }

        /** The violation codes of the problem at {@code index}, in order. */
        List<String> violationCodes(int index) {
            return violationCodes(report().get("problems").get(index));
        }

        /** The violation pointers of the problem at {@code index}, in order. */
        List<String> violationPointers(int index) {
            List<String> pointers = new ArrayList<>();
            JsonNode violations = report().get("problems").get(index).get("violations");
            if (violations != null) {
                violations.forEach(
                        violation -> pointers.add(violation.get("pointer").asString()));
            }
            return pointers;
        }

        private static List<String> violationCodes(JsonNode problem) {
            List<String> codes = new ArrayList<>();
            JsonNode violations = problem.get("violations");
            if (violations != null) {
                violations.forEach(violation -> codes.add(violation.get("code").asString()));
            }
            return codes;
        }
    }

    private static Path property(String name) {
        String value = System.getProperty(name);
        assertThat(value).as("jarTest passes -D%s", name).isNotBlank();
        return Path.of(value);
    }

    private static Path jar() {
        return property("packcheck.jar");
    }

    /** Runs the jar with the toolchain's java in a clean environment, and asserts what every run must hold. */
    private Run run(String... args) throws IOException, InterruptedException {
        Path work = Files.createTempDirectory(temp, "work");
        Path tmp = Files.createDirectories(work.resolve("tmp"));
        Path out = temp.resolve(work.getFileName() + ".out");
        Path err = temp.resolve(work.getFileName() + ".err");
        List<String> command = new ArrayList<>(
                List.of(property("packcheck.java").toString(), "-Djava.io.tmpdir=" + tmp, "-jar", jar().toString()));
        Collections.addAll(command, args);
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(work.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")))
                .redirectOutput(out.toFile())
                .redirectError(err.toFile());
        Map<String, String> environment = builder.environment();
        for (String variable : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH")) {
            environment.remove(variable);
        }
        Process process = builder.start();
        assertThat(process.waitFor(120, TimeUnit.SECONDS))
                .as("pack-check finished within 120 s")
                .isTrue();
        Run run = new Run(
                process.exitValue(),
                Files.readString(out, StandardCharsets.UTF_8),
                Files.readString(err, StandardCharsets.UTF_8));
        for (String never : NEVER_ON_STDERR) {
            assertThat(run.err()).as("standard error of %s", command).doesNotContain(never);
        }
        try (var left = Files.list(tmp)) {
            assertThat(left)
                    .as("the run leaves nothing in its temporary directory")
                    .isEmpty();
        }
        return run;
    }

    private static List<String> lines(String text) {
        return text.lines().toList();
    }

    /** The five example packs under {@code root/packs/}, a config root. */
    private Path configRoot() {
        Path root = temp.resolve("root");
        ExamplePacks.names().forEach(name -> ExamplePacks.copyOf(name, root.resolve("packs")));
        return root;
    }

    private static GoldenSubjects.Subject subject(String key) {
        return GoldenSubjects.subjects().stream()
                .filter(subject -> subject.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no golden subject " + key));
    }

    // Case 1.
    @Test
    void theExamplePacksAsAConfigRootPassEveryCheck() throws Exception {
        Run run = run("--config-root", configRoot().toString(), "--round-trip");

        assertThat(run.exit()).as(run.err()).isZero();
        assertThat(run.checks()).containsExactly("PACK", "CLOSURE", "ROUND_TRIP");
        assertThat(run.report().get("packs")).hasSize(ExamplePacks.names().size());
        assertThat(run.codes()).isEmpty();
        assertThat(lines(run.err())).allSatisfy(line -> assertThat(line).startsWith("warning: "));
    }

    /** Every line of a run's standard error is a warning: a fault is in the report, never on standard error. */
    private static void onlyWarningsOnStandardError(Run run) {
        assertThat(lines(run.err()))
                .as("standard error")
                .allSatisfy(line -> assertThat(line).startsWith("warning: "));
    }

    // Case 2, with the round trip (squad J3).
    @Test
    void aPlantedPackFaultExitsOneWithItsCode() throws Exception {
        Path pack = GoldenSubjects.copy(
                subject("planted/duplicate-artifact-id" + GoldenSubjects.WITH_DESCRIPTOR), temp.resolve("plant"));

        Run run = run("--pack", pack.toString(), "--round-trip");

        assertThat(run.exit()).as(run.err()).isEqualTo(1);
        // CLOSURE is skipped: the plant's composition facts cannot be read (D3).
        assertThat(run.checks()).containsExactly("PACK", "ROUND_TRIP");
        assertThat(run.codes()).as(run.out()).containsExactly("validation_failed", "validation_failed");
        assertThat(run.violationCodes(0)).containsExactly("org_fragment_missing", "duplicate_artifact_id");
        // The round trip refuses the other holder of the id, and writes the one tactic under its id's name.
        assertThat(run.violationCodes(1)).containsExactly("round_trip_red", "round_trip_written_unpaired");
        assertThat(run.err()).isEmpty();
    }

    // Case 3.
    @Test
    void aDependentPackAloneExitsOneOnItsUnknownDependencies() throws Exception {
        Path project = ExamplePacks.copyOf(ExamplePacks.PROJECT, temp.resolve("packs"));

        Run run = run("--pack", project + ":project");

        assertThat(run.exit()).as(run.err()).isEqualTo(1);
        assertThat(run.checks()).containsExactly("PACK", "CLOSURE");
        assertThat(run.codes()).containsExactly("pack_dependency_unknown", "pack_dependency_unknown");
        assertThat(run.err()).isEmpty();
    }

    // Case 4 (squad S2), with the round trip: nothing reads through the link, so its target's text reaches neither
    // stream. The link is one problem: the round trip's reason at a file validate mode already refuses is dropped
    // (WP05), so pack_entry_linked stands alone.
    @Test
    void aPlantedLinkIsRefusedAndItsTargetNeverRead() throws Exception {
        String marker = "pack-check-marker-" + UUID.randomUUID();
        Path target = Files.createDirectories(temp.resolve("outside")).resolve("notes.txt");
        Files.writeString(target, marker + "\n", StandardCharsets.UTF_8);
        Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs"));
        try {
            Files.createSymbolicLink(builtIn.resolve("tactics/linked.tactic.yaml"), target);
        } catch (IOException | UnsupportedOperationException noLinks) {
            assumeTrue(Boolean.getBoolean("charter.requireSymlinks"), "the file system cannot create links");
            throw noLinks;
        }

        Run run = run("--pack", builtIn + ":built-in", "--round-trip");

        assertThat(run.exit()).as(run.err()).isEqualTo(1);
        assertThat(run.codes()).as(run.out()).containsExactly("validation_failed");
        assertThat(run.violationCodes()).containsExactly("pack_entry_linked");
        assertThat(run.out()).contains("tactics/linked.tactic.yaml").doesNotContain(marker);
        assertThat(run.err()).doesNotContain(marker);
        onlyWarningsOnStandardError(run);
    }

    // Case 5, with the round trip (squad J3): the networknt schema validation and the commons paths run in the jar.
    @Test
    void aTacticWithAnUnknownKeyExitsOneWithItsSchemaCode() throws Exception {
        Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs"));
        Path tactic = builtIn.resolve("tactics/usage-examples-sync.tactic.yaml");
        Files.writeString(
                tactic,
                Files.readString(tactic, StandardCharsets.UTF_8) + "not_a_tactic_key: true\n",
                StandardCharsets.UTF_8);

        Run run = run("--pack", builtIn + ":built-in", "--round-trip");

        assertThat(run.exit()).as(run.err()).isEqualTo(1);
        assertThat(run.codes()).as(run.out()).containsExactly("validation_failed", "validation_failed");
        // The schema's code for the tactic, beside the step that delegates to the tactic the pack no longer holds.
        assertThat(run.violationCodes(0))
                .as(run.out())
                .containsExactly("substep_delegation_unresolved", "artifact_schema_error");
        // The round trip refuses the mission type that delegates to the missing tactic: each of its other files is
        // red, and what the write then holds is unpaired. The two files validate mode refuses get no second reason.
        assertThat(run.violationCodes(1))
                .as(run.out())
                .contains("round_trip_red", "round_trip_written_unpaired")
                .containsOnly("round_trip_red", "round_trip_written_unpaired");
        assertThat(run.violationPointers(1))
                .doesNotContainAnyElementsOf(run.violationPointers(0))
                .isNotEmpty();
        assertThat(run.out()).contains("tactics/usage-examples-sync.tactic.yaml: unknown key 'not_a_tactic_key'");
        onlyWarningsOnStandardError(run);
    }

    // Case 6.
    @Test
    void noArgumentsIsAUsageFaultThatExitsTwoWithNothingOnStandardOutput() throws Exception {
        Run run = run();

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.out()).isEmpty();
        assertThat(run.err()).contains("usage: pack-check");
    }

    @Test
    void theJarHoldsNoSpringNoModuleInfoAndNoResolverService() throws IOException {
        List<String> refused = new ArrayList<>();
        try (JarFile jar = new JarFile(jar().toFile())) {
            jar.stream().map(ZipEntry::getName).forEach(name -> {
                boolean prefixed = REFUSED_PREFIXES.stream().anyMatch(name::startsWith);
                if (prefixed
                        || name.endsWith("module-info.class")
                        || name.equals("META-INF/services/java.net.spi.InetAddressResolverProvider")) {
                    refused.add(name);
                }
            });
            assertThat(jar.getEntry("ai/speckitty/charter/packcheck/PackCheckMain.class"))
                    .isNotNull();
            assertThat(jar.getEntry("ai/speckitty/charter/packvalidation/PackCheck.class"))
                    .isNotNull();
        }
        assertThat(refused).isEmpty();
    }

    @Test
    void theManifestNamesTheMainClassAndIsMultiRelease() throws IOException {
        try (JarFile jar = new JarFile(jar().toFile())) {
            Manifest manifest = jar.getManifest();
            Attributes main = manifest.getMainAttributes();
            assertThat(main.getValue("Main-Class")).isEqualTo("ai.speckitty.charter.packcheck.PackCheckMain");
            assertThat(main.getValue("Multi-Release")).isEqualTo("true");
            assertThat(main.getValue("Implementation-Version")).isNotBlank();
            assertThat(jar.isMultiRelease()).isTrue();
        }
    }

    @Test
    void slf4jFindsTheNopProviderInTheJarAlone() throws IOException {
        try (JarFile jar = new JarFile(jar().toFile())) {
            assertThat(text(jar, "META-INF/services/org.slf4j.spi.SLF4JServiceProvider"))
                    .isEqualTo("org.slf4j.helpers.NOP_FallbackServiceProvider\n");
        }
        // The module's own output never holds it (squad J1): deployable's tests put :pack-check beside Logback.
        List<String> providers = new ArrayList<>();
        Collections.list(getClass()
                        .getClassLoader()
                        .getResources("META-INF/services/org.slf4j.spi.SLF4JServiceProvider"))
                .forEach(url -> providers.add(url.toString()));
        assertThat(providers).noneMatch(url -> url.contains("/pack-check/build/"));
    }

    @Test
    void theJarIsUnderFortyMegabytes() throws IOException {
        assertThat(Files.size(jar())).isLessThan(MAX_BYTES);
    }

    @Test
    void everyLibraryCarriesItsLicence() throws IOException {
        Map<String, Integer> files = new TreeMap<>();
        try (JarFile jar = new JarFile(jar().toFile())) {
            assertThat(text(jar, "META-INF/LICENSE")).startsWith("MIT License");
            jar.stream().map(ZipEntry::getName).forEach(name -> {
                if (name.startsWith("META-INF/licenses/")) {
                    String[] parts = name.split("/", -1);
                    if (parts.length >= 4) {
                        files.merge(parts[2], parts[3].isEmpty() ? 0 : 1, Integer::sum);
                    }
                }
            });
            for (String library : libraries(jar).keySet()) {
                assertThat(files).as("licences of %s", library).containsKey(library.replace(':', '-'));
            }
        }
        assertThat(files)
                .isNotEmpty()
                .allSatisfy((library, count) ->
                        assertThat(count).as("META-INF/licenses/%s/", library).isPositive());
    }

    @Test
    void theJarsLibrariesHaveRuntimeClasspathsVersions() throws IOException {
        Map<String, String> runtime = versions(lines(Files.readString(property("packcheck.runtimeVersions"))));
        Map<String, String> inJar;
        Map<String, String> poms = new TreeMap<>();
        try (JarFile jar = new JarFile(jar().toFile())) {
            inJar = libraries(jar);
            for (ZipEntry entry : jar.stream().toList()) {
                String name = entry.getName();
                if (name.startsWith("META-INF/maven/") && name.endsWith("/pom.properties")) {
                    Properties pom = new Properties();
                    try (InputStream in = jar.getInputStream(entry)) {
                        pom.load(in);
                    }
                    poms.put(
                            pom.getProperty("groupId") + ":" + pom.getProperty("artifactId"),
                            pom.getProperty("version"));
                }
            }
        }
        assertThat(inJar).isNotEmpty().containsKeys("tools.jackson.core:jackson-databind", "org.slf4j:slf4j-api");
        assertThat(shifted(runtime, inJar))
                .as("libraries whose version differs from runtimeClasspath's")
                .isEmpty();
        assertThat(shifted(inJar, poms))
                .as("pom.properties that disagree with the jar's list")
                .isEmpty();
        assertThat(inJar.keySet()).containsAll(poms.keySet());
        TreeSet<String> dropped = new TreeSet<>(runtime.keySet());
        dropped.removeAll(inJar.keySet());
        assertThat(dropped)
                .as("what the jar leaves out of runtimeClasspath")
                .isNotEmpty()
                .allMatch(module -> DROPPED_MODULES.contains(module)
                        || DROPPED_GROUPS.contains(module.substring(0, module.indexOf(':'))));
    }

    /** The control of the version check: a shifted version is caught. */
    @Test
    void aShiftedVersionIsCaught() {
        Map<String, String> runtime = Map.of("commons-codec:commons-codec", "1.21.0");

        assertThat(shifted(runtime, Map.of("commons-codec:commons-codec", "1.19.0")))
                .containsExactly("commons-codec:commons-codec: 1.19.0, expected 1.21.0");
        assertThat(shifted(runtime, Map.of("commons-codec:commons-codec", "1.21.0")))
                .isEmpty();
        assertThat(shifted(runtime, Map.of("org.example:extra", "1.0")))
                .containsExactly("org.example:extra: 1.0, expected none");
    }

    @Test
    void twoBuildsOfTheJarAreByteForByteEqual() throws IOException {
        Path again = property("packcheck.jarAgain");

        assertThat(Files.mismatch(jar(), again))
                .as("the first differing byte of %s", again)
                .isEqualTo(-1L);
    }

    /** Each module of {@code actual} whose version is not {@code expected}'s, as {@code module: actual, expected v}. */
    private static List<String> shifted(Map<String, String> expected, Map<String, String> actual) {
        List<String> shifted = new ArrayList<>();
        new TreeMap<>(actual).forEach((module, version) -> {
            String wanted = expected.get(module);
            if (!version.equals(wanted)) {
                shifted.add(module + ": " + version + ", expected " + (wanted == null ? "none" : wanted));
            }
        });
        return shifted;
    }

    private static Map<String, String> versions(List<String> lines) {
        Map<String, String> versions = new TreeMap<>();
        for (String line : lines) {
            if (!line.isBlank()) {
                int last = line.lastIndexOf(':');
                versions.put(line.substring(0, last), line.substring(last + 1));
            }
        }
        return versions;
    }

    private static Map<String, String> libraries(JarFile jar) throws IOException {
        return versions(lines(text(jar, "META-INF/pack-check/libraries.txt")));
    }

    private static String text(JarFile jar, String name) throws IOException {
        ZipEntry entry = jar.getEntry(name);
        assertThat(entry).as(name).isNotNull();
        try (InputStream in = jar.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
