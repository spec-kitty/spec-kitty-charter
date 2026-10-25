package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.DoctrineGraph;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackPolicy.GraphBuild;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The read &rarr; upsert-on-pair &rarr; serialise cycle for registered relationships, operating on shard YAML
 * <em>text</em>; file placement and the atomic directory swap are the WP04 commit store's job.
 *
 * <h2>Where registered relationships persist (analysis finding U1)</h2>
 *
 * Registered relationships persist to a dedicated pack graph shard at the pack root, {@value #SHARD_PATH}, in
 * the canonical {@code *.graph.yaml} shape {@link GraphShardWriter} emits ({@code schema_version},
 * {@code generated_at}, {@code nodes}, {@code edges}). This convention is chosen so that:
 *
 * <ol>
 *   <li><strong>It round-trips losslessly (PROJECT_002) and never rewrites a source shard on read</strong>
 *       (ADR-0027 Decision 2): the built-in {@code *.graph.yaml} shards and {@code drg/fragment.yaml} are left
 *       untouched; registered relationships live in their own shard, merged on the {@code {from, to}} pair.</li>
 *   <li><strong>It reuses the canonical shard YAML shape</strong> the reader and writer already agree on, so a
 *       write is read back identically.</li>
 *   <li><strong>It is covered by the read-path freshness digest.</strong> {@code PackFileSet} already digests
 *       every root {@code *.graph.yaml} regular file, so {@code PackSources.fingerprint()} sees this shard with
 *       no change to the fingerprint's file set. A {@code drg/}-nested shard would fall outside that glob,
 *       which is why the root {@code *.graph.yaml} convention is used.</li>
 * </ol>
 *
 * The WP04 commit store performs the actual placement and atomic swap of {@value #SHARD_PATH}.
 */
public final class RelationshipShard {

    /** The pack-relative path registered relationships persist to: a root shard the fingerprint already covers. */
    public static final String SHARD_PATH = "registered.graph.yaml";

    private RelationshipShard() {}

    /**
     * What a shard read yielded: the relationships that bound, and one finding for anything that did not
     * (a malformed document, or an edge whose endpoint is not a URN, whose relation is unknown, or that is a
     * self-loop). A fault is a finding, never a silent drop and never a throw (ADR-0027).
     *
     * @param relationships the relationships that bound, in document order
     * @param findings one finding per thing that did not bind, in document order
     */
    public record ShardRead(List<Relationship> relationships, List<PackFinding> findings) {
        /**
         * Copies the lists.
         *
         * @param relationships the relationships
         * @param findings the findings
         */
        public ShardRead {
            relationships = List.copyOf(relationships);
            findings = List.copyOf(findings);
        }
    }

    /**
     * Reads a registered-relationship shard's text into relationships, reporting what did not bind. It never
     * throws on content; a malformed shard surfaces as findings with no relationships.
     *
     * @param packName the pack's name, which prefixes a finding's origin
     * @param shardYamlText the shard's YAML text
     * @return the relationships and the findings
     */
    public static ShardRead read(String packName, String shardYamlText) {
        FileRead<RawGraph> read = GraphDocumentReader.read(shardYamlText, Shape.SHARD, SHARD_PATH);
        return switch (read) {
            case FileRead.Unreadable<RawGraph> bad -> new ShardRead(List.of(), PackPolicy.unreadable(packName, bad));
            case FileRead.Read<RawGraph> ok -> {
                GraphBuild build = PackPolicy.graph(packName, ok.value());
                yield new ShardRead(build.graph().relationships(), build.findings());
            }
            case FileRead.Absent<RawGraph> absent -> new ShardRead(List.of(), List.of());
        };
    }

    /**
     * Upserts {@code incoming} into {@code existing} on the unordered {@code {from, to}} pair, last-write-wins.
     * This is an explicit put, not a {@code Set.add}: any relationship already on the pair — in either
     * orientation — is removed and {@code incoming} is appended, so re-registering a pair replaces it with the
     * latest type, condition and rationale (spec US1 scenario 3), and registering the reverse orientation
     * replaces rather than adds (a pair holds at most one relationship).
     *
     * @param existing the relationships registered so far, in registration order
     * @param incoming the relationship to register
     * @return the merged relationships; neither input changes
     */
    public static List<Relationship> upsert(List<Relationship> existing, Relationship incoming) {
        List<Relationship> merged = new ArrayList<>(existing.size() + 1);
        for (Relationship relationship : existing) {
            if (!relationship.equals(incoming)) {
                merged.add(relationship);
            }
        }
        merged.add(incoming);
        return List.copyOf(merged);
    }

    /**
     * Serialises {@code relationships} to canonical shard text via a {@link DoctrineGraph} and
     * {@link GraphShardWriter}. The shard is made self-describing: it carries a node for each distinct endpoint
     * URN (in first-seen order), so a read-back is lossless.
     *
     * @param relationships the relationships to persist, in order
     * @return the shard's YAML text
     */
    public static String write(List<Relationship> relationships) {
        Map<Urn, DrgNode> nodes = new LinkedHashMap<>();
        for (Relationship relationship : relationships) {
            nodes.putIfAbsent(relationship.from(), new DrgNode(relationship.from(), Optional.empty()));
            nodes.putIfAbsent(relationship.to(), new DrgNode(relationship.to(), Optional.empty()));
        }
        return GraphShardWriter.write(new DoctrineGraph(nodes.values(), relationships));
    }
}
