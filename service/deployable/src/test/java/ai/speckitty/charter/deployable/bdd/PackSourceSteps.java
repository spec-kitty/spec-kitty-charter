package ai.speckitty.charter.deployable.bdd;

import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.infra.api.PackSourceSwitch;
import ai.speckitty.charter.packs.infra.fetch.PackSourceProperties;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

/**
 * The source seams of contracts/charter/scenarios/README.md ("Registration steps", one seam per source kind): a stub https
 * exchange that serves archives to the hosts a scenario allowed (never the network), and a directory under the configured
 * filesystem root that holds the files of a named archive. Allowing a host, or filling the root, also switches the kind's
 * own flag on, as an operator does; the flag steps move it. Every seam is put back at its default when the scenario ends.
 */
public class PackSourceSteps {

    private static final String GZIP = "application/gzip";

    private final StubArchiveExchange exchange;
    private final PackSourceSwitch switches;
    private final PackSourceProperties properties;
    private final PackRegistrationSteps registration;

    /**
     * The steps over the stub exchange and the source switches.
     *
     * @param exchange the stub exchange, the {@code @Primary} https exchange of the scenarios' context
     * @param switches whether each source kind is on
     * @param properties the bound source configuration, whose filesystem root the scenarios fill
     * @param registration the registration steps, whose archives these steps serve
     */
    public PackSourceSteps(
            StubArchiveExchange exchange,
            PackSourceSwitch switches,
            PackSourceProperties properties,
            PackRegistrationSteps registration) {
        this.exchange = exchange;
        this.switches = switches;
        this.properties = properties;
        this.registration = registration;
    }

    @Before(order = 0)
    public void everySourceIsOffAndEmpty() {
        putTheSeamsBack();
    }

    /** Before the scenario's packs are deleted, like the registration seams: a job may still be reading the root. */
    @After(order = 20000)
    public void putTheSeamsBackWhenItEnds() {
        putTheSeamsBack();
    }

    private void putTheSeamsBack() {
        exchange.reset();
        for (PackSourceKind kind : PackSourceKind.values()) {
            switches.set(kind, false);
        }
        emptyTheRoot();
    }

    @Given("the https host {string} is allowed")
    public void hostAllowed(String host) {
        exchange.allow(host);
        switches.set(PackSourceKind.HTTPS_ARCHIVE, true);
    }

    @Given("the https host {string} serves {string} with the archive {string}")
    public void hostServes(String host, String url, String archive) {
        exchange.serve(URI.create(url), registration.archives().tarGz(archive), GZIP);
    }

    @Given("the filesystem source root holds the directory {string} with the archive {string}")
    public void rootHolds(String directory, String archive) {
        Path root = root();
        for (Map.Entry<String, String> file :
                registration.archives().files(archive).entrySet()) {
            write(root.resolve(directory).resolve(file.getKey()), file.getValue());
        }
        switches.set(PackSourceKind.FILESYSTEM, true);
    }

    @Given("the filesystem source is switched off")
    public void filesystemOff() {
        switches.set(PackSourceKind.FILESYSTEM, false);
    }

    @Given("the filesystem source is switched on")
    public void filesystemOn() {
        switches.set(PackSourceKind.FILESYSTEM, true);
    }

    private Path root() {
        Path root = properties.filesystem().root();
        if (root == null) {
            throw new IllegalStateException("the scenarios' context sets no charter.pack-sources.filesystem.root");
        }
        return root;
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void emptyTheRoot() {
        Path root = properties.filesystem().root();
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    if (!directory.equals(root)) {
                        Files.delete(directory);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
