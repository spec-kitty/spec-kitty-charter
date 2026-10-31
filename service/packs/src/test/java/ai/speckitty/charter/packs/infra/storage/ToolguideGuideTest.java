package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.GuideBlock;
import ai.speckitty.charter.packs.domain.GuideSection;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Outcome;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Written;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownRefusal;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A toolguide's structured guide (#194, research D5, D10, D11): the {@code <id>.guide.md} companion beside the
 * toolguide is read into a {@link Guide}, written back canonically with its H1 from the title, compared by the gate,
 * and refused, with an accepted neighbour, wherever it breaks the guide layout, the structure schema or the companion
 * rule. The fixture is {@code roundtrip-fixtures/toolguide}.
 */
@DisplayName("A toolguide's guide is a structured Markdown companion (#194)")
class ToolguideGuideTest {

    private static final String PACK = "toolguide";
    private static final PackIdentifier PACK_ID = new PackIdentifier(PACK);
    private static final String ID = "git-agent-commit-signing";
    private static final String TITLE = "Git Agent Commit Signing";
    private static final String TOOLGUIDE_FILE = "toolguides/" + ID + ".toolguide.yaml";
    private static final String GUIDE_FILE = "toolguides/" + ID + ".guide.md";
    private static final String GUIDE = ID + ".guide.md";

    /** A small guide in the canonical layout; each refusal below changes it in one place. */
    private static final String ACCEPTED = """
            # Git Agent Commit Signing

            ## Overview

            O.

            ## Setup

            S.

            ## Usage

            U.

            ## Examples

            E.

            ## Cautions

            C.

            ## References

            - https://example.org
            """;

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @TempDir
    Path host;

    // ---- the fixture round trip
    // --------------------------------------------------------------------------------------

    @Test
    @DisplayName(
            "the fixture guide reads into its blocks and sections and writes back byte for byte, H1 from the title")
    void theFixtureGuideRoundTrips() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        String source = Files.readString(pack.resolve(GUIDE_FILE));
        FilesystemPackStore store = new FilesystemPackStore(host, strategy);

        FilesystemPackStore.PackRead read = store.read(PACK_ID, List.of(ToolguideBinding.INSTANCE));

        assertThat(read.violations()).isEmpty();
        ToolguideDso dso = read.dsos(ToolguideBinding.INSTANCE).getFirst();
        Toolguide toolguide = ToolguideBinding.INSTANCE.toEntity(PACK_ID, dso);
        Guide guide = toolguide.guide();
        assertThat(guide.blocks().keySet())
                .containsExactly(
                        GuideBlock.OVERVIEW,
                        GuideBlock.WHEN_TO_USE,
                        GuideBlock.USAGE,
                        GuideBlock.CAUTIONS,
                        GuideBlock.REFERENCES);
        assertThat(guide.block(GuideBlock.USAGE).orElseThrow())
                .as("a fenced comment stays in its block")
                .contains("# sign with the agent's key");
        assertThat(guide.sections()).extracting(GuideSection::title).containsExactly("Examples");
        assertThat(guide.sections().getFirst().content())
                .as("an H3 stays in content")
                .startsWith("### A signed merge");
        assertThat(toolguide.guidePath()).isEqualTo(GUIDE);
        assertThat(ToolguideBinding.INSTANCE.toDso(toolguide))
                .as("DSO fidelity")
                .isEqualTo(dso);

        Files.delete(pack.resolve(GUIDE_FILE));
        store.write(PACK_ID, ToolguideBinding.INSTANCE, ToolguideBinding.INSTANCE.toDso(toolguide));

