package ai.speckitty.charter.deployable.bdd;

import ai.speckitty.charter.packs.infra.fetch.git.GitHostResolver;
import ai.speckitty.charter.packs.infra.fetch.git.GitRemote;
import ai.speckitty.charter.packs.infra.fetch.git.GitRepository;
import ai.speckitty.charter.packs.infra.fetch.git.GitTransport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The git seam of the contract scenarios (T093, decision DM-01M6RN677S4XN4WR8ZVMAW777G): the repositories a scenario
 * describes are real bare repositories, built with the host git in a directory of the scenarios' own, and the git source
 * runs against them for real, argument list, environment and deadline, tree and blobs included. Only where git is sent
 * differs from production: a URL the scenario named, {@code https://git.example.org/acme/packs.git}, is mapped to the bare
 * repository's directory under the {@code file} transport, which the production policy refuses (its pins allow https
 * alone), and the host the scenario allowed resolves to a global address, so the host check and the pin of the address are
 * exercised without a name server. The real https transport, with a test certificate authority and a redirect, is the
 * {@code git-host} tests of {@code packs} ({@code GitHostTest}), which need git 2.50.1 or later.
 *
 * <p>It is the {@link GitRemote} and the {@link GitHostResolver} of the scenarios' Spring context.
 */
public final class GitFixtureServer implements GitRemote, GitHostResolver {

    /** A global address, so the address check passes without a lookup (TEST-NET ranges are refused). */
    private static final InetAddress GLOBAL = literal("93.184.215.14");

    private final Set<String> allowed = ConcurrentHashMap.newKeySet();
    private final Map<String, Path> repositories = new ConcurrentHashMap<>();
    private final Map<String, String> commits = new LinkedHashMap<>();
    private Path root;

    private static InetAddress literal(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Allows a host: it resolves, until the scenario ends.
     *
     * @param host the host
     */
    public void allow(String host) {
        allowed.add(host.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Describes a repository: the files of an archive committed at the root, and the ref that points at the commit.
     *
     * @param url the URL a request names
     * @param files the files, by path
     * @param ref {@code refs/heads/...} or {@code refs/tags/...}
     */
    public synchronized void publish(String url, Map<String, String> files, String ref) {
        try {
            if (root == null) {
                root = Files.createTempDirectory("charter-scenario-git-");
            }
            Path work = Files.createTempDirectory(root, "work-");
            for (Map.Entry<String, String> file : files.entrySet()) {
                Path target = work.resolve(file.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
            }
            Path bare = root.resolve("bare-" + repositories.size() + ".git");
            run(root, "init", "-q", "--bare", bare.toString());
            run(work, "init", "-q");
            run(work, "add", "-A");
            run(work, "-c", "user.name=Scenario", "-c", "user.email=scenario@example.org", "-c", "commit.gpgsign=false",
                    "commit", "-q", "-m", "the archive");
            run(work, "push", "-q", bare.toString(), "HEAD:" + ref);
            repositories.put(url, bare);
            commits.put(ref, run(bare, "rev-parse", "--verify", ref + "^{commit}").strip());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * The commit a ref of the repositories described points at.
     *
     * @param ref the ref
     * @return the commit, 40 hex digits
     */
    public synchronized String commitOf(String ref) {
        String commit = commits.get(ref);
        if (commit == null) {
            throw new IllegalStateException("no repository was described with the ref " + ref);
        }
        return commit;
    }

    /** Forgets every host and repository and removes the directories, so each scenario starts as empty as the default. */
    public synchronized void reset() {
        allowed.clear();
        repositories.clear();
        commits.clear();
        if (root != null) {
            remove(root);
            root = null;
        }
    }

    @Override
    public List<InetAddress> resolve(String host) throws UnknownHostException {
        if (!allowed.contains(host.toLowerCase(java.util.Locale.ROOT))) {
            throw new UnknownHostException(host);
        }
        return List.of(GLOBAL);
    }

    @Override
    public Target target(GitRepository repository, InetAddress address) {
        Path bare = repositories.get(repository.url());
        String location = bare == null ? "file:///nonexistent-charter-scenario-repository" : "file://" + bare;
        return new Target(location, new GitTransport(List.of("protocol.file.allow=always"), Map.of("GIT_ALLOW_PROTOCOL", "file")));
    }

    /** Runs the host git for a fixture, with the user's and the system's configuration out of it; returns its stdout. */
    private static String run(Path directory, String... arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        Process process = builder.start();
        try {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IOException("git " + String.join(" ", arguments) + " failed: " + output);
            }
            return output;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while running git", interrupted);
        } finally {
            process.destroyForcibly();
        }
    }

    private static void remove(Path directory) {
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
