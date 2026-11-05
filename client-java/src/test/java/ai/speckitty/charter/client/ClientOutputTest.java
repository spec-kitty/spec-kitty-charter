package ai.speckitty.charter.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** What the build hands consumers: every source carries the licence header, every class loads on Java 21. */
class ClientOutputTest {

    private static final int JAVA_CLASS_VERSION_OFFSET = 44;

    /** Every file with the suffix under each directory the property names; each directory must exist and hold one. */
    private static List<Path> under(String property, String suffix) {
        String dirs = System.getProperty(property);
        assertThat(dirs).as(property).isNotBlank();
        List<Path> files = new ArrayList<>();
        for (String dir : dirs.split(File.pathSeparator, -1)) {
            Path root = Path.of(dir);
            assertThat(root).as(property).isDirectory();
            List<Path> found =
                    walk(root).filter(p -> p.toString().endsWith(suffix)).toList();
            assertThat(found).as("%s files under %s", suffix, root).isNotEmpty();
            files.addAll(found);
        }
        return files;
    }

    private static Stream<Path> walk(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).toList().stream();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void theWholeClientIsGenerated() {
        List<String> names = under("charter.client.generated-sources", ".java").stream()
                .map(p -> p.getFileName().toString())
                .toList();
        assertThat(names).contains("ApiClient.java", "ApiException.java", "Problem.java", "ServiceApi.java");
        assertThat(names).hasSizeGreaterThan(60);
    }

    @Test
    void noGeneratedModelRefusesAMemberItDoesNotDeclare() throws IOException {
        for (Path source : under("charter.client.generated-sources", ".java")) {
            assertThat(Files.readString(source, StandardCharsets.UTF_8))
                    .as(source.toString())
                    .doesNotContain("is not defined in the");
        }
    }

    @Test
    void everyGeneratedSourceCarriesTheMitHeaderAndTheDoNotEditNote() throws IOException {
        String version = System.getProperty("charter.client.contract-version");
        assertThat(version).isNotBlank();
        for (Path source : under("charter.client.generated-sources", ".java")) {
            String head = Files.readString(source, StandardCharsets.UTF_8)
                    .lines()
                    .limit(6)
                    .reduce("", (a, b) -> a + b + "\n");
            assertThat(head)
                    .as(source.toString())
                    .startsWith("/*\n * SPDX-License-Identifier: MIT\n")
                    .contains("Generated from the charter contract " + version + " ")
                    .contains("Do not edit");
        }
    }

    @Test
    void everyClassFileLoadsOnTheConsumersJavaRelease() throws IOException {
        int release = Integer.parseInt(System.getProperty("charter.client.release", "0"));
        assertThat(release).isEqualTo(21);
        List<Path> classFiles = new ArrayList<>(under("charter.client.generated-classes", ".class"));
        classFiles.addAll(under("charter.client.main-classes", ".class"));
        for (Path classFile : classFiles) {
            assertThat(majorVersion(classFile)).as(classFile.toString()).isEqualTo(JAVA_CLASS_VERSION_OFFSET + release);
        }
    }

    private static int majorVersion(Path classFile) throws IOException {
        try (InputStream in = Files.newInputStream(classFile);
                DataInputStream data = new DataInputStream(in)) {
            assertThat(data.readInt()).isEqualTo(0xCAFEBABE);
            data.readUnsignedShort();
            return data.readUnsignedShort();
        }
    }
}
