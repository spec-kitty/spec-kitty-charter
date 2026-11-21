package ai.speckitty.charter.contracts.identifiers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RoleBearer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The {@code <pack>::} qualifier of every reference pattern of the contracts gives the kernel's verdict on a pack name
 * (#582, ADR-0075 decision 2): the pack-name part of a reference is a pack id. {@link IdentifierPatternParityTest}
 * leaves out every pattern that holds {@code ::}, because a reference is a grammar of its own; this test reads those
 * patterns. A qualifier site is any pattern that holds {@code ::}, discovered from the schema files, and each {@code ::}
 * in it must close a group that opens with the kernel's written slug, {@link IdentifierGrammar#SLUG_AS_WRITTEN},
 * wherever the group stands: at the start of the pattern, inside an alternation, or before {@code guard:}.
 *
 * <p>The documented exception: a pattern cannot cap one part of a longer string without lookahead, and the charter
 * contract carries none, so a qualifier of more than {@value PackIdentifier#MAX_LENGTH} characters passes the schema and
 * the service refuses it.
 */
class QualifierParityTest {

    private static final Path CONTRACTS =
            Path.of(System.getProperty("charter.repositoryRoot")).resolve("contracts");

    /** The qualifier group as every site must open it. */
    private static final String GROUP = "(" + IdentifierGrammar.SLUG_AS_WRITTEN + "::";

    /** The URN parts a qualifier site may be followed by; a site accepts at least one after {@code a::}. */
    private static final List<String> SUFFIXES = List.of("directive:D1", "term:a/b", "tdd", "asset:x", "guard:x");

    /** The qualifier sites each schema file holds, the census of #582. */
    private static final Map<String, Integer> SITES_PER_FILE = new TreeMap<>(Map.ofEntries(
            Map.entry("charter/schemas/GlossaryContextTerm.yaml", 1),
            Map.entry("charter/schemas/GlossaryTerm.yaml", 1),
            Map.entry("charter/schemas/PackQualifiedReference.yaml", 1),
            Map.entry("charter/schemas/PackQualifiedReferenceWrite.yaml", 1),
            Map.entry("charter/schemas/VocabularyTerm.yaml", 1),
            Map.entry("packs/schemas/glossary.schema.json", 1),
            Map.entry("packs/schemas/graph-shard.schema.json", 3),
            Map.entry("packs/schemas/guard.schema.json", 1),
            Map.entry("packs/schemas/lifecycle.schema.json", 1),
            Map.entry("packs/schemas/mission-type.schema.json", 1),
            Map.entry("packs/schemas/org-fragment.schema.json", 3)));

    private static List<IdentifierSite> qualifierSites;

    @BeforeAll
    static void discover() {
        qualifierSites = IdentifierSite.discover(CONTRACTS).stream()
                .filter(site -> site.pattern().contains("::"))
                .toList();
    }

    @Test
    void theDiscoveryFindsEveryQualifierSite() {
        Map<String, Integer> perFile = qualifierSites.stream()
                .collect(Collectors.groupingBy(
                        site -> site.location().substring(0, site.location().indexOf('#')),
                        TreeMap::new,
                        Collectors.summingInt(site -> 1)));

        assertThat(perFile).isEqualTo(SITES_PER_FILE);
    }

    @Test
    void everyQualifierIsWrittenAsTheKernelsSlug() {
        List<String> drift = new ArrayList<>();
        for (IdentifierSite site : qualifierSites) {
            String pattern = site.pattern();
            for (int at = pattern.indexOf("::"); at >= 0; at = pattern.indexOf("::", at + 2)) {
                int start = at + 2 - GROUP.length();
                if (start < 0 || !pattern.startsWith(GROUP, start)) {
                    drift.add(site.location() + " " + pattern);
                }
            }
        }
        assertThat(drift)
                .as("qualifier sites whose pack name is not the kernel's slug")
                .isEmpty();
    }

    /** Up to the cap, the written slug and the kernel agree on every row of the corpus. */
    @Test
    void theWrittenQualifierGivesTheKernelsVerdictUpToTheCap() {
        EcmaPattern written = new EcmaPattern("^" + IdentifierGrammar.SLUG_AS_WRITTEN + "$");
        List<String> drift = new ArrayList<>();
        for (String row : corpus()) {
            if (row.length() <= PackIdentifier.MAX_LENGTH
                    && written.accepts(row) != PackQualifiedReference.isPackName(row)) {
                drift.add(row);
            }
        }
        assertThat(drift).as("rows the written slug and the kernel judge apart").isEmpty();
    }

    /**
     * The documented exception: every qualifier site accepts a qualifier of {@value PackIdentifier#MAX_LENGTH} + 1
     * characters wherever it accepts {@code a}, and the kernel refuses it. Each site first accepts {@code a::} with one
     * of the known suffixes, so the check cannot pass on a site that refuses everything.
     */
    @Test
    void aQualifierOverTheCapPassesEverySiteAndIsRefusedByTheService() {
        String overCap = "p".repeat(PackIdentifier.MAX_LENGTH + 1);
        String atCap = "p".repeat(PackIdentifier.MAX_LENGTH);
        for (IdentifierSite site : qualifierSites) {
            EcmaPattern compiled = new EcmaPattern(site.pattern());
            Optional<String> suffix = SUFFIXES.stream()
                    .filter(candidate -> site.accepts("a::" + candidate, compiled))
                    .findFirst();
            assertThat(suffix)
                    .as("%s accepts a::<urn> for a known urn", site.location())
                    .isPresent();
            assertThat(site.accepts("a_b::" + suffix.get(), compiled))
                    .as("%s refuses a_b", site.location())
                    .isFalse();
            assertThat(site.accepts(atCap + "::" + suffix.get(), compiled))
                    .as("%s accepts a qualifier at the cap", site.location())
                    .isTrue();
            assertThat(site.accepts(overCap + "::" + suffix.get(), compiled))
                    .as("%s accepts a qualifier over the cap (the documented exception)", site.location())
                    .isTrue();
        }
        assertThat(PackQualifiedReference.isPackName(atCap)).isTrue();
        assertThat(PackQualifiedReference.isPackName(overCap)).isFalse();
    }

    /**
     * Each site, not only the written slug, gives the kernel's verdict on every corpus row of at most the cap, written
     * as the qualifier of a URN the site accepts. A row holding a colon or a line break is left out: it is not a pack
     * name the qualifier could hold, and how a URN part may hold a colon is not the qualifier's grammar.
     */
    @Test
    void everySiteGivesTheKernelsVerdictOnTheQualifierUpToTheCap() {
        List<String> drift = new ArrayList<>();
        for (IdentifierSite site : qualifierSites) {
            EcmaPattern compiled = new EcmaPattern(site.pattern());
            String suffix = SUFFIXES.stream()
                    .filter(candidate -> site.accepts("a::" + candidate, compiled))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(site.location() + " accepts no a::<urn>"));
            for (String row : corpus()) {
                if (row.length() <= PackIdentifier.MAX_LENGTH
                        && !row.isEmpty()
                        && row.indexOf(':') < 0
                        && row.indexOf('\n') < 0
                        && site.accepts(row + "::" + suffix, compiled) != PackQualifiedReference.isPackName(row)) {
                    drift.add(site.location() + " '" + row + "'");
                }
            }
        }
        assertThat(drift).as("qualifier rows a site and the kernel judge apart").isEmpty();
    }

    /** A pack qualifier is written {@code ::}; no pattern spells the separator another way, which discovery would miss. */
    @Test
    void noPatternSpellsTheSeparatorAnotherWay() {
        List<String> spelled = IdentifierSite.discover(CONTRACTS).stream()
                .filter(site -> site.pattern().contains(":{2}")
                        || site.pattern().contains("[:][:]")
                        || site.pattern().contains("\\:\\:"))
                .map(site -> site.location() + " " + site.pattern())
                .toList();
        assertThat(spelled).isEmpty();
    }

    /**
     * The URN part of a reference or a URN does not start with a colon (#582), so {@code a_b::directive:D1} is not read
     * as a bare URN of kind {@code a_b}; a URN whose id holds colons and dots stays valid. The kernel reads the same
     * rows: it splits {@code a_b::} off as a qualifier and refuses it.
     */
    @Test
    void theUrnPartDoesNotStartWithAColon() {
        List<IdentifierSite> urnSites = IdentifierSite.discover(CONTRACTS).stream()
                .filter(site -> List.of(
                                "charter/schemas/Urn.yaml#",
                                "charter/schemas/PackQualifiedReference.yaml#",
                                "charter/schemas/PackQualifiedReferenceWrite.yaml#")
                        .contains(site.location()))
                .toList();
        assertThat(urnSites).hasSize(3);
        for (IdentifierSite site : urnSites) {
            EcmaPattern compiled = new EcmaPattern(site.pattern());
            for (String accepted : List.of("directive:D1", "principle:keep.it:whole", "term:a/b")) {
                assertThat(site.accepts(accepted, compiled))
                        .as("%s accepts %s", site.location(), accepted)
                        .isTrue();
            }
            for (String refused : List.of("a_b::directive:D1", "directive::x", "directive:", "directive:\nx")) {
                assertThat(site.accepts(refused, compiled))
                        .as("%s refuses %s", site.location(), refused)
                        .isFalse();
            }
        }
        assertThatThrownBy(() -> PackQualifiedReference.parse("a_b::directive:D1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(PackIdentifier.GRAMMAR_DETAIL);
        assertThat(PackQualifiedReference.parse("principle:keep.it:whole").urn())
                .isEqualTo("principle:keep.it:whole");
    }

    /** The pack-name and pack-id fields that stand alone carry the pack-id pattern and cap, so they map to PACK_ID. */
    @Test
    void thePackNameAndPackIdFieldsMapToThePackIdGrammar() {
        List<String> packIdSites = IdentifierSite.discover(CONTRACTS).stream()
                .filter(site ->
                        KernelGrammar.of(site.pattern(), site.maxLength(), RoleBearer.GENERIC.equals(site.forbidden()))
                                        .orElse(null)
                                == KernelGrammar.PACK_ID)
                .map(IdentifierSite::location)
                .toList();

        assertThat(packIdSites)
                .contains(
                        "packs/schemas/pack-descriptor.schema.json#/properties/name",
                        "charter/schemas/PackRegistration.yaml#/properties/name",
                        "_shared/schemas/RequiringPack.yaml#/properties/packId",
                        "_shared/schemas/RequiringPack.yaml#/properties/via/items");
    }

    private static List<String> corpus() {
        List<String> rows = new ArrayList<>(List.of(
                "",
                "a",
                "_",
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
                "open_packs-2"));
        IntStream.of(PackIdentifier.MAX_LENGTH - 1, PackIdentifier.MAX_LENGTH, PackIdentifier.MAX_LENGTH + 1)
                .forEach(length -> {
                    rows.add("a".repeat(length));
                    rows.add("a-" + "b".repeat(length - 2));
                    rows.add("a_" + "b".repeat(length - 2));
                });
        String alphabet = "ab09-_A";
        int size = alphabet.length();
        for (int row = 0; row < size * size * size; row++) {
            rows.add("" + alphabet.charAt(row / (size * size)) + alphabet.charAt(row / size % size)
                    + alphabet.charAt(row % size));
        }
        return rows;
    }
}
