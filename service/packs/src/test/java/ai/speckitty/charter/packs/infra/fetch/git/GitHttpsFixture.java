package ai.speckitty.charter.packs.infra.fetch.git;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * Bare repositories served over https by {@code git http-backend} behind a JDK {@link HttpsServer} with a certificate of the
 * test's own (T093): what the {@code git-host} tests of the git source fetch from. The certificate is self-signed, for
 * {@code git.example.org}, {@code localhost} and {@code 127.0.0.1}, and its PEM is the "certificate authority" a remote
 * hands git ({@code http.sslCAInfo}); the host {@code git.example.org} resolves nowhere, so git reaches the server only
 * through the pinned address, as it does in production. A path can be made to answer a redirect, and every request is
 * counted.
 */
final class GitHttpsFixture implements AutoCloseable {

    /** The host the requests name; it is on no name server. */
    static final String HOST = "git.example.org";

    private static final char[] PASSWORD = "changeit".toCharArray();

    private final Path projects;
    private final Path certificate;
    private final HttpsServer server;
    private final Map<String, String> redirects = new ConcurrentHashMap<>();
    private final AtomicInteger requests = new AtomicInteger();

    private GitHttpsFixture(Path projects, Path certificate, HttpsServer server) {
        this.projects = projects;
        this.certificate = certificate;
        this.server = server;
    }

