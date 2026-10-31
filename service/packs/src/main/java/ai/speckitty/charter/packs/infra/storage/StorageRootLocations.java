package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackLevel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The packs of the pack domain, {@code <config-root>/packs}: every directory directly under it is a pack, read and
 * written in place and named after its directory. Its level is the one its own {@code pack.yaml} declares
 * ({@code pack_level}), so built-in, org and project packs live side by side; a pack without a readable declaration is
 * an org pack, and the pack policy reports what is wrong with it.
 *
 * <p>The directory is listed on every call, so a pack copied in or removed while the service runs is seen on the next
 * read; a domain directory that does not exist yet holds no packs. A domain that is itself a symbolic link is followed
 * (#285): its packs are read-only when it resolves outside the configuration root, and it holds no packs, warned once,
 * when it resolves to no directory, to the configuration root or a directory above it, or into the activations domain. A symbolic link to a directory
 * is a pack named after
 * the link (#174, ADR-0038 decision 3 as amended 2026-10-31): the store reads it through the link, and a commit stages
 * and swaps beside the link's real target, so the link stays a link ({@link PackCommitStore}). Links below a pack's
 * root are not followed. These entries are never packs:
 *
 * <ul>
 *   <li>hidden entries, among them the {@code .pack-commit-*} staging directory of {@link PackCommitStore};
 *   <li>a {@code <name>.pack-old-*} entry, the aside copy a commit leaves when its swap is interrupted;
 *   <li>a file;
 *   <li>a link that resolves to no directory (a dangling link, a link to a file), or to the pack domain, the
 *       configuration root or a directory above either;
 *   <li>a link to a directory in the configuration root outside the pack domain (the activations domain);
 *   <li>two or more entries whose directories overlap, one directory or one inside the other (two links to one
 *       target, a link to a sibling pack or into it): a commit to one would copy and replace the other's files, and
 *       the per-pack write lock would not serialise them, so none of them is served.
 * </ul>
 *
 * <p>A refused entry is logged as a warning once for each reason, naming the entry and why, not on every listing; an
 * entry that is served again is warned again if it is refused later.
 */
public final class StorageRootLocations implements DomainRepositoryService.Locations {

    private static final String ASIDE_MARKER = ".pack-old-";

    private static final Logger LOG = LoggerFactory.getLogger(StorageRootLocations.class);

    private final Path packs;

    /** Each refused entry's reason, as last warned, so a listing on every read warns once per entry and reason. */
    private final Map<String, String> reported = new ConcurrentHashMap<>();

    /** Why the pack domain itself holds no packs, as last warned; null while it is served. */
    private final AtomicReference<@Nullable String> domainReported = new AtomicReference<>();

    /**
     * The packs under {@code packs}.
     *
     * @param packs the pack domain's directory; it need not exist yet
     */
    public StorageRootLocations(Path packs) {
        this.packs = packs.toAbsolutePath().normalize();
    }

    @Override
    public List<PackLocation> all() {
        Optional<Path> domain = realDomain();
        if (domain.isEmpty()) {
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>();
        try (Stream<Path> children = Files.list(packs)) {
            Path configRoot = realConfigRoot();
            for (Path entry :
                    children.filter(StorageRootLocations::isPack).sorted().toList()) {
                directoryOf(entry, domain.get(), configRoot)
                        .ifPresent(real -> candidates.add(new Candidate(entry, real)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list the pack domain", e);
        }
        List<PackLocation> out = new ArrayList<>();
        for (Candidate candidate : candidates) {
            List<String> overlapping = candidates.stream()
                    .filter(other -> !other.entry().equals(candidate.entry()) && candidate.overlaps(other))
                    .map(other -> nameOf(other.entry()))
                    .toList();
            if (overlapping.isEmpty()) {
                reported.remove(nameOf(candidate.entry()));
                out.add(location(candidate.entry()));
            } else {
                refuse(candidate.entry(), "overlaps " + overlapping + " (the same or a nested directory)");
            }
        }
        out.sort(Comparator.comparing((PackLocation location) -> location.level() != PackLevel.BUILT_IN)
                .thenComparing(PackLocation::name));
        return List.copyOf(out);
    }

    /**
     * A pack whose real directory is outside the configuration root, a link's target elsewhere or a pack in a domain
     * linked from elsewhere, is read-only through the service: a commit would replace a directory the service does not
     * own (ADR-0038 as amended 2026-10-31).
     */
    @Override
    public Optional<String> whyReadOnly(Path realDirectory) {
        try {
            if (realDirectory.startsWith(realConfigRoot())) {
                return Optional.empty();
            }
        } catch (IOException unresolved) {
            // A configuration root that cannot be resolved owns nothing to write.
        }
        return Optional.of("is linked from outside the configuration root; it is read-only through the service");
    }

    /**
     * The pack domain's real directory, or empty when it holds no packs. A domain that does not exist yet, or is a
     * file, holds none, silently. A domain that is a link is followed (#285, ADR-0038 as amended 2026-10-31); one that
     * resolves to no directory, to the configuration root or a directory above it, or into the activations domain, holds
     * none and is warned once.
     */
    private Optional<Path> realDomain() {
        boolean link = Files.isSymbolicLink(packs);
        if (!link && !Files.isDirectory(packs, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        Path real;
        Optional<String> why;
        try {
            real = packs.toRealPath();
            why = whyNotADomain(real);
        } catch (IOException unresolved) {
            if (!link) {
                // A directory removed while it is listed holds no packs, as one that does not exist yet.
                return Optional.empty();
            }
            real = packs;
            why = Optional.of("is a link that does not resolve");
        }
        if (why.isPresent()) {
            if (!why.get().equals(domainReported.getAndSet(why.get()))) {
                LOG.warn("pack domain {} {}; no pack is served", packs, why.get());
            }
            return Optional.empty();
        }
        domainReported.set(null);
        return Optional.of(real);
    }

    /** Why the domain's real directory holds no packs, or empty when it may. */
    private Optional<String> whyNotADomain(Path real) throws IOException {
        if (!Files.isDirectory(real)) {
            return Optional.of("resolves to " + real + ", which is not a directory");
        }
        Path configRoot = realConfigRoot();
        if (configRoot.startsWith(real)) {
            return Optional.of("resolves to " + real + ", which holds the configuration root");
        }
        if (real.startsWith(configRoot.resolve(ConfigRoot.ACTIVATIONS))) {
            return Optional.of("resolves to " + real + ", in the activations domain");
        }
        return Optional.empty();
    }

    /** The configuration root's real path: the pack domain's parent, as {@code ConfigRoot.packs()} places it. */
    private Path realConfigRoot() throws IOException {
        return Objects.requireNonNullElse(packs.getParent(), packs).toRealPath();
    }

    /** An entry that resolves to a directory, with that directory's real path. */
    private record Candidate(Path entry, Path real) {
        /** Whether the two resolve to one directory, or one's directory holds the other's. */
        boolean overlaps(Candidate other) {
            return real.startsWith(other.real) || other.real.startsWith(real);
        }
    }

    /**
     * The real directory an entry is a pack of: a directory's own, a link's target's. Empty for a file, an entry that
     * vanished, and a link that resolves to no directory, to one that holds the pack domain, or to one in the
     * configuration root outside the pack domain; such a link is refused.
     */
    private Optional<Path> directoryOf(Path entry, Path domain, Path configRoot) {
        boolean link = Files.isSymbolicLink(entry);
        if (!link && !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        Path real;
        try {
            real = entry.toRealPath();
        } catch (IOException unresolved) {
            if (link) {
                refuse(entry, "is a link that does not resolve");
            }
            return Optional.empty();
        }
        Optional<String> why = whyNotAPack(real, domain, configRoot);
        why.ifPresent(reason -> refuse(entry, reason));
        return why.isPresent() ? Optional.empty() : Optional.of(real);
    }

    /**
     * Why a link's real target is no pack directory, or empty when it may be one. {@code domain} and {@code configRoot}
     * are real paths, so a domain linked from elsewhere is checked where it lives.
     */
    private static Optional<String> whyNotAPack(Path real, Path domain, Path configRoot) {
        if (!Files.isDirectory(real)) {
            return Optional.of("resolves to " + real + ", which is not a directory");
        }
        if (domain.startsWith(real)) {
            return Optional.of("resolves to " + real + ", which holds the pack domain");
        }
        // Belt-and-suspenders: the pack domain is always under the configuration root, so a target that holds the
        // configuration root also holds the domain and is already refused above; this guards the invariant directly.
        if (configRoot.startsWith(real)) {
            return Optional.of("resolves to " + real + ", which holds the configuration root");
        }
        if (real.startsWith(configRoot) && !real.startsWith(domain)) {
            return Optional.of("resolves to " + real + ", in the configuration root but outside the pack domain");
        }
        return Optional.empty();
    }

    /** Warns that an entry is not served, once for each reason it is refused for. */
    private void refuse(Path entry, String reason) {
        String name = nameOf(entry);
        if (!reason.equals(reported.put(name, reason))) {
            LOG.warn("pack entry {} {}; it is not served", name, reason);
        }
    }

    /** The entry's name under the pack domain, the name a pack is served under. */
    private static String nameOf(Path entry) {
        return String.valueOf(entry.getFileName());
    }

    private static boolean isPack(Path directory) {
        String name = String.valueOf(directory.getFileName());
        return !name.startsWith(".") && !name.contains(ASIDE_MARKER);
    }

    private static PackLocation location(Path directory) {
        return new PackLocation(String.valueOf(directory.getFileName()), declaredLevel(directory), directory);
    }

    /** The {@code pack_level} the pack's {@code pack.yaml} declares; org when there is no readable declaration. */
    private static PackLevel declaredLevel(Path directory) {
        Path packYaml = directory.resolve(PackFileSet.PACK_YAML);
        if (!Files.isRegularFile(packYaml, LinkOption.NOFOLLOW_LINKS)) {
            return PackLevel.ORG;
        }
        try {
            Object document = Yaml.load(Files.readString(packYaml, StandardCharsets.UTF_8));
            if (document instanceof Map<?, ?> map && map.get("pack_level") instanceof String token) {
                return PackLevel.fromToken(token).orElse(PackLevel.ORG);
            }
            return PackLevel.ORG;
        } catch (IOException | RuntimeException unreadable) {
            return PackLevel.ORG;
        }
    }
}
