package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.GuideFetchRefusal;
import ai.speckitty.charter.packs.application.GuideFetcher;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackWriteStore;
import ai.speckitty.charter.packs.application.PackWriteStore.Change;
import ai.speckitty.charter.packs.application.PermissiveWritePolicyGate;
import ai.speckitty.charter.packs.application.ToolguideReadService;
import ai.speckitty.charter.packs.application.ToolguideRegistrationService;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.domain.ToolguideRepository;
import ai.speckitty.charter.packs.infra.storage.MarkdownGuideFormat;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The HTTP contract of the toolguide routes: status, headers and body shape, the exactly-one rule of {@code guide} and
 * {@code guideUri}, and the inline size cap (FR-010, NFR-005). The guide is read by the real Markdown adapter; the
 * fetch is a stub, so no test opens a socket.
 */
class ToolguideControllerTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final String DIGEST = "sha256:" + "7".repeat(64);
    private static final String FINGERPRINT = "Charter-Source-Fingerprint";
    /** A valid guide, JSON-escaped for a request body. */
    private static final String GUIDE = "## Overview\\n\\nHow to use the tool.";

    private static final String SERVED_URI = "https://guides.example.org/make.guide.md";
    private static final String REFUSED_URI = "https://elsewhere.example.org/make.guide.md";
    private static final int MIB = 1024 * 1024;

    private final FakeStore store = new FakeStore();
    private final StubFetcher fetcher = new StubFetcher();
    private final MarkdownGuideFormat format = new MarkdownGuideFormat();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ToolguideController(
                    new ToolguideReadService(store, store, new ConsistentRead(() -> DIGEST), store),
                    new ToolguideRegistrationService(store, store, new PermissiveWritePolicyGate(), fetcher, format),
                    format))
            .setControllerAdvice(new RefusalHandler())
            .build();

    ToolguideControllerTest() {
        store.put(toolguide("git", "2026-03-03"));
    }

    @Test
    void listsThePacksToolguidesAsJson() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/toolguides"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("git"))
                .andExpect(jsonPath("$[0].tool").value("git"))
                .andExpect(jsonPath("$[0].guide").value("# How to use git\n\n## Overview\n\nHow to use the tool.\n"))
                .andExpect(jsonPath("$[0].guidePath").doesNotExist())
                .andExpect(jsonPath("$[0].lastUpdated").value("2026-03-03"));
    }

    @Test
    void returnsOneToolguideWithAnEtag() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/toolguides/git"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.id").value("git"))
                .andExpect(jsonPath("$.summary").value("Using git under governance."))
                .andExpect(jsonPath("$.guide").value("# How to use git\n\n## Overview\n\nHow to use the tool.\n"));
    }

    @Test
    void anAbsentToolguideIsA404Problem() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/toolguides/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("toolguide_not_found"));
    }

    @Test
    void registersANewToolguideWithLocationAndEtag() throws Exception {
        mvc.perform(post("/api/v1/packs/pack-a/toolguides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("make")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/packs/pack-a/toolguides/make"))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.id").value("make"));
    }

    @Test
    void aDuplicateIdPostIsA409() throws Exception {
        mvc.perform(post("/api/v1/packs/pack-a/toolguides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("git")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("registration_conflict"));
    }

    @Test
    void aPutReplacesAndAnswers200() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("git")))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"));
    }

    @Test
    void aPutCreatesAndAnswers201() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/make")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("make")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.id").value("make"));
    }

    @Test
    void aStaleIfMatchOnAPutIsA412PlainProblem() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/git")
                        .header("If-Match", "sha256:" + "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("git")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void aMalformedBodyIsA422() throws Exception {
        // Well-formed JSON, DTO-valid, but domain-invalid: schemaVersion breaks the toolguide's ^1\.0$ invariant.
        String bad = "{\"id\":\"make\",\"schemaVersion\":\"9.9\",\"tool\":\"make\",\"title\":\"Make\"," + "\"guide\":\""
                + GUIDE + "\",\"summary\":\"Using make.\"}";
        mvc.perform(post("/api/v1/packs/pack-a/toolguides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    // ---- FR-010/011: the guide as content, with its H1 written from the title ------------------------------------

    @Test
    void aGuideWithoutAnH1IsStoredAndAnsweredWithTheH1FromTheTitle() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/make")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("make", guideField(GUIDE + "\\n\\n## Usage\\n\\nRun make."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guide")
                        .value("# How to use make\n\n## Overview\n\nHow to use the tool.\n\n## Usage\n\nRun make.\n"));
        mvc.perform(get("/api/v1/packs/pack-a/toolguides/make"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guide")
                        .value("# How to use make\n\n## Overview\n\nHow to use the tool.\n\n## Usage\n\nRun make.\n"));
    }

    @Test
    void anH1EqualToTheTitleIsAcceptedWhileADifferentOneIsRefused() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/make")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("make", guideField("# Something else\\n\\n" + GUIDE))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Something else")));
        assertThat(store.commits).isZero();

        mvc.perform(put("/api/v1/packs/pack-a/toolguides/make")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("make", guideField("# How to use make\\n\\n" + GUIDE))))
                .andExpect(status().isCreated());
    }

    @Test
    void aPutReplacesTheGuideWithTheOneItSendsNeverWithTheSummary() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("git", guideField("## Overview\\n\\nThe second guide."))))
                .andExpect(status().isOk());

        Toolguide stored = store.load(PACK, new ToolguideIdentifier("git")).orElseThrow();
        assertThat(stored.guide()).isEqualTo(Guide.of("The second guide."));
    }

    @Test
    void aGuideWithoutAnOverviewIsA422ThatNamesTheRule() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/make")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("make", guideField("## Usage\\n\\nRun make."))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.detail").value("The guide has no ## Overview."));
        assertThat(store.commits).isZero();
    }

    @Test
    void aTitleThatCannotBeAnH1IsA422NotAServerError() throws Exception {
        String body = "{\"schemaVersion\":\"1.0\",\"tool\":\"make\",\"title\":\"How to\\nuse make\","
                + "\"summary\":\"Using make.\"," + guideField(GUIDE) + "}";
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/make")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"));
        assertThat(store.commits).isZero();
    }

    // ---- FR-010: exactly one of guide and guideUri ----------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "",
                "\"guide\":null",
                "\"guideUri\":null",
                "\"guide\":null,\"guideUri\":null",
                "\"guide\":\"\"",
                "\"guideUri\":\"\"",
                "\"guide\":\"" + GUIDE + "\",\"guideUri\":\"" + SERVED_URI + "\"",
                "\"guide\":\"" + GUIDE + "\",\"guideUri\":\"\"",
                "\"guide\":\"\",\"guideUri\":\"" + SERVED_URI + "\""
            })
    void aRegistrationWithoutExactlyOneNonEmptyGuideOrGuideUriIsA422(String guideFields) throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/make")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("make", guideFields)))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation_failed"));
        assertThat(store.commits).as("a refused registration commits nothing").isZero();
        assertThat(fetcher.fetched).as("a refused registration fetches nothing").isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"\"guide\":\"" + GUIDE + "\"", "\"guideUri\":\"" + SERVED_URI + "\""})
    void aRegistrationWithExactlyOneOfGuideOrGuideUriIsAccepted(String guideFields) throws Exception {
        // The positive control of the matrix above, on the same fixture.
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/make")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("make", guideFields)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guide").value("# How to use make\n\n## Overview\n\nHow to use the tool.\n"));
    }

    @Test
    void aGuideUriIsFetchedAgainOnEveryPut() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/v1/packs/pack-a/toolguides/make")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("make", "\"guideUri\":\"" + SERVED_URI + "\"")))
                    .andExpect(status().is2xxSuccessful());
        }
        assertThat(fetcher.fetched).containsExactly(URI.create(SERVED_URI), URI.create(SERVED_URI));
    }

    @Test
    void aRefusedFetchIsA422ThatNamesTheReasonAndTheHostButNotThePath() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/make")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("make", "\"guideUri\":\"" + REFUSED_URI + "\"")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("HOST_NOT_ALLOWED"),
                                org.hamcrest.Matchers.containsString("elsewhere.example.org"),
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("make.guide.md")))));
        assertThat(store.commits).isZero();
    }

    // ---- NFR-005: the inline guide is capped at 1 MiB of UTF-8 ----------------------------------------------------

    @Test
    void anInlineGuideOfExactly1MibIsAcceptedWhileOneByteMoreIsRefused() throws Exception {
        String prefix = "## Overview\n\n";
        String exact = prefix + "a".repeat(MIB - prefix.length());
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/make")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("make", guideField(escaped(exact + "a")))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1 MiB")));
        assertThat(store.commits).isZero();

        mvc.perform(put("/api/v1/packs/pack-a/toolguides/make")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("make", guideField(escaped(exact)))))
                .andExpect(status().isCreated());
    }

    @Test
    void theCapCountsUtf8BytesNotCharacters() throws Exception {
        // Fewer than 1 Mi characters, more than 1 MiB of UTF-8: each "é" is two bytes.
        String guide = "## Overview\n\n" + "é".repeat(MIB / 2);
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/make")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("make", guideField(escaped(guide)))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1 MiB")));
    }

    private static String body(String id) {
        return body(id, guideField(GUIDE));
    }

    private static String body(String id, String guideFields) {
        return "{\"id\":\"" + id + "\",\"schemaVersion\":\"1.0\",\"tool\":\"" + id + "\",\"title\":\"How to use " + id
                + "\",\"summary\":\"Using " + id + " under governance.\"" + (guideFields.isEmpty() ? "" : ",")
                + guideFields + "}";
    }

    /** A {@code guide} member; the Markdown must already be JSON-escaped. */
    private static String guideField(String escapedMarkdown) {
        return "\"guide\":\"" + escapedMarkdown + "\"";
    }

    private static String escaped(String markdown) {
        return markdown.replace("\n", "\\n");
    }

    private static Toolguide toolguide(String id, String lastUpdated) {
        return Toolguide.builder()
                .id(new ToolguideIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .tool(id)
                .title("How to use " + id)
                .guide(Guide.of("How to use the tool."))
                .summary("Using " + id + " under governance.")
                .lastUpdated(lastUpdated)
                .build();
    }

    /** Serves {@link #SERVED_URI} and refuses every other host, as the empty-by-default allowlist would. */
    private static final class StubFetcher implements GuideFetcher {
        private final List<URI> fetched = new ArrayList<>();

        @Override
        public String fetch(URI uri) throws GuideFetchRefusal {
            fetched.add(uri);
            if (!uri.equals(URI.create(SERVED_URI))) {
                throw new GuideFetchRefusal(GuideFetchRefusal.Reason.HOST_NOT_ALLOWED, uri.getHost(), "not allowed");
            }
            return "## Overview\n\nHow to use the tool.";
        }
    }

    /** One fake backing the read port, the toolguide port and the write-path port over a mutable pack. */
    private static final class FakeStore implements PackRepository, ToolguideRepository, PackWriteStore {
        private Pack pack = Pack.builder()
                .id(PACK)
                .descriptor(new PackDescriptor(
                        "pack-a", "1.0.0", Optional.empty(), "pack-a", Optional.empty(), Optional.empty()))
                .build();
        private int commits;

        void put(Toolguide toolguide) {
            Pack.Builder builder = Pack.builder().id(pack.id()).descriptor(pack.descriptor());
            pack.toolguides().stream()
                    .filter(t -> !t.id().equals(toolguide.id()))
                    .forEach(builder::toolguide);
            builder.toolguide(toolguide);
            pack = builder.build();
        }

        @Override
        public Pack load(PackIdentifier id) {
            if (!PACK.equals(id)) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + id.value());
            }
            return pack;
        }

        @Override
        public void save(Toolguide toolguide) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Toolguide> load(PackIdentifier id, ToolguideIdentifier toolguide) {
            if (!PACK.equals(id)) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + id.value());
            }
            return pack.toolguides().stream()
                    .filter(t -> t.id().equals(toolguide))
                    .findFirst();
        }

        @Override
        public PackRevision currentRevision(PackIdentifier id) {
            return PackRevision.compute(List.of(id.value() + ":" + commits));
        }

        @Override
        public List<Relationship> registeredRelationships(PackIdentifier id) {
            return List.of();
        }

        @Override
        public <T> T exclusively(PackIdentifier id, Supplier<T> work) {
            return work.get();
        }

        @Override
        public PackRevision commit(PackIdentifier id, String ifMatch, Change change) {
            this.pack = change.revalidated();
            commits++;
            return currentRevision(id);
        }
    }
}
