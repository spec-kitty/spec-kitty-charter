package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ArtifactReadService;
import ai.speckitty.charter.packs.application.CompositeComponentResolver;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.DirectiveReadService;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackRevisions;
import ai.speckitty.charter.packs.application.ParadigmReadService;
import ai.speckitty.charter.packs.application.ProcedureReadService;
import ai.speckitty.charter.packs.application.StyleguideReadService;
import ai.speckitty.charter.packs.application.TacticReadService;
import ai.speckitty.charter.packs.application.ToolguideReadService;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.GuideBlock;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmRepository;
import ai.speckitty.charter.packs.domain.Procedure;
import ai.speckitty.charter.packs.domain.ProcedureIdentifier;
import ai.speckitty.charter.packs.domain.ProcedureRepository;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguideRepository;
import ai.speckitty.charter.packs.domain.StyleguideScope;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticRepository;
import ai.speckitty.charter.packs.domain.TacticStep;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.domain.ToolguideRepository;
import ai.speckitty.charter.packs.infra.storage.MarkdownGuideFormat;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.Specialization;
import ai.speckitty.charter.web.ProblemHandler;
import ai.speckitty.charter.web.RefusalHandler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

/**
 * The HTTP shape of the generic artifact reads, the only reads of a pack's artifacts: the envelope of each kind, the
 * links, the ETag of a writable kind, the refusals, the cursor round trip and the 400s the adapter answers. The read rules are {@code ArtifactReadServiceTest}'s.
 */
class ArtifactControllerTest {

    private static final PackIdentifier PACK = new PackIdentifier("qa");
    private static final String DIGEST = "sha256:" + "7".repeat(64);
    private static final String FINGERPRINT = "Charter-Source-Fingerprint";
    private static final PackRevision REVISION = PackRevision.compute(List.of("committed"));
    private static final String BASE = "/api/v1/packs/qa/artifacts";

    private final Pack pack = Pack.builder()
            .id(PACK)
            .descriptor(new PackDescriptor(
                    PACK.value(), "1.0.0", Optional.empty(), PACK.value(), Optional.empty(), Optional.empty()))
            .tactic(Tactic.builder()
                    .id(new TacticIdentifier("rehearse"))
                    .packId(PACK)
                    .schemaVersion("1.0")
                    .name("Rehearse")
                    .step(new TacticStep("Only step", Optional.empty(), List.of()))
                    .build())
            .directive(Directive.builder()
                    .id(new DirectiveIdentifier("GUARD"))
                    .packId(PACK)
                    .schemaVersion("1.0")
                    .title("Guard the gate")
                    .intent("Guard it.")
                    .enforcement(Enforcement.REQUIRED)
                    .build())
            .styleguide(Styleguide.builder()
                    .id(new StyleguideIdentifier("java-style"))
                    .packId(PACK)
                    .schemaVersion("1.0")
                    .title("Java style")
                    .scope(StyleguideScope.CODE)
                    .principle("Name things plainly.", "Name things plainly, in one sentence.")
                    .build())
            .paradigm(Paradigm.builder()
                    .id(new ParadigmIdentifier("layered"))
                    .packId(PACK)
                    .schemaVersion("1.0")
                    .name("Layered architecture")
                    .summary("Keep the layers apart.")
                    .build())
            .toolguide(Toolguide.builder()
                    .id(new ToolguideIdentifier("git"))
                    .packId(PACK)
                    .schemaVersion("1.0")
                    .tool("git")
                    .title("How to use git")
                    .guide(new Guide(Map.of(GuideBlock.OVERVIEW, "Use git under governance."), List.of()))
                    .summary("Using git under governance.")
                    .build())
            .artifact(AgentProfile.builder()
                    .id(new AgentProfileIdentifier("jenny"))
                    .packId(PACK)
                    .name("Java Jenny")
                    .role("implementer")
                    .purpose("Implement.")
                    .specialization(new Specialization("Java", Optional.empty(), Optional.empty(), Optional.empty()))
                    .build())
            .build();

    private final ArtifactController controller;
    private final MockMvc mvc;