        assertThat(pack.resolve(GUIDE_FILE)).hasContent(source);
    }

    @Test
    @DisplayName("a guide without an H1 is accepted and written with the toolguide's title as its H1")
    void anAbsentH1IsAcceptedAndWritten() {
        Guide guide = guide(ACCEPTED.substring(ACCEPTED.indexOf("## Overview")));

        MarkdownDocument written = GuideMarkdown.toDocument(TITLE, guide);

        assertThat(guide).isEqualTo(guide(ACCEPTED));
        assertThat(written.title()).contains(TITLE);
        assertThat(MarkdownCodec.write(written)).isEqualTo(ACCEPTED);
    }

    @Test
    @DisplayName("a link to a styleguide in the guide's prose mints no relationship")
    void aProseLinkMintsNoRelationship() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        DomainRepositoryService service =
                new DomainRepositoryService(() -> List.of(new PackLocation(PACK, PackLevel.ORG, pack)), strategy);

        Pack loaded = service.load(PACK_ID);

        assertThat(loaded.violations()).isEmpty();
        assertThat(loaded.toolguides())
                .singleElement()
                .satisfies(toolguide -> assertThat(toolguide.guide().overview())
                        .contains("../styleguides/commit-messages.styleguide.yaml"));
        assertThat(loaded.artifacts())
                .flatExtracting(CharterArtifact::relationships)
                .isEmpty();
    }

    // ---- the gate
    // ----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the fixture pack round-trips through the gate green, the guide paired with the written toolguide's")
    void theFixtureIsGreenThroughTheGate() throws IOException {
        Path source = PackFixtures.copyOf(PACK, host);

        Gate faithful = gate(source, "faithful", pack -> pack);

        assertThat(faithful.outcomes()).containsEntry(GUIDE_FILE, new Written(GUIDE));
        assertThat(faithful.observed())
                .containsKeys(TOOLGUIDE_FILE, GUIDE_FILE)
                .allSatisfy((path, red) -> assertThat(red).as(path).isEmpty());
        assertThat(faithful.unpaired()).isEmpty();
    }

    @Test
    @DisplayName("positive control: one changed character in a guide section is a gate difference at that section")
    void oneChangedCharacterInAGuideSectionIsReported() throws IOException {
        Path source = PackFixtures.copyOf(PACK, host);

        Gate changed = gate(source, "changed", pack -> {
            Toolguide toolguide = pack.toolguides().getFirst();
            GuideSection examples = toolguide.guide().sections().getFirst();
            Guide edited = new Guide(
                    toolguide.guide().blocks(),
                    List.of(new GuideSection(
                            examples.title(), examples.content().replace("feature", "featurE"))));
            ToolguideDso dso = ToolguideBinding.INSTANCE
                    .toDso(toolguide)
                    .withGuide(Optional.of(GuideMarkdown.toDocument(toolguide.title(), edited)));
            return pack.with(ToolguideBinding.INSTANCE.toEntity(pack.id(), dso));
        });

        assertThat(changed.observed().get(GUIDE_FILE))
                .hasValueSatisfying(red -> assertThat(red)
                        .startsWith("sections[3].content: ")
                        .contains("git merge --no-ff -S feature\n", "git merge --no-ff -S featurE\n"));
        assertThat(changed.observed().get(TOOLGUIDE_FILE))
                .as("the toolguide's own file")
                .isEmpty();
    }

    private record Gate(Map<String, Outcome> outcomes, Map<String, Optional<String>> observed, List<String> unpaired) {}

    /** The gate over the fixture: {@link DomainRoundTrip} with the registry's bindings and the bundled table. */
    private Gate gate(Path source, String run, UnaryOperator<Pack> beforeSave) {
        Path target = host.resolve(run).resolve(PACK);
        SortedMap<String, String> census = WholePackCensus.of(source);
        Map<String, Outcome> outcomes = new TreeMap<>();
        WholePackRoundTrip recording = new WholePackRoundTrip() {
            private final DomainRoundTrip domain = new DomainRoundTrip(strategy, beforeSave);

            @Override
            public Set<String> modelledKinds() {
                return domain.modelledKinds();
            }

            @Override
            public Map<String, Outcome> roundTrip(Path from, Path to, Map<String, String> kindOf) {
                outcomes.putAll(domain.roundTrip(from, to, kindOf));
                return outcomes;
            }
        };
        Set<String> paired = new TreeSet<>();
        Map<String, Optional<String>> observed = WholePackRoundTripTest.observe(
                recording, source, target, census, new TreeMap<>(), paired, WholePackCensus.formsOf(source));
        assertThat(census).containsEntry(GUIDE_FILE, "toolguide");
        assertThat(WholePackCensus.formsOf(source).apply(GUIDE_FILE)).isEqualTo(ContentForm.MARKDOWN);
        return new Gate(outcomes, observed, WholePackRoundTripTest.unpaired(target, paired));
    }

    // ---- the guide layout (T027), each refusal beside its accepted neighbour --------------------------------------

    @Test
    @DisplayName("the accepted guide maps to its blocks and its freeform section, in write order")
    void theAcceptedNeighbourMaps() {
        Guide guide = guide(ACCEPTED);

        assertThat(guide.blocks())
                .containsExactly(
                        Map.entry(GuideBlock.OVERVIEW, "O."),
                        Map.entry(GuideBlock.SETUP, "S."),
                        Map.entry(GuideBlock.USAGE, "U."),
                        Map.entry(GuideBlock.CAUTIONS, "C."),
                        Map.entry(GuideBlock.REFERENCES, "- https://example.org"));
        assertThat(guide.sections()).containsExactly(new GuideSection("Examples", "E."));
        assertThat(MarkdownCodec.write(GuideMarkdown.toDocument(TITLE, guide))).isEqualTo(ACCEPTED);
    }

    @Test
    @DisplayName("frontmatter is refused; the same guide without it is accepted")
    void frontmatterIsRefused() {
        assertRefused("---\nid: x\n---\n" + ACCEPTED, "has frontmatter");
    }

    @Test
    @DisplayName("a preamble is refused; the same text as the start of the overview is accepted")
    void aPreambleIsRefusedWhileTheOverviewTwinIsAccepted() {
        assertRefused(
                ACCEPTED.replace("## Overview\n\n", "Read this first.\n\n## Overview\n\n"),
                "has text before its first section; it belongs in ## Overview");
        assertThat(guide(ACCEPTED.replace("## Overview\n\n", "## Overview\n\nRead this first.\n\n"))
                        .overview())
                .isEqualTo("Read this first.\n\nO.");
    }

    @Test
    @DisplayName("an H1 that differs from the toolguide title is refused; the equal H1 is accepted")
    void aDifferentH1IsRefused() {
        assertRefused(
                ACCEPTED.replace("# Git Agent Commit Signing", "# Git Agent Commit Signing - Invocation Contract"),
                "has the H1 'Git Agent Commit Signing - Invocation Contract', which differs from the toolguide title"
                        + " 'Git Agent Commit Signing'");
    }

    @Test
    @DisplayName("a missing or blank overview is refused; the overview alone is accepted")
    void aMissingOrBlankOverviewIsRefused() {
        assertRefused("## Usage\n\nU.\n", "has no ## Overview");
        assertRefused("## Overview\n\n## Usage\n\nU.\n", "overview that is not blank");
        assertThat(guide("## Overview\n\nO.\n")).isEqualTo(Guide.of("O."));
    }

    @Test
    @DisplayName("fixed blocks out of order or twice are refused; the ordered twin is accepted")
    void fixedBlocksOutOfOrderOrTwiceAreRefused() {
        assertRefused(
                ACCEPTED.replace("## Setup\n\nS.\n\n## Usage\n\nU.", "## Usage\n\nU.\n\n## Setup\n\nS."),
                "has ## Setup after ## Usage");
        assertRefused(
                ACCEPTED.replace("## References", "## Known limitations\n\nK.\n\n## Cautions\n\nC2.\n\n## References"),
                "has ## Cautions twice");
        assertRefused(
                ACCEPTED.replace(
                        "## Cautions\n\nC.\n\n## References", "## References\n\nR.\n\n## Cautions\n\nC.\n\n## X"),
                "has ## Cautions after ## References");
    }

    @Test
    @DisplayName("a fixed heading in another letter case is refused; the canonical spelling is accepted")
    void aFixedHeadingInAnotherCaseIsRefused() {
        assertRefused(ACCEPTED.replace("## Usage", "## usage"), "has ## usage; the fixed heading is written ## Usage");
        assertRefused(
                ACCEPTED.replace("## Cautions", "## CAUTIONS"),
                "has ## CAUTIONS; the fixed heading is written ## Cautions");
    }

    @Test
    @DisplayName("a freeform section after a trailing block, or a leading block after one, is refused")
    void freeformSectionsSitBetweenTheLeadingAndTrailingBlocks() {
        assertRefused(
                ACCEPTED.replace("## Examples\n\nE.\n\n## Cautions\n\nC.", "## Cautions\n\nC.\n\n## Examples\n\nE."),
                "has the freeform section 'Examples' after ## Cautions");
        assertRefused(ACCEPTED + "\n## Appendix\n\nA.\n", "has the freeform section 'Appendix' after ## References");
        assertRefused(
                ACCEPTED.replace("## Usage\n\nU.\n\n## Examples\n\nE.", "## Examples\n\nE.\n\n## Usage\n\nU."),
                "has ## Usage after the freeform section 'Examples'");
    }

    // ---- the store and the companion rule
    // ----------------------------------------------------------------------------

    @Test
    @DisplayName("a missing guide file is a companion violation; the present guide reads clean")
    void aMissingGuideIsAViolation() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.delete(pack.resolve(GUIDE_FILE));

        FilesystemPackStore.PackRead read =
                new FilesystemPackStore(host, strategy).read(PACK_ID, List.of(ToolguideBinding.INSTANCE));

        assertThat(read.dsos(ToolguideBinding.INSTANCE)).isEmpty();
        assertThat(read.violations())
                .containsExactly(new PackElementViolation(TOOLGUIDE_FILE, "companion", GUIDE + ": missing"));
    }

    @Test
    @DisplayName("a guide breaking the structure schema is a companion violation naming the keyword")
    void aStructureBreachIsAViolation() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.writeString(pack.resolve(GUIDE_FILE), "# " + TITLE + "\n\nRead this first.\n\n## Overview\n\nO.\n");

        FilesystemPackStore.PackRead read =
                new FilesystemPackStore(host, strategy).read(PACK_ID, List.of(ToolguideBinding.INSTANCE));

        assertThat(read.violations())
                .containsExactly(new PackElementViolation(
                        TOOLGUIDE_FILE, "companion", GUIDE + ": structure: const violated at /preamble"));
    }

    @Test
    @DisplayName("a guide breaking the layout is a domain violation of the toolguide when the pack loads")
    void aLayoutBreachIsADomainViolation() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.writeString(pack.resolve(GUIDE_FILE), "# Another title\n\n## Overview\n\nO.\n");
        DomainRepositoryService service =
                new DomainRepositoryService(() -> List.of(new PackLocation(PACK, PackLevel.ORG, pack)), strategy);

        Pack loaded = service.load(PACK_ID);

        assertThat(loaded.toolguides()).isEmpty();
        assertThat(loaded.violations())
                .containsExactly(new PackElementViolation(
                        ID,
                        "domain",
                        "the guide has the H1 'Another title', which differs from the toolguide title '" + TITLE
                                + "'"));
    }

    @Test
    @DisplayName("a stored guide without its H1 is a domain violation; the H1 is derived from the title (ADR-0043)")
    void aStoredGuideWithoutItsH1IsADomainViolation() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.writeString(pack.resolve(GUIDE_FILE), "## Overview\n\nO.\n");
        DomainRepositoryService service =
                new DomainRepositoryService(() -> List.of(new PackLocation(PACK, PackLevel.ORG, pack)), strategy);

        Pack loaded = service.load(PACK_ID);

        assertThat(loaded.toolguides()).isEmpty();
        assertThat(loaded.violations())
                .containsExactly(new PackElementViolation(
                        ID,
                        "domain",
                        "the guide has no H1; a stored guide carries its toolguide's title '" + TITLE + "' as its H1"));

        Files.writeString(pack.resolve(GUIDE_FILE), "# " + TITLE + "\n\n## Overview\n\nO.\n");
        assertThat(service.load(PACK_ID).toolguides())
                .as("the same guide with its H1")
                .hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Search for #", "C #", "#"})
    @DisplayName(
            "a stored toolguide whose title cannot be the guide's H1 can never match the stored H1: it is refused at"
                    + " load, never when it is answered")
    void aStoredTitleThatCannotBeTheH1IsRefusedAtLoad(String title) throws IOException {
        // #271: no guide document can carry such an H1 (the Markdown model refuses it, as the codec always did), so
        // the stored guide keeps an H1 the title cannot equal.
        PackFixtures.copyOf(PACK, host);
        ToolguideDso read = new FilesystemPackStore(host, strategy)
                .read(PACK_ID, List.of(ToolguideBinding.INSTANCE))
                .dsos(ToolguideBinding.INSTANCE)
                .getFirst();
        MarkdownDocument guide = read.guide().orElseThrow();
        ToolguideDso retitled = new ToolguideDso(
                        read.id(),
                        read.schemaVersion(),
                        read.tool(),
                        title,
                        read.guidePath(),
                        read.summary(),
                        read.commands(),
                        read.appliesToLanguages(),
                        read.lastUpdated(),
                        read.references(),
                        Optional.empty())
                .withGuide(Optional.of(guide));

        assertThatThrownBy(() -> ToolguideBinding.INSTANCE.toEntity(PACK_ID, retitled))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("which differs from the toolguide title '" + title + "'");
        assertThatThrownBy(() -> GuideMarkdown.write(title, Guide.of("O.")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the title '" + title + "' cannot be the guide's H1: it cannot be a heading's text: an H2"
                        + " with a closing # sequence is ambiguous");
        assertThatCode(() -> ToolguideBinding.INSTANCE.toEntity(PACK_ID, read))
                .as("the stored title")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("writing a guide that breaks its structure is a client error and writes nothing")
    void aStructureBreachIsRefusedOnWrite() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        ToolguideDso read = new FilesystemPackStore(host, strategy)
                .readAll(PACK_ID, ToolguideBinding.INSTANCE)
                .getFirst();
        ToolguideDso withPreamble =
                read.withGuide(Optional.of(document("# " + TITLE + "\n\nP.\n\n## Overview\n\nO.\n")));
        String before = Files.readString(pack.resolve(GUIDE_FILE));

        assertThatThrownBy(() ->
                        new FilesystemPackStore(host, strategy).write(PACK_ID, ToolguideBinding.INSTANCE, withPreamble))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED))
                .hasMessage("toolguide '" + ID + "': companion " + GUIDE
                        + " breaks its structure: const violated at /preamble");
        assertThat(pack.resolve(GUIDE_FILE)).hasContent(before);
    }

    @Test
    @DisplayName("a guide_path that leaves the toolguide's directory or differs in case is unreadable")
    void aGuidePathThatLeavesOrDiffersInCaseIsUnreadable() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        String yaml = Files.readString(pack.resolve(TOOLGUIDE_FILE));
        FilesystemPackStore store = new FilesystemPackStore(host, strategy);

        for (String path : List.of("../" + GUIDE, "Git-agent-commit-signing.guide.md", "../x.guide.md")) {
            Files.writeString(
                    pack.resolve(TOOLGUIDE_FILE), yaml.replace("guide_path: " + GUIDE, "guide_path: " + path));
            FilesystemPackStore.PackRead read = store.read(PACK_ID, List.of(ToolguideBinding.INSTANCE));
            assertThat(read.dsos(ToolguideBinding.INSTANCE)).as(path).isEmpty();
            assertThat(read.violations())
                    .as(path)
                    .extracting(PackElementViolation::file)
                    .containsExactly(TOOLGUIDE_FILE);
        }
        Files.writeString(pack.resolve(TOOLGUIDE_FILE), yaml);
        assertThat(store.read(PACK_ID, List.of(ToolguideBinding.INSTANCE)).violations())
                .as("the sanitized name beside the toolguide")
                .isEmpty();
        Path packDir = pack.toAbsolutePath().normalize();
        assertThat(Companions.resolve(
                        Companions.Rules.bundled(), packDir, TOOLGUIDE_FILE, guideCompanion("../../x.guide.md")))
                .as("the companion rule refuses it too, should a path ever reach it")
                .isEqualTo(new Companions.Refused("leaves the pack"));
    }

    @Test
    @DisplayName("the guide rule is the guide companion's own; another companion there is refused naming the rule")
    void theGuideRuleAdmitsOnlyTheGuide() throws IOException {
        Path packDir =
                Files.createDirectories(host.resolve(PACK)).toAbsolutePath().normalize();
        KindBinding.Companion textAtTheGuide = new KindBinding.Companion("../toolguides/" + GUIDE, Optional.empty());

        assertThat(Companions.resolve(Companions.Rules.bundled(), packDir, TOOLGUIDE_FILE, guideCompanion(GUIDE)))
                .isEqualTo(new Companions.Inside(packDir.resolve(GUIDE_FILE), GUIDE_FILE));
        assertThat(Companions.resolve(Companions.Rules.bundled(), packDir, "skills/s.skill.yaml", textAtTheGuide))
                .isEqualTo(new Companions.Refused("is a pack file of kind toolguide-guide (file-kinds rule"
                        + " '**/toolguides/*.guide.md'), not a companion"));
    }

    @Test
    @DisplayName("a companion naming the guide in another letter case is refused as the guide's file; a distinct name"
            + " reads clean")
    void aCaseOnlyCollisionWithTheGuideIsRefused() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.createDirectories(pack.resolve("skills"));
        Files.writeString(pack.resolve("toolguides/" + ID.toUpperCase(java.util.Locale.ROOT) + ".GUIDE.MD"), "body\n");
        Files.writeString(pack.resolve("skills/s.md"), "body\n");
        FilesystemPackStore store = new FilesystemPackStore(host, strategy);
        List<KindBinding<?, ?>> kinds = List.of(ToolguideBinding.INSTANCE, SkillBinding.INSTANCE);

        Files.writeString(pack.resolve("skills/s.skill.yaml"), skill("s.md"));
        assertThat(store.read(PACK_ID, kinds).violations()).as("distinct names").isEmpty();

        Files.writeString(
                pack.resolve("skills/s.skill.yaml"),
                skill("../toolguides/" + ID.toUpperCase(java.util.Locale.ROOT) + ".GUIDE.MD"));
        // #272: the path is classified case-folded, so the guide's rule owns it, as it owns the exact name.
        assertThat(store.read(PACK_ID, kinds).violations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.reason())
                        .isEqualTo("../toolguides/" + ID.toUpperCase(java.util.Locale.ROOT) + ".GUIDE.MD: is a pack"
                                + " file of kind toolguide-guide (file-kinds rule '**/toolguides/*.guide.md'), not a"
                                + " companion"));
    }

    @Test
    @DisplayName("the test fixtures' copy of the guide structure schema is the bundled one")
    void theFixtureSchemaCopyIsTheBundledOne() {
        assertThat(resource("markdown-rule-fixtures/toolguide-guide.schema.json"))
                .isEqualTo(resource("packs/schemas/toolguide-guide.schema.json"));
    }

    // ---- helpers
    // -----------------------------------------------------------------------------------------------------

    private static void assertRefused(String text, String reason) {
        assertThatCode(() -> guide(ACCEPTED)).as("the accepted neighbour").doesNotThrowAnyException();
        assertThatThrownBy(() -> guide(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(reason);
    }

    private static Guide guide(String text) {
        return GuideMarkdown.fromDocument(TITLE, document(text));
    }

    private static MarkdownDocument document(String text) {
        return switch (MarkdownCodec.parse(text)) {
            case MarkdownCodec.Parsed parsed -> parsed.document();
            case MarkdownRefusal refusal -> throw new AssertionError("not Markdown: " + refusal.message());
        };
    }

    private static KindBinding.Companion guideCompanion(String path) {
        return new KindBinding.Companion(
                path, Optional.empty(), ContentForm.MARKDOWN, Optional.of(ToolguideBinding.GUIDE_SCHEMA));
    }

    private static String skill(String bodyPath) {
        return """
                schema_version: "1.0"
                id: s
                title: S
                description: Use when s.
                form: prompt
                body_path: %s
                """.formatted(bodyPath);
    }

    private static String resource(String name) {
        try (InputStream in = ToolguideGuideTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
