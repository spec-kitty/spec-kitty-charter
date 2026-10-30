package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackDocument;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackInspector;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRoot;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The pack document through the whole-pack store (#260, ADR-0042 decision 8): {@code pack.md} is read into the
 * {@code Pack} through #194's {@code markdown} content form, written back in its canonical form, and equal in meaning
 * by the Markdown model. Each case is judged by #194's rule: a carriage return or a byte order mark is refused at its
 * line, never normalized; a missing final newline is the same model. Each refusal is paired with a clean control on
 * the same document.
 */
@DisplayName("Pack document round trip (#260, ADR-0042 decision 8)")
class PackDocumentRoundTripTest {

    private static final String PACK_YAML =
            "pack_id: 01M65MBWPACKDOCUMENT0000000\npack_version: 0.1.0\nname: documented\npack_level: project\n";

    private static final String WITH_FRONTMATTER = """
            ---
            audience: pack authors
            weight: 1.5
            count: 1
            tags:
              - baseline
            ---

            # A Documented Pack

            What the pack is for.

            ## Contents

            Directives and tactics.

            ## Lineage

            It has no `parent_pack`.
            """;

    private static final String WITHOUT_FRONTMATTER = """
            # Built-in Charter Pack

            The **built-in** pack ships with Spec Kitty itself — the baseline.

            It has no `parent_pack`.
            """;

    private final WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

    @Test
    @DisplayName("a pack.md with frontmatter and sections is read, written as pack.md and equal by its model")
    void frontmatterAndSectionsRoundTrip(@TempDir Path host) throws IOException {
        Path pack = documentedPack(host.resolve("source"), WITH_FRONTMATTER);
        WholePackStore.LoadedPack loaded = store.load(pack);

        assertThat(loaded.pack().violations()).isEmpty();
        assertThat(loaded.unmodelled()).doesNotContain(PackDocumentMarkdown.FILE);
        PackDocument document = loaded.pack().document().orElseThrow();
        assertThat(document.frontmatter())
                .contains(Map.of("audience", "pack authors", "weight", 1.5, "count", 1, "tags", List.of("baseline")));
        assertThat(document.title()).contains("A Documented Pack");
        assertThat(document.preamble()).isEqualTo("What the pack is for.");
        assertThat(document.sections())
                .containsExactly(
                        new PackDocument.Section("Contents", "Directives and tactics."),
                        new PackDocument.Section("Lineage", "It has no `parent_pack`."));

        Path target = host.resolve("written").resolve("documented");
        store.write(loaded, target);

        assertThat(store.load(target).pack().document()).isEqualTo(loaded.pack().document());
        assertThat(meaningDifferences(pack, target)).isEmpty();
    }

    @Test
    @DisplayName("a pack.md without frontmatter is written without frontmatter, byte for byte when canonical")
    void noFrontmatterStaysAbsent(@TempDir Path host) throws IOException {
        Path pack = documentedPack(host.resolve("source"), WITHOUT_FRONTMATTER);
        WholePackStore.LoadedPack loaded = store.load(pack);
        Path target = host.resolve("written").resolve("documented");

        store.write(loaded, target);

        assertThat(loaded.pack().document().orElseThrow().frontmatter()).isEmpty();
        assertThat(Files.readString(target.resolve(PackDocumentMarkdown.FILE))).isEqualTo(WITHOUT_FRONTMATTER);
    }

    @Test
    @DisplayName("an empty frontmatter stays empty, not absent: the two are different models")
    void emptyFrontmatterStaysEmpty(@TempDir Path host) throws IOException {
        Path empty = documentedPack(host.resolve("empty"), "---\n---\n" + WITHOUT_FRONTMATTER);
        Path absent = documentedPack(host.resolve("absent"), WITHOUT_FRONTMATTER);
        WholePackStore.LoadedPack loaded = store.load(empty);
        Path target = host.resolve("written").resolve("documented");

        store.write(loaded, target);

        assertThat(loaded.pack().document().orElseThrow().frontmatter()).contains(Map.of());
        assertThat(store.load(target).pack().document()).isEqualTo(loaded.pack().document());
        assertThat(store.load(absent).pack().document())
                .isNotEqualTo(loaded.pack().document());
    }

    @Test
    @DisplayName("a pack.md with no final newline loads as the same model and is written with one")
    void noFinalNewlineIsTheSameModel(@TempDir Path host) throws IOException {
        String unterminated = WITHOUT_FRONTMATTER.stripTrailing();
        Path pack = documentedPack(host.resolve("source"), unterminated);
        Path control = documentedPack(host.resolve("control"), WITHOUT_FRONTMATTER);
        WholePackStore.LoadedPack loaded = store.load(pack);
        Path target = host.resolve("written").resolve("documented");

        store.write(loaded, target);

        assertThat(loaded.pack().violations()).isEmpty();
        assertThat(loaded.pack().document())
                .isEqualTo(store.load(control).pack().document());
        assertThat(Files.readString(target.resolve(PackDocumentMarkdown.FILE))).isEqualTo(unterminated + "\n");
        assertThat(meaningDifferences(pack, target)).isEmpty();
    }

    @Test
    @DisplayName("a pack.md with CRLF line endings is refused at its first line, never normalized; LF loads")
    void crlfIsRefusedNotNormalised(@TempDir Path host) throws IOException {
        Path clean = documentedPack(host.resolve("clean"), WITHOUT_FRONTMATTER);
        Path refused = documentedPack(host.resolve("refused"), WITHOUT_FRONTMATTER.replace("\n", "\r\n"));

        assertThat(store.load(clean).pack().violations()).isEmpty();
        WholePackStore.LoadedPack loaded = store.load(refused);
        assertThat(loaded.pack().document()).isEmpty();
        assertThat(loaded.pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(PackDocumentMarkdown.FILE);
            assertThat(violation.reason()).isEqualTo("line 1: a carriage return is not admitted");
        });
        assertThatThrownBy(() -> store.write(loaded, host.resolve("written").resolve("documented")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED));
        assertThat(host.resolve("written").resolve("documented")).doesNotExist();
    }

    @Test
    @DisplayName("a pack.md that opens with a byte order mark is refused; the same text without it loads")
    void aByteOrderMarkIsRefused(@TempDir Path host) throws IOException {
        Path clean = documentedPack(host.resolve("clean"), WITHOUT_FRONTMATTER);
        Path refused = documentedPack(host.resolve("refused"), "﻿" + WITHOUT_FRONTMATTER);

        assertThat(store.load(clean).pack().violations()).isEmpty();
        assertThat(store.load(refused).pack().violations())
                .extracting(PackElementViolation::file, PackElementViolation::reason)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        PackDocumentMarkdown.FILE, "line 1: a byte order mark is not admitted"));
    }

    @Test
    @DisplayName("an empty pack.md is the empty document and is written back")
    void anEmptyFileIsTheEmptyDocument(@TempDir Path host) throws IOException {
        Path pack = documentedPack(host.resolve("source"), "");
        WholePackStore.LoadedPack loaded = store.load(pack);
        Path target = host.resolve("written").resolve("documented");

        store.write(loaded, target);

        assertThat(loaded.pack().document())
                .contains(new PackDocument(Optional.empty(), Optional.empty(), "", List.of()));
        assertThat(store.load(target).pack().document()).isEqualTo(loaded.pack().document());
    }

    @Test
    @DisplayName("a pack without pack.md holds no document and is written without one")
    void aPackWithoutPackMdHasNone(@TempDir Path host) throws IOException {
        Path pack = Files.createDirectories(host.resolve("bare"));
        Files.writeString(pack.resolve(PackFileSet.PACK_YAML), PACK_YAML);
        Path target = host.resolve("written").resolve("bare");

        store.write(store.load(pack), target);

        assertThat(store.load(pack).pack().document()).isEmpty();
        assertThat(target.resolve(PackDocumentMarkdown.FILE)).doesNotExist();
    }

    @Test
    @DisplayName("a pack.md below the pack root is not the pack document: it stays unmodelled")
    void aNestedPackMdIsNotTheDocument(@TempDir Path host) throws IOException {
        Path pack = Files.createDirectories(host.resolve("nested"));
        Files.writeString(pack.resolve(PackFileSet.PACK_YAML), PACK_YAML);
        Files.createDirectories(pack.resolve("docs"));
        Files.writeString(pack.resolve("docs/pack.md"), WITHOUT_FRONTMATTER);

        WholePackStore.LoadedPack loaded = store.load(pack);

        assertThat(loaded.pack().document()).isEmpty();
        assertThat(loaded.unmodelled()).contains("docs/pack.md");
    }

    @Test
    @DisplayName("a writer that changes the preamble goes red at preamble; the faithful write is green")
    void aLossyBodyIsRedAtThePreamble(@TempDir Path host) throws IOException {
        Path pack = documentedPack(host.resolve("source"), WITH_FRONTMATTER);
        WholePackStore.LoadedPack loaded = store.load(pack);
        PackDocument held = loaded.pack().document().orElseThrow();
        PackDocument lossy =
                new PackDocument(held.frontmatter(), held.title(), held.preamble() + " Changed.", held.sections());
        WholePackStore.LoadedPack changed = withDocument(loaded, lossy);
        Path faithful = host.resolve("faithful").resolve("documented");
        Path red = host.resolve("lossy").resolve("documented");

        store.write(loaded, faithful);
        store.write(changed, red);

        assertThat(meaningDifferences(pack, faithful)).isEmpty();
        assertThat(meaningDifferences(pack, red))
                .extracting(MeaningComparator.Difference::path)
                .containsExactly("preamble");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"crlf", "bom"})
    @DisplayName("validate mode refuses the pack.md the load refuses, and is silent on the clean one")
    void validateModeAgreesWithTheLoad(String breakage, @TempDir Path host) throws IOException {
        String text = "crlf".equals(breakage) ? WITHOUT_FRONTMATTER.replace("\n", "\r\n") : "﻿" + WITHOUT_FRONTMATTER;
        Path clean = documentedPack(host.resolve("clean"), WITHOUT_FRONTMATTER);
        Path refused = documentedPack(host.resolve("refused"), text);
        String reason = store.load(refused).pack().violations().getFirst().reason();

        assertThat(validateFindings(clean)).isEmpty();
        assertThat(validateFindings(refused)).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
            assertThat(finding.detail()).endsWith(reason);
        });
    }

    @Test
    @DisplayName("a document that cannot stand as Markdown refuses the write, naming pack.md, and leaves no target")
    void anUnwritableDocumentRefusesTheWrite(@TempDir Path host) throws IOException {
        Path pack = documentedPack(host.resolve("source"), WITHOUT_FRONTMATTER);
        WholePackStore.LoadedPack loaded = store.load(pack);
        PackDocument held = loaded.pack().document().orElseThrow();
        PackDocument broken = new PackDocument(held.frontmatter(), held.title(), "# A second title", held.sections());
        Path clean = host.resolve("clean").resolve("documented");
        Path refused = host.resolve("refused").resolve("documented");

        store.write(loaded, clean);

        assertThat(clean.resolve(PackDocumentMarkdown.FILE)).exists();
        assertThatThrownBy(() -> store.write(withDocument(loaded, broken), refused))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.getMessage()).contains("pack.md cannot be written as Markdown: preamble");
                });
        assertThat(refused).doesNotExist();
    }

    @Test
    @DisplayName("a title the codec cannot read back (a closing # sequence) refuses the write as VALIDATION_FAILED")
    void aTitleTheCodecCannotWriteRefusesTheWrite(@TempDir Path host) throws IOException {
        Path pack = documentedPack(host.resolve("source"), WITHOUT_FRONTMATTER);
        WholePackStore.LoadedPack loaded = store.load(pack);
        PackDocument held = loaded.pack().document().orElseThrow();
        PackDocument closed =
                new PackDocument(held.frontmatter(), Optional.of("Release #"), held.preamble(), held.sections());
        PackDocument open =
                new PackDocument(held.frontmatter(), Optional.of("Release 1"), held.preamble(), held.sections());
        Path refused = host.resolve("refused").resolve("documented");

        store.write(withDocument(loaded, open), host.resolve("clean").resolve("documented"));

        assertThatThrownBy(() -> store.write(withDocument(loaded, closed), refused))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.getMessage()).contains("pack.md cannot be written as Markdown");
                });
        assertThat(refused).doesNotExist();
    }

    // ---- through the gate: DomainRoundTrip, compared by the Markdown model -------------------------------------

    @Test
    @DisplayName("through the gate, a pack.md with frontmatter is green, numbers and lists included")
    void theGateIsGreenOnAFaithfulRun(@TempDir Path host) throws IOException {
        Path pack = documentedPack(host.resolve("documented"), WITH_FRONTMATTER);

        Gate gate = gate(pack, host.resolve("written"), UnaryOperator.identity());

        assertThat(gate.observed()).containsEntry(PackDocumentMarkdown.FILE, Optional.empty());
        assertThat(gate.unpaired()).isEmpty();
    }

    @Test
    @DisplayName("through the gate, dropping an empty frontmatter is red at frontmatter; keeping it is green")
    void droppingAnEmptyFrontmatterIsRed(@TempDir Path host) throws IOException {
        Path pack = documentedPack(host.resolve("documented"), "---\n---\n" + WITHOUT_FRONTMATTER);

        Gate kept = gate(pack, host.resolve("kept"), UnaryOperator.identity());
        Gate dropped = gate(pack, host.resolve("dropped"), read -> {
            PackDocument held = read.document().orElseThrow();
            return read.toBuilder()
                    .document(new PackDocument(Optional.empty(), held.title(), held.preamble(), held.sections()))
                    .build();
        });

        assertThat(kept.observed()).containsEntry(PackDocumentMarkdown.FILE, Optional.empty());
        assertThat(dropped.observed())
                .containsEntry(PackDocumentMarkdown.FILE, Optional.of("frontmatter: source {}, written <absent>"));
    }

    @Test
    @DisplayName("through the gate, a document the write drops is refused at read-back; the faithful run is green")
    void aDroppedDocumentIsRefusedAtReadBack(@TempDir Path host) throws IOException {
        Path pack = documentedPack(host.resolve("documented"), WITHOUT_FRONTMATTER);

        Gate kept = gate(pack, host.resolve("kept"), UnaryOperator.identity());
        Gate dropped = gate(
                pack,
                host.resolve("dropped"),
                read -> read.toBuilder().withoutDocument().build());

        assertThat(kept.observed()).containsEntry(PackDocumentMarkdown.FILE, Optional.empty());
        assertThat(dropped.observed())
                .containsEntry(
                        PackDocumentMarkdown.FILE,
                        Optional.of("refused: read-back: the written pack holds no document at pack.md"));
    }

    @Test
    @DisplayName("through the gate, a CRLF pack.md is refused with the store's reason; the LF one is green")
    void aCrlfPackMdIsRefusedThroughTheGate(@TempDir Path host) throws IOException {
        Path clean = documentedPack(host.resolve("clean").resolve("documented"), WITHOUT_FRONTMATTER);
        Path crlf =
                documentedPack(host.resolve("crlf").resolve("documented"), WITHOUT_FRONTMATTER.replace("\n", "\r\n"));

        assertThat(gate(clean, host.resolve("clean-written"), UnaryOperator.identity())
                        .observed())
                .containsEntry(PackDocumentMarkdown.FILE, Optional.empty());
        assertThat(gate(crlf, host.resolve("crlf-written"), UnaryOperator.identity())
                        .observed())
                .containsEntry(
                        PackDocumentMarkdown.FILE, Optional.of("refused: line 1: a carriage return is not admitted"));
    }

    @Test
    @DisplayName("the test fixtures' copy of the pack-document structure schema is the bundled one")
    void theFixtureSchemaCopyIsTheBundledOne() throws IOException {
        assertThat(resource("markdown-rule-fixtures/pack-document.schema.json"))
                .isEqualTo(resource("packs/schemas/pack-document.schema.json"));
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private record Gate(Map<String, Optional<String>> observed, List<String> unpaired) {}

    /** The whole-pack gate over one pack: {@link DomainRoundTrip}, then every modelled file compared in its form. */
    private static Gate gate(Path pack, Path host, UnaryOperator<Pack> beforeSave) throws IOException {
        Path target = host.resolve(String.valueOf(pack.getFileName()));
        Set<String> paired = new TreeSet<>();
        Map<String, Optional<String>> observed = WholePackRoundTripTest.observe(
                new DomainRoundTrip(new YamlSerializationStrategy(), beforeSave),
                pack,
                target,
                WholePackCensus.of(pack),
                new TreeMap<>(),
                paired,
                WholePackCensus.formsOf(pack));
        return new Gate(observed, WholePackRoundTripTest.unpaired(target, paired));
    }

    private static WholePackStore.LoadedPack withDocument(WholePackStore.LoadedPack loaded, PackDocument document) {
        return new WholePackStore.LoadedPack(
                loaded.pack().toBuilder().document(document).build(),
                loaded.layout(),
                loaded.graphs(),
                loaded.unmodelled(),
                loaded.manifest());
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = PackDocumentRoundTripTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path documentedPack(Path directory, String document) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(PackFileSet.PACK_YAML), PACK_YAML);
        Files.write(directory.resolve(PackDocumentMarkdown.FILE), document.getBytes(StandardCharsets.UTF_8));
        return directory;
    }

    private static List<MeaningComparator.Difference> meaningDifferences(Path source, Path written) throws IOException {
        return MeaningComparator.adr0036()
                .compareMarkdown(
                        Files.readString(source.resolve(PackDocumentMarkdown.FILE)),
                        Files.readString(written.resolve(PackDocumentMarkdown.FILE)));
    }

    /** Validate mode's findings on the pack document of a pack directory. */
    private static List<PackFinding> validateFindings(Path pack) {
        PackRoot root = new PackRoot("documented", Optional.of(PackLevel.PROJECT), pack.toString());
        return PackPolicy.judge(new FileSystemPackInspector().inspect(root, PackInspector.Scope.FULL))
                .findings()
                .stream()
                .filter(finding -> finding.file().equals(PackDocumentMarkdown.FILE))
                .toList();
    }
}
