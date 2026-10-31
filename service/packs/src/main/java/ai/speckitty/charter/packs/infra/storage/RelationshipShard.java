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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The read and serialise halves of the registered-relationship cycle, operating on shard YAML <em>text</em>; the
 * upsert on the {@code {from, to}} pair is the application's ({@code RelationshipMerge}); file placement and the atomic directory swap are the WP04 commit store's job.
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

    /** The {@code generated_by} the registration store stamps on the shard it writes; a source shard does not carry it. */
    static final String WRITTEN_BY = "charter-registration";

    private RelationshipShard() {}

    /**
     * Whether {@code text} is a shard this store wrote. {@value #SHARD_PATH} is reserved for the registration
     * store, which rewrites it whole on every commit, so a pack's own source shard under that name would be
     * merged into the registered relationships and then overwritten. The store's stamp tells the two apart.
     *
     * @param text the text of a pack's {@value #SHARD_PATH}
     * @return whether the registration store wrote it
     */
    static boolean isWrittenByRegistration(String text) {
        return ParsedYaml.parse(text, SHARD_PATH).document() instanceof Map<?, ?> root
                && WRITTEN_BY.equals(root.get("generated_by"));
    }

    /**
     * What a shard read yielded: the relationships that bound, and one finding for anything that did not
     * (a malformed document, or an edge whose endpoint is not a URN, whose relation is unknown, that is a self-loop,
     * or that is a tension, which is authored in a pack's graph shards and never registered). A fault is a finding,
     * never a silent drop and never a throw (ADR-0027).
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
                GraphBuild build = PackPolicy.registeredGraph(packName, ok.value());
                yield new ShardRead(build.graph().relationships(), build.findings());
            }
            case FileRead.Absent<RawGraph> absent -> new ShardRead(List.of(), List.of());
        };
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
        return GraphShardWriter.write(new DoctrineGraph(nodes.values(), relationships), WRITTEN_BY);
    }
}
