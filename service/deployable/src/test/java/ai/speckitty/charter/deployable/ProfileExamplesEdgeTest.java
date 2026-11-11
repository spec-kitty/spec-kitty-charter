package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * The {@code edge} of every governance entry in the two agent profile examples of the contract,
 * {@code Artifact.agent_profile.yaml} and {@code EffectiveAgentProfileRead.java-jenny.yaml}, is the one the
 * {@code built-in} example pack's graph files (ADR-0070) write for the edge the entry was reached through: the same {@code when} and
 * {@code reason}, and {@code {}} where the edge states neither. The examples were wrong twice (a reason cut short, a
 * reason-only edge shown as {@code {}}); this keeps them equal to the pack.
 *
 * <p>The only entries not checked are those the example's own summary declares invented, in a sentence "Invented and
 * illustrative: <urn>[, <urn>]." Such an urn must not be a target of any edge of the pack, or the declaration would hide
 * a real entry.
 *
 * <p>The contract directory is {@code charter.contractsDir} when set, else {@code contracts/charter} of the nearest
 * enclosing directory that holds one. Nothing here skips.
 */
class ProfileExamplesEdgeTest {

    private static final List<String> EXAMPLES =
            List.of("Artifact.agent_profile.yaml", "EffectiveAgentProfileRead.java-jenny.yaml");
    private static final Pattern INVENTED =
            Pattern.compile("Invented and illustrative: ([A-Za-z_]+:[A-Za-z0-9_.-]+(?:, [A-Za-z_]+:[A-Za-z0-9_.-]+)*)");

    @Test
    void everyEdgeOfTheProfileExamplesIsTheOneThePackGraphWrites() throws IOException {
        Path examples = contracts().resolve("examples");
        Map<String, Set<Map<String, String>>> written =
                PackGraphEdges.words(ExamplePacks.packsDirectory(), List.of(ExamplePacks.BUILT_IN));
        Set<String> targets = new HashSet<>();
        written.keySet().forEach(key -> targets.add(key.substring(key.lastIndexOf(' ') + 1)));
        List<String> problems = new ArrayList<>();
        int checked = 0;
        int worded = 0;
        for (String name : EXAMPLES) {
            Map<String, Object> example = yaml(Files.readString(examples.resolve(name)));
            Set<String> invented = inventedUrns(String.valueOf(example.get("summary")));
            for (String urn : invented) {
                if (targets.contains(urn)) {
                    problems.add(name + ": " + urn + " is declared invented but a pack edge targets it");
                }
            }
            Set<String> seen = new HashSet<>();
            for (Object item : list(map(example.get("value")).get("governance"))) {
                Map<String, Object> entry = map(item);
                String urn = entry.containsKey("urn")
                        ? String.valueOf(entry.get("urn"))
                        : entry.get("kind") + ":" + entry.get("artifactId");
                if (invented.contains(urn)) {
                    seen.add(urn);
                    continue;
                }
                String key = entry.get("via") + " " + entry.get("relation") + " " + urn;
                if (!(entry.get("edge") instanceof Map<?, ?>)) {
                    problems.add(name + ": " + key + " has no edge object: " + entry.get("edge"));
                    continue;
                }
                Map<String, String> actual = new LinkedHashMap<>();
                map(entry.get("edge")).forEach((word, value) -> actual.put(word, String.valueOf(value)));
                checked++;
                if (!actual.isEmpty()) {
                    worded++;
                }
                if (!written.getOrDefault(key, Set.of()).contains(actual)) {
                    problems.add(
                            name + ": the edge of " + key + " is " + actual + ", the graph writes " + written.get(key));
                }
            }
            if (!seen.equals(invented)) {
                problems.add(name + ": the summary declares " + invented + " invented, the entries hold " + seen);
            }
        }
        assertThat(problems).isEmpty();
        assertThat(checked).as("edges checked").isPositive();
        assertThat(worded).as("edges that state a word").isPositive();
    }

    private static Path contracts() {
        String set = System.getProperty("charter.contractsDir");
        if (set != null) {
            return Path.of(set);
        }
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("contracts/charter/examples"))) {
                return dir.resolve("contracts/charter");
            }
        }
        throw new IllegalStateException(
                "no contracts/charter/examples above " + Path.of("").toAbsolutePath());
    }

    private static Set<String> inventedUrns(String summary) {
        Matcher matcher = INVENTED.matcher(summary.replaceAll("\\s+", " "));
        Set<String> urns = new HashSet<>();
        if (matcher.find()) {
            urns.addAll(List.of(matcher.group(1).split(", ")));
        }
        return urns;
    }

    private static Map<String, Object> yaml(String text) {
        return map(new Load(LoadSettings.builder().build()).loadFromString(text));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }
}