    /** Starts the server on a free loopback port, serving the bare repositories under a directory of {@code parent}. */
    static GitHttpsFixture start(Path parent) throws IOException {
        Path projects = Files.createDirectories(parent.resolve("projects"));
        Path keystore = parent.resolve("fixture.p12");
        Path certificate = parent.resolve("fixture.pem");
        keytool(
                parent,
                "-genkeypair",
                "-alias",
                "fixture",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-validity",
                "2",
                "-dname",
                "CN=" + HOST,
                "-ext",
                "san=dns:" + HOST + ",dns:localhost,ip:127.0.0.1",
                "-ext",
                "bc=ca:true",
                "-keystore",
                keystore.toString(),
                "-storetype",
                "PKCS12",
                "-storepass",
                new String(PASSWORD));
        keytool(
                parent,
                "-exportcert",
                "-rfc",
                "-alias",
                "fixture",
                "-file",
                certificate.toString(),
                "-keystore",
                keystore.toString(),
                "-storepass",
                new String(PASSWORD));
        HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(contextOf(keystore)));
        GitHttpsFixture fixture = new GitHttpsFixture(projects, certificate, server);
        server.createContext("/", fixture::handle);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        return fixture;
    }

    private static SSLContext contextOf(Path keystore) throws IOException {
        try (InputStream in = Files.newInputStream(keystore)) {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(in, PASSWORD);
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, PASSWORD);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keys.getKeyManagers(), null, null);
            return context;
        } catch (GeneralSecurityException failure) {
            throw new IOException("cannot build the fixture's TLS context", failure);
        }
    }

    private static void keytool(Path directory, String... arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(List.of(arguments));
        run(directory, command);
    }

    int port() {
        return server.getAddress().getPort();
    }

    /** The PEM of the fixture's certificate: what a remote trusts. */
    Path certificate() {
        return certificate;
    }

    /** How many requests the server has answered. */
    int requests() {
        return requests.get();
    }

    /** Makes every request whose path starts with {@code from} answer {@code 302} to the same path under {@code to}. */
    void redirect(String from, String to) {
        redirects.put(from, to);
    }

    /** A bare repository at {@code relative} under the served root, which any object may be fetched from by id. */
    Path repository(String relative) throws IOException {
        Path bare = projects.resolve(relative);
        Files.createDirectories(bare);
        git(bare, "init", "-q", "--bare");
        git(bare, "config", "http.receivepack", "false");
        git(bare, "config", "uploadpack.allowAnySHA1InWant", "true");
        return bare;
    }

    /** Commits the files in a work tree and pushes the commit to a ref of the bare repository; returns the commit. */
    String publish(Path bare, Path work, Map<String, String> files, String ref) throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = work.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
        }
        return commit(bare, work, ref);
    }

    /** Commits whatever the work tree holds and pushes it to a ref of the bare repository; returns the commit. */
    String commit(Path bare, Path work, String ref) throws IOException {
        if (!Files.isDirectory(work.resolve(".git"))) {
            git(work, "init", "-q");
        }
        git(work, "add", "-A");
        return commitIndex(bare, work, ref);
    }

    /** Commits what the index of the work tree holds, which a test may have set by hand, and pushes it; returns the commit. */
    String commitIndex(Path bare, Path work, String ref) throws IOException {
        git(
                work,
                "-c",
                "user.name=Fixture",
                "-c",
                "user.email=fixture@example.org",
                "-c",
                "commit.gpgsign=false",
                "commit",
                "-q",
                "-m",
                "fixture",
                "--allow-empty");
        git(work, "push", "-q", bare.toString(), "HEAD:" + ref);
        return git(bare, "rev-parse", "--verify", ref + "^{commit}").strip();
    }

    /** The remote of the tests: the canonical URL's path under this server, trusting this server's certificate. */
    GitRemote remote(boolean pinned, boolean trusted) {
        return (repository, address) -> {
            URI uri = URI.create(repository.url());
            String location = "https://" + HOST + ":" + port() + uri.getRawPath();
            List<String> pins = new ArrayList<>();
            if (pinned) {
                pins.add("http.curloptResolve=" + HOST + ":" + port() + ":127.0.0.1");
            }
            if (trusted) {
                pins.add("http.sslCAInfo=" + certificate);
            }
            return new GitRemote.Target(location, new GitTransport(pins, Map.of()));
        };
    }

    /** Runs the host git for a fixture with the user's and the system's configuration out of it; returns its stdout. */
    static String git(Path directory, String... arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        return run(directory, command);
    }

    private static String run(Path directory, List<String> command) throws IOException {
        ProcessBuilder builder =
                new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        Process process = builder.start();
        try {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(120, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IOException(String.join(" ", command) + " failed: " + output);
            }
            return output;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", interrupted);
        } finally {
            process.destroyForcibly();
        }
    }

    // ---- the server ----

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        try (exchange) {
            String path = exchange.getRequestURI().getRawPath();
            for (Map.Entry<String, String> redirect : redirects.entrySet()) {
                if (path.startsWith(redirect.getKey())) {
                    exchange.getResponseHeaders()
                            .add(
                                    "Location",
                                    "https://" + HOST + ":" + port() + redirect.getValue()
                                            + path.substring(redirect.getKey().length()));
                    exchange.sendResponseHeaders(302, -1);
                    return;
                }
            }
            cgi(exchange, path);
        } catch (IOException | RuntimeException failure) {
            throw new UncheckedIOException(new IOException("the fixture could not answer", failure));
        }
    }

    /** Answers the request with {@code git http-backend}, the CGI that ships with git. */
    private void cgi(HttpExchange exchange, String path) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        ProcessBuilder builder = new ProcessBuilder("git", "http-backend");
        Map<String, String> environment = builder.environment();
        environment.clear();
        environment.put("PATH", System.getenv("PATH"));
        environment.put("GIT_PROJECT_ROOT", projects.toString());
        environment.put("GIT_HTTP_EXPORT_ALL", "1");
        environment.put("GIT_CONFIG_GLOBAL", "/dev/null");
        environment.put("GIT_CONFIG_NOSYSTEM", "1");
        environment.put("REQUEST_METHOD", exchange.getRequestMethod());
        environment.put("PATH_INFO", path);
        environment.put(
                "QUERY_STRING",
                exchange.getRequestURI().getRawQuery() == null
                        ? ""
                        : exchange.getRequestURI().getRawQuery());
        environment.put("REMOTE_ADDR", "127.0.0.1");
        environment.put("CONTENT_LENGTH", Integer.toString(body.length));
        header(exchange, "Content-Type", "CONTENT_TYPE", environment);
        header(exchange, "Content-Encoding", "HTTP_CONTENT_ENCODING", environment);
        header(exchange, "Git-Protocol", "HTTP_GIT_PROTOCOL", environment);
        Process process = builder.start();
        try {
            Thread writer = Thread.ofVirtual().start(() -> feed(process, body));
            byte[] output = process.getInputStream().readAllBytes();
            writer.join();
            process.waitFor(60, TimeUnit.SECONDS);
            respond(exchange, output);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", interrupted);
        } finally {
            process.destroyForcibly();
        }
    }

    private static void header(HttpExchange exchange, String name, String variable, Map<String, String> environment) {
        String value = exchange.getRequestHeaders().getFirst(name);
        if (value != null) {
            environment.put(variable, value);
        }
    }

    private static void feed(Process process, byte[] body) {
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(body);
        } catch (IOException closedEarly) {
            // The CGI answered without reading the body.
        }
    }

    /** Turns the CGI's output, headers then a blank line then the body, into the response. */
    private static void respond(HttpExchange exchange, byte[] output) throws IOException {
        int status = 200;
        int at = 0;
        while (at < output.length) {
            int end = at;
            while (end < output.length && output[end] != '\n') {
                end++;
            }
            String line = new String(output, at, end - at, StandardCharsets.ISO_8859_1).stripTrailing();
            at = end + 1;
            if (line.isEmpty()) {
                break;
            }
            int colon = line.indexOf(':');
            String name = line.substring(0, colon);
            String value = line.substring(colon + 1).strip();
            if (name.equalsIgnoreCase("Status")) {
                status = Integer.parseInt(value.substring(0, 3));
            } else {
                exchange.getResponseHeaders().add(name, value);
            }
        }
        ByteArrayOutputStream rest = new ByteArrayOutputStream();
        rest.write(output, Math.min(at, output.length), Math.max(0, output.length - at));
        byte[] payload = rest.toByteArray();
        exchange.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);
        if (payload.length > 0) {
            exchange.getResponseBody().write(payload);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
    }
}
