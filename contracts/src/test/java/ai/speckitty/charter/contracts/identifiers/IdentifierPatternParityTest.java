package ai.speckitty.charter.contracts.identifiers;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RoleBearer;
import ai.speckitty.charter.kernel.markdown.SlotId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The identifier patterns of the contracts give the kernel's verdict (#585, ADR-0075 decision 2). The sites are
 * discovered from the schema files ({@link IdentifierSite#discover}), mapped to a kernel predicate by the rule of
 * {@link KernelGrammar}, and an identifier-like site the rule cannot map fails the test, so a new one cannot go
 * unchecked. Each site's pattern, with its {@code maxLength} and {@code not: {const}}, is then compared with the
 * kernel predicate over the kernel's hostile rows and a bounded exhaustive and seeded random corpus.
 */
class IdentifierPatternParityTest {

    private static final Path CONTRACTS =
            Path.of(System.getProperty("charter.repositoryRoot")).resolve("contracts");
    private static final String SMALL_ALPHABET = "abg09-_A:";
    private static final String FULL_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789-_A:";
    private static final int EXHAUSTIVE_LENGTH = 5;
    private static final int RANDOM_ROWS = 20_000;
    private static final long SEED = 585L;
    private static final int PART_CAP = 64;

    private static List<IdentifierSite> sites;
    private static List<String> corpus;

    @BeforeAll
    static void discover() {
        sites = IdentifierSite.discover(CONTRACTS);
        corpus = corpus();
    }

    @Test
    void everyIdentifierLikeSiteMapsToAKernelGrammar() {
        List<String> unmapped = sites.stream()
                .filter(site -> KernelGrammar.identifierLike(site.pattern()))
                .filter(site -> grammar(site).isEmpty())
                .map(site -> site.location() + " " + site.pattern())
                .toList();
        assertThat(unmapped)
                .as("identifier-like patterns with no kernel grammar")
                .isEmpty();
    }

    @Test
    void theDiscoveryReachesEveryGrammarAndTheNamedSchemas() {
        Set<KernelGrammar> seen = EnumSet.noneOf(KernelGrammar.class);
        Set<String> files = new LinkedHashSet<>();
        for (IdentifierSite site : sites) {
            grammar(site).ifPresent(grammar -> {
                seen.add(grammar);
                files.add(site.location().substring(0, site.location().indexOf('#')));
            });
        }
        assertThat(seen).containsExactlyInAnyOrder(KernelGrammar.values());
        assertThat(files)
                .contains(
                        "packs/schemas/paradigm.schema.json",
                        "packs/schemas/styleguide.schema.json",
                        "packs/schemas/tactic.schema.json",
                        "packs/schemas/agent-profile.schema.json",
                        "charter/parameters/PackId.yaml");
    }

    @Test
    void everyMappedSiteGivesTheKernelVerdict() {
        List<String> drift = new ArrayList<>();
        for (IdentifierSite site : sites) {
            grammar(site).ifPresent(grammar -> disagreement(site, grammar).ifPresent(drift::add));
        }
        assertThat(drift)
                .as("sites whose schema verdict differs from the kernel's")
                .isEmpty();
    }

    /** The documented exception (#580): the schema caps the whole slot id, the service each part at 64. */
    @Test
    void aSlotPartOverSixtyFourPassesTheSchemaAndIsRefusedByTheService() {
        String longPart = "a".repeat(PART_CAP + 1) + ":b";
        List<IdentifierSite> slotSites = sites.stream()
                .filter(site -> grammar(site).orElse(null) == KernelGrammar.SLOT_ID)
                .toList();
        assertThat(slotSites)
                .isNotEmpty()
                .allSatisfy(site -> assertThat(site.accepts(longPart, new EcmaPattern(site.pattern())))
                        .as(site.location())
                        .isTrue());
        assertThat(SlotId.tryParse(longPart)).isEmpty();
        assertThat(SlotId.tryParse("a".repeat(PART_CAP) + ":b")).isPresent();
    }

    @Test
    void theEcmaRewriteKeepsTheEndAnchorStrict() {
        assertThat(new EcmaPattern("^[a-z]+$").accepts("a\n")).isFalse();
        assertThat(new EcmaPattern("[a-z]+").accepts("-a-")).isTrue();
        assertThat(EcmaPattern.toJava("^[.$]a.$")).isEqualTo("^[.$]a[^\\n\\r\\u2028\\u2029]\\z");
    }

    private static Optional<KernelGrammar> grammar(IdentifierSite site) {
        return KernelGrammar.of(site.pattern(), site.maxLength(), RoleBearer.GENERIC.equals(site.forbidden()));
    }

    private static Optional<String> disagreement(IdentifierSite site, KernelGrammar grammar) {
        EcmaPattern compiled = new EcmaPattern(site.pattern());
        for (String text : rows(site)) {
            boolean schema = site.accepts(text, compiled);
            if (schema != grammar.accepts(text)) {
                return Optional.of(site.location() + " (" + grammar + "): schema " + (schema ? "accepts" : "refuses")
                        + " '" + text.replace("\n", "\\n") + "', the kernel does not agree");
            }
        }
        return Optional.empty();
    }

    private static List<String> rows(IdentifierSite site) {
        List<String> rows = new ArrayList<>(corpus);
        rows.addAll(capRows(site.maxLength() == null ? PART_CAP : site.maxLength()));
        return rows;
    }

    /** The kernel's hostile rows, and the cap and cap+1 for whole ids and for each slot part. */
    private static List<String> capRows(int cap) {
        List<String> rows = new ArrayList<>();
        for (int length : new int[] {cap - 1, cap, cap + 1, PART_CAP - 1, PART_CAP, PART_CAP + 1}) {
            rows.add("a".repeat(length));
            rows.add("1" + "a".repeat(Math.max(0, length - 1)));
            rows.add("a-" + "b".repeat(Math.max(0, length - 2)));
        }
        String part = "a".repeat(PART_CAP);
        rows.addAll(List.of(part + ":" + part, part + ":" + part + "a", part + "a:" + part));
        return rows;
    }

    private static List<String> corpus() {
        Set<String> rows = new LinkedHashSet<>(List.of(
                "",
                "a",
                "a--b",
                "a-",
                "-a",
                "1a",
                "9",
                "a_b",
                "A",
                "aB",
                "a\n",
                "a.b",
                "a b",
                "a:b",
                "a:",
                ":a",
                "a::b",
                "a:b:c",
                "1a:b",
                "a:1b",
                "a-:b",
                "a:b-",
                RoleBearer.GENERIC,
                "generic-a",
                "generica",
                "generi",
                "a-generic",
                "generic:a"));
        exhaustive("", rows);
        Random random = new Random(SEED);
        for (int i = 0; i < RANDOM_ROWS; i++) {
            StringBuilder row = new StringBuilder();
            for (int length = 1 + random.nextInt(12); row.length() < length; ) {
                row.append(FULL_ALPHABET.charAt(random.nextInt(FULL_ALPHABET.length())));
            }
            rows.add(row.toString());
        }
        return List.copyOf(rows);
    }

    private static void exhaustive(String prefix, Set<String> rows) {
        rows.add(prefix);
        if (prefix.length() == EXHAUSTIVE_LENGTH) {
            return;
        }
        for (int i = 0; i < SMALL_ALPHABET.length(); i++) {
            exhaustive(prefix + SMALL_ALPHABET.charAt(i), rows);
        }
    }
}
