package ai.speckitty.charter.artifacts.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.artifacts.application.GlossaryReads;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.ClosureQueries;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.ProfileGovernance;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * SC-001 (#106): a client reads every one of the 139 terms of the example vocabulary by its address, one read each, in
 * a pack. The term ids and names come from the file itself, not from a read, and each is read through the production
 * {@link GlossaryReads#term} path over the real store of charter's example packs (ADR-0070). The positive control is
 * the count: the file holds 139 terms, and a term missing from the read, or one it cannot answer, fails the test.
 */
class ExampleVocabularyReadTest {

    private static final String CONTEXT = "spec-kitty-core";
    private static final int TERMS = 139;
    private static final Pattern TERM = Pattern.compile("(?m)^- id: (\\S+)\\n  name: (.+)$");

    /** The adapter over the packs slice's own read, over the real store of the three example packs. */
    private static GlossaryReads examplePacks() {
        List<PackLocation> locations = ExamplePacks.names().stream()
                .map(name -> new PackLocation(name, ExamplePacks.level(name), ExamplePacks.pack(name)))
                .toList();
        DomainRepositoryService store = new DomainRepositoryService(() -> locations, new YamlSerializationStrategy());
        ConsistentRead read = new ConsistentRead(() -> "sha256:" + "a".repeat(64));
        OfferingReadService offering = new OfferingReadService(store, store, store, read);
        ClosureQueries queries = new ClosureQueries(offering, store, read, store, store);
        ForeignArtifactRead foreign = new ForeignArtifactRead(
                store,
                store,
                store,
                read,
                pack -> PackRevision.compute(List.of(pack.value())),
                new ProfileGovernance(queries));
        return new PacksGlossaries(foreign);
    }

    /** The term ids of the example vocabulary, each with its name, in authored order, as the file writes them. */
    private static Map<String, String> termsOfTheFile() throws IOException {
        Path file = ExamplePacks.pack(ExamplePacks.BUILT_IN).resolve("glossaries/" + CONTEXT + ".glossary.yaml");
        Matcher matcher = TERM.matcher(Files.readString(file, StandardCharsets.UTF_8));
        Map<String, String> terms = new LinkedHashMap<>();
        while (matcher.find()) {
            terms.put(matcher.group(1), matcher.group(2));
        }
        return terms;
    }

    @Test
    void everyTermOfTheExampleVocabularyIsReadByItsAddress() throws IOException {
        Map<String, String> terms = termsOfTheFile();
        assertThat(terms)
                .as("the positive control: the example vocabulary's terms")
                .hasSize(TERMS);
        GlossaryReads glossaries = examplePacks();
        PackIdentifier builtIn = new PackIdentifier(ExamplePacks.BUILT_IN);

        terms.forEach((id, name) -> {
            GlossaryReads.TermRead read = glossaries.term(builtIn, CONTEXT, id);

            assertThat(read.term().id()).as(id).isEqualTo(id);
            assertThat(read.term().name()).as(id).isEqualTo(name);
            assertThat(read.term().urn()).as(id).isEqualTo("term:" + CONTEXT + "/" + id);
            assertThat(read.contextId()).as(id).isEqualTo(CONTEXT);
            assertThat(read.packId()).as(id).isEqualTo(builtIn);
        });
        assertThat(glossaries.context(builtIn, CONTEXT).context().terms())
                .as("the context answers exactly the terms the file holds, in its order")
                .extracting(term -> term.id())
                .containsExactlyElementsOf(terms.keySet());
    }
}
