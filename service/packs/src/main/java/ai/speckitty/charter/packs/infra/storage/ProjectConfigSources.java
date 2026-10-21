package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.Fingerprint;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackSources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The inputs the service answers from: the pack files, and {@code config.yaml} and {@code charter.yaml} under the
 * worktree's project configuration directory. The configuration files are digested, never parsed: reading what they
 * say belongs to the operations that need it.
 *
 * <p>The digest feeds, in order: the domain tag {@value #DOMAIN}; the packs' own digest, as one field; and, when a
 * directory is configured, for each configuration file the label {@code project-config/<name>}, a presence byte
 * ({@code 0} absent, {@code 1} present) and, when present, the file's bytes. Every field is length-prefixed
 * ({@link Fingerprint}). The packs' digest is one opaque field and the labels are separate fields under the tag, so
 * no pack name or file path can collide with a label, and an absent file differs from an empty one.
 */
public final class ProjectConfigSources implements PackSources {

    /** Starts every digest, so this scheme's digests never equal a bare pack digest, and a later scheme differs. */
    static final String DOMAIN = "charter-sources/v1";

    private static final String LABEL = "project-config/";
    private static final List<String> FILES = List.of("config.yaml", "charter.yaml");
    private static final byte[] ABSENT = {0};
    private static final byte[] PRESENT = {1};

    private final PackSources packs;
    private final Supplier<Optional<Path>> configDir;

    /**
     * Sources over {@code packs} and, if {@code configDir} names one, a project configuration directory.
     *
     * @param packs the pack files port, for example the pack repository
     * @param configDir the project configuration directory, asked again on every {@link #fingerprint()}; empty when
     *     none is configured, and the digest then covers the packs only
     */
    public ProjectConfigSources(PackSources packs, Supplier<Optional<Path>> configDir) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.configDir = Objects.requireNonNull(configDir, "configDir");
    }

    /**
     * {@inheritDoc}
     *
     * <p>The digest also covers {@code config.yaml} and {@code charter.yaml} of the project configuration directory,
     * with their presence, when one is configured.
     *
     * @throws CharterRefusal with {@link RefusalCode#PACK_UNREADABLE} when the project configuration directory is not
     *     a directory, or one of its two files exists and cannot be read; the detail names no host path
     */
    @Override
    public String fingerprint() {
        Fingerprint fingerprint = Fingerprint.sha256().add(DOMAIN).add(packs.fingerprint());
        configDir.get().ifPresent(directory -> addConfigFiles(fingerprint, directory));
        return fingerprint.value();
    }

    private static void addConfigFiles(Fingerprint fingerprint, Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "the project configuration directory is not a directory");
        }
        for (String name : FILES) {
            fingerprint.add(LABEL + name);
            Optional<byte[]> bytes = read(directory.resolve(name), name);
            if (bytes.isPresent()) {
                fingerprint.add(PRESENT).add(bytes.get());
            } else {
                fingerprint.add(ABSENT);
            }
        }
    }

    private static Optional<byte[]> read(Path file, String name) {
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (NoSuchFileException absent) {
            return Optional.empty();
        } catch (IOException e) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, LABEL + name + " cannot be read", e);
        }
    }
}