    ArtifactControllerTest() {
        controller = controller(new ConsistentRead(() -> DIGEST), ignored -> REVISION);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RefusalHandler(), new ProblemHandler())
                .build();
    }

    private ArtifactController controller(ConsistentRead read, PackRevisions revisions) {
        InMemoryStore store = new InMemoryStore();
        ArtifactReadService reads = new ArtifactReadService(
                store,
                store,
                read,
                new TacticReadService(store, store, read, revisions),
                new DirectiveReadService(store, store, read, revisions),
                new StyleguideReadService(store, store, read, revisions),
                new ParadigmReadService(store, store, read, revisions),
                new ToolguideReadService(store, store, read, revisions),
                new ProcedureReadService(store, read, revisions),
                new OfferingReadService(store, store, store, read),
                new CompositeComponentResolver(),
                pack -> List.of(),
                type -> Set.of(),
                revisions);
        return new ArtifactController(reads, new Hypermedia("/api/v1"), new MarkdownGuideFormat());
    }

    // ---- the item ---------------------------------------------------------------------------------------------

    @Test
    void aTacticIsAnsweredInTheEnvelopeWithItsETag() throws Exception {
        mvc.perform(get(BASE + "/tactic/rehearse"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(header().string("ETag", "\"" + REVISION.token() + "\""))
                .andExpect(jsonPath("$.kind").value("tactic"))
                .andExpect(jsonPath("$.id").value("rehearse"))
                .andExpect(jsonPath("$.packId").value("qa"))
                .andExpect(jsonPath("$.tactic.name").value("Rehearse"))
                .andExpect(jsonPath("$.tactic.purpose").value((Object) null))
                .andExpect(jsonPath("$.tactic.notes").value((Object) null))
                .andExpect(jsonPath("$.tactic.steps.length()").value(1))
                .andExpect(jsonPath("$.tactic.steps[0].title").value("Only step"))
                .andExpect(jsonPath("$.tactic.failureModes.length()").value(0))
                .andExpect(jsonPath("$.directive").doesNotExist())
                .andExpect(jsonPath("$._links.self.href").value(BASE + "/tactic/rehearse"))
                .andExpect(jsonPath("$._links.self.templated").doesNotExist())
                .andExpect(jsonPath("$._links.self.method").doesNotExist())
                .andExpect(jsonPath("$._links.collection.href").value(BASE + "?kind=tactic"))
                .andExpect(jsonPath("$._links.edit").doesNotExist());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "directive, GUARD, $.directive.title, Guard the gate",
        "styleguide, java-style, $.styleguide.title, Java style",
        "paradigm, layered, $.paradigm.name, Layered architecture",
        "toolguide, git, $.toolguide.title, How to use git",
    })
    void aWritableKindIsAnsweredInTheEnvelopeWithItsETag(String kind, String id, String member, String value)
            throws Exception {
        mvc.perform(get(BASE + "/" + kind + "/" + id))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.kind").value(kind))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath(member).value(value))
                .andExpect(jsonPath("$.tactic").doesNotExist())
                .andExpect(jsonPath("$._links.self.href").value(BASE + "/" + kind + "/" + id))
                .andExpect(jsonPath("$._links.collection.href").value(BASE + "?kind=" + kind));
    }

    /** FR-004: a tactic item whose inputs change during the read is refused {@code stale_input} with no ETag. */
    @Test
    void aTacticWhoseInputsChangeDuringTheReadIsStaleWithoutAnETag() throws Exception {
        AtomicInteger digests = new AtomicInteger();
        ConsistentRead racing =
                new ConsistentRead(() -> digests.incrementAndGet() == 1 ? DIGEST : "sha256:" + "1".repeat(64));
        MockMvcBuilders.standaloneSetup(controller(racing, ignored -> REVISION))
                .setControllerAdvice(new RefusalHandler(), new ProblemHandler())
                .build()
                .perform(get(BASE + "/tactic/rehearse"))
                .andExpect(status().is5xxServerError())
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(jsonPath("$.code").value("stale_input"));
    }

    @Test
    void theItemEtagIsTheRevisionTheBodyWasReadAt() throws Exception {
        AtomicInteger commits = new AtomicInteger();
        String readAt = PackRevision.compute(List.of("commit:0")).token();
        AtomicInteger digests = new AtomicInteger();
        // A commit lands just after the read's closing digest, which still saw the files the body came from. The item
        // is the kind's verified read nested in the item's own (the pack.yaml check), so the closing digest is the 4th.
        ConsistentRead racing = new ConsistentRead(() -> {
            if (digests.incrementAndGet() == 4) {
                commits.incrementAndGet();
            }
            return DIGEST;
        });
        MockMvcBuilders.standaloneSetup(
                        controller(racing, ignored -> PackRevision.compute(List.of("commit:" + commits.get()))))
                .build()
                .perform(get(BASE + "/directive/GUARD"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + readAt + "\""));
        assertThat(commits.get()).as("the commit landed during the read").isEqualTo(1);
    }

    @Test
    void aToolguideMemberCarriesItsGuideWithTheH1FromItsTitle() throws Exception {
        mvc.perform(get(BASE + "/toolguide/git"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolguide.guide")
                        .value("# How to use git\n\n## Overview\n\nUse git under governance.\n"));
    }

    @Test
    void anUnservedKindIsKindNotFound() throws Exception {
        mvc.perform(get(BASE + "/template/x"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(FINGERPRINT))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("kind_not_found"))
                .andExpect(jsonPath("$.title").value("Kind not found"));
    }

    @Test
    void anAgentProfileOnTheTemplatedItemReadIsKindNotFoundNotAServerError() throws Exception {
        mvc.perform(get(BASE + "/agent_profile/jenny"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("kind_not_found"));
    }

    @Test
    void anUnknownPackIsPackNotFoundBeforeTheKind() throws Exception {
        mvc.perform(get("/api/v1/packs/nope/artifacts/bogus/x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("pack_not_found"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "tactic, tactic_not_found",
        "directive, directive_not_found",
        "styleguide, styleguide_not_found",
        "paradigm, paradigm_not_found",
        "toolguide, toolguide_not_found",
    })
    void anUnknownIdOfAServedKindIsThatKindsNotFound(String kind, String code) throws Exception {
        mvc.perform(get(BASE + "/" + kind + "/missing"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(FINGERPRINT))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }

    // ---- the list ---------------------------------------------------------------------------------------------

    @Test
    void aListAnswersSummariesThePageAndItsLinks() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(jsonPath("$.items.length()").value(6))
                .andExpect(jsonPath("$.items[0].kind").value("tactic"))
                .andExpect(jsonPath("$.items[0].title").value("Rehearse"))
                .andExpect(jsonPath("$.items[0]._links.self.href").value(BASE + "/tactic/rehearse"))
                .andExpect(jsonPath("$.items[4].kind").value("toolguide"))
                .andExpect(jsonPath("$.items[4].title").value("How to use git"))
                .andExpect(jsonPath("$.items[5].kind").value("agent_profile"))
                .andExpect(jsonPath("$.items[5].title").value("Java Jenny"))
                .andExpect(jsonPath("$.items[5]._links.self.href").value(BASE + "/agent_profile/jenny"))
                .andExpect(jsonPath("$.page.hasNextPage").value(false))
                .andExpect(jsonPath("$.page.nextPageCursor").value((Object) null))
                .andExpect(jsonPath("$.page.pageSize").value(50))
                .andExpect(jsonPath("$._links.self.href").value(BASE))
                .andExpect(jsonPath("$._links.first.href").value(BASE))
                .andExpect(jsonPath("$._links.next").doesNotExist());
    }

    @Test
    void theSelfLinkKeepsTheQueryTheRequestGaveAndFirstDropsTheCursor() throws Exception {
        String cursor = ArtifactCursor.encode(new ai.speckitty.charter.packs.application.ArtifactPosition(
                ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE, "GUARD"));

        mvc.perform(get(BASE + "?pageCursor=" + cursor + "&pageSize=2&kind=directive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page.pageSize").value(2))
                .andExpect(
                        jsonPath("$._links.self.href").value(BASE + "?kind=directive&pageSize=2&pageCursor=" + cursor))
                .andExpect(jsonPath("$._links.first.href").value(BASE + "?kind=directive&pageSize=2"));
    }

    @Test
    void theNextLinkLeadsToTheRestOfTheList() throws Exception {
        MvcResult first = mvc.perform(get(BASE + "?pageSize=4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.page.hasNextPage").value(true))
                .andExpect(jsonPath("$.page.nextPageCursor").value("cGFyYWRpZ20vbGF5ZXJlZA"))
                .andExpect(jsonPath("$._links.next.href").value(BASE + "?pageSize=4&pageCursor=cGFyYWRpZ20vbGF5ZXJlZA"))
                .andReturn();
        String next = new ObjectMapper()
                .readTree(first.getResponse().getContentAsString())
                .path("_links")
                .path("next")
                .path("href")
                .asString();

        mvc.perform(get(next))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value("git"))
                .andExpect(jsonPath("$.items[1].id").value("jenny"))
                .andExpect(jsonPath("$.page.hasNextPage").value(false))
                .andExpect(jsonPath("$._links.next").doesNotExist());
    }

    @Test
    void theKindFilterListsThatKind() throws Exception {
        mvc.perform(get(BASE + "?kind=paradigm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value("layered"))
                .andExpect(jsonPath("$._links.self.href").value(BASE + "?kind=paradigm"));
    }

    @Test
    void theKindFilterListsAgentProfilesEachLinkingToItsRead() throws Exception {
        mvc.perform(get(BASE + "?kind=agent_profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value("jenny"))
                .andExpect(jsonPath("$.items[0]._links.self.href").value(BASE + "/agent_profile/jenny"))
                .andExpect(jsonPath("$._links.self.href").value(BASE + "?kind=agent_profile"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "kind=bogus",
                "kind=template",
                "kind=Tactic",
                "pageCursor=***",
                "pageCursor=dGFjdGlj",
                "kind=tactic&pageCursor=ZGlyZWN0aXZlL0dVQVJE",
                "pageSize=abc",
                "pageCursor="
            })
    void aBadQueryIsA400ProblemWithoutACode(String query) throws Exception {
        mvc.perform(get(BASE + "?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist(FINGERPRINT))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void anUnknownPackIsPackNotFoundOnTheList() throws Exception {
        mvc.perform(get("/api/v1/packs/nope/artifacts"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(FINGERPRINT))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("pack_not_found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    /** Every port the reads ask, over the one pack; another pack is not found, as storage refuses it. */
    private final class InMemoryStore
            implements PackRepository,
                    TacticRepository,
                    DirectiveRepository,
                    StyleguideRepository,
                    ParadigmRepository,
                    ToolguideRepository,
                    ProcedureRepository,
                    PackCatalogRepository,
                    PackDescriptorRepository,
                    PackArtifactRepository {

        @Override
        public Pack load(PackIdentifier asked) {
            if (!asked.equals(PACK)) {
                throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + asked.value() + " was not found.");
            }
            return pack;
        }

        @Override
        public List<ConfiguredPack> configured() {
            return List.of(new ConfiguredPack(PACK, PackLevel.ORG));
        }

        @Override
        public PackDescriptor get(PackIdentifier asked) {
            return load(asked).descriptor();
        }

        @Override
        public Optional<PackDescriptor> find(PackIdentifier asked) {
            return Optional.of(get(asked));
        }

        @Override
        public void save(PackIdentifier asked, PackDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        /** No route this controller serves resolves a reference, so the fixture's packs refine and hold none. */
        @Override
        public PackCompositionFacts compositionFacts(PackIdentifier asked) {
            return PackCompositionFacts.NONE;
        }

        @Override
        public Optional<Tactic> load(PackIdentifier asked, TacticIdentifier tactic) {
            return load(asked).tactics().stream()
                    .filter(t -> t.id().equals(tactic))
                    .findFirst();
        }

        @Override
        public Optional<Directive> load(PackIdentifier asked, DirectiveIdentifier directive) {
            return load(asked).directives().stream()
                    .filter(d -> d.id().equals(directive))
                    .findFirst();
        }

        @Override
        public Optional<Styleguide> load(PackIdentifier asked, StyleguideIdentifier styleguide) {
            return load(asked).styleguides().stream()
                    .filter(s -> s.id().equals(styleguide))
                    .findFirst();
        }

        @Override
        public Optional<Paradigm> load(PackIdentifier asked, ParadigmIdentifier paradigm) {
            return load(asked).paradigms().stream()
                    .filter(p -> p.id().equals(paradigm))
                    .findFirst();
        }

        @Override
        public Optional<Toolguide> load(PackIdentifier asked, ToolguideIdentifier toolguide) {
            return load(asked).toolguides().stream()
                    .filter(t -> t.id().equals(toolguide))
                    .findFirst();
        }

        @Override
        public Optional<Procedure> load(PackIdentifier asked, ProcedureIdentifier procedure) {
            throw new UnsupportedOperationException("the controller tests read no procedure");
        }

        @Override
        public void save(Tactic tactic) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Directive directive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Styleguide styleguide) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Paradigm paradigm) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Toolguide toolguide) {
            throw new UnsupportedOperationException();
        }
    }
}
