package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What the one reader read from one pack root: storage-agnostic facts only, with no verdict and no read fault (#554, S1,
 * FR-016). A file the read refused yields no fact here; the read faults, the classification of the files, the files the
 * store does not hold, the manifest and the reserved shard are the storage edge's ({@code TreeRead}, {@code TreeCensus}),
 * and the domain port {@link PackReading} carries what of them the edges need in its {@link PackReadout}.
 *
 * <p>{@code root} names the pack: the content rules word their findings with its configured name, and validate mode's
 * placement rules read the name from it (WP09).
 *
 * @param root the pack that was read
 * @param descriptor what {@code pack.yaml} gave
 * @param shards the root {@code *.graph.yaml} files, in name order
 * @param fragment what {@code drg/fragment.yaml} gave
 * @param artifacts the kind and id each artifact declares; filled by a full read only
 * @param assets the path and MIME type each asset declares; filled by a full read only
 * @param stepEnhancements the step enhancements, their companions and the pack's graph edges as plain facts; filled by
 *     a full read only
 * @param promptSlots the slot parse of each step prompt the read could parse, over its canonical text; filled by a
 *     full read only. A prompt the codec refuses is a read fault instead and has no parse (A5).
 * @param missionTypes the id and step ids of each mission type the read could parse; filled by a full read only
 * @param assembly the whole input of the aggregate's assembly, read from the same tree: the held artifacts and the
 *     faults the load meets, as domain values ({@code PackAssembly} builds the aggregate from it alone); {@link
 *     Assembly.None} for facts a test writes by hand
 */
public record PackContents(
        PackRoot root,
        FileRead<PackDescriptor> descriptor,
        List<FileRead<RawGraph>> shards,
        FileRead<RawGraph> fragment,
        List<ArtifactFacts> artifacts,
        List<AssetFacts> assets,
        StepEnhancementFacts stepEnhancements,
        List<PromptSlotFacts> promptSlots,
        List<MissionTypeFacts> missionTypes,
        Assembly assembly) {
    /**
     * Checks the fields and copies the lists.
     *
     * @param root the pack that was read
     * @param descriptor what {@code pack.yaml} gave
     * @param shards the root shards
     * @param fragment what {@code drg/fragment.yaml} gave
     * @param artifacts the artifact facts
     * @param assets the asset facts
     * @param stepEnhancements the step enhancement facts
     * @param promptSlots the slot parse of each step prompt
     * @param missionTypes the mission type facts
     * @param assembly the assembly input
     */
    public PackContents {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(fragment, "fragment");
        shards = List.copyOf(shards);
        artifacts = List.copyOf(artifacts);
        assets = List.copyOf(assets);
        Objects.requireNonNull(stepEnhancements, "stepEnhancements");
        promptSlots = List.copyOf(promptSlots);
        missionTypes = List.copyOf(missionTypes);
        Objects.requireNonNull(assembly, "assembly");
    }

    /**
     * Facts with no assembly input: what a test writes by hand to judge the rules, which read no aggregate.
     *
     * @param root the pack that was read
     * @param descriptor what {@code pack.yaml} gave
     * @param shards the root shards
     * @param fragment what {@code drg/fragment.yaml} gave
     * @param artifacts the artifact facts
     * @param assets the asset facts
     * @param stepEnhancements the step enhancement facts
     * @param promptSlots the slot parse of each step prompt
     * @param missionTypes the mission type facts
     */
    public PackContents(
            PackRoot root,
            FileRead<PackDescriptor> descriptor,
            List<FileRead<RawGraph>> shards,
            FileRead<RawGraph> fragment,
            List<ArtifactFacts> artifacts,
            List<AssetFacts> assets,
            StepEnhancementFacts stepEnhancements,
            List<PromptSlotFacts> promptSlots,
            List<MissionTypeFacts> missionTypes) {
        this(
                root,
                descriptor,
                shards,
                fragment,
                artifacts,
                assets,
                stepEnhancements,
                promptSlots,
                missionTypes,
                new Assembly.None());
    }

    /**
     * The whole input of the aggregate's assembly, as the one read found it (#554, S1, S6, T025): what the load holds
     * and every fault it meets, as domain values, so the aggregate is built from the read that was judged and no
     * storage type crosses the port.
     */
    public sealed interface Assembly {

        /**
         * What the reference closure judges of the read: the assembled read's facts, and none for a read the load refused
         * or facts a test writes by hand.
         *
         * @return the facts
         */
        default ReferenceFacts references() {
            return ReferenceFacts.NONE;
        }

        /** No assembly input: facts a test writes by hand. */
        record None() implements Assembly {}

        /**
         * The load refused the pack as a whole: a root that cannot be read, a descriptor that is missing or
         * unreadable, or an aggregate invariant the readable artifacts break ({@code Pack.Builder.build()}).
         *
         * @param graphFaults the faults of the graph documents read before the refusal, in read order
         * @param code the refusal's code
         * @param message the refusal's message
         * @param atDescriptor whether the fault is the descriptor's, so it is placed at {@code pack.yaml}
         */
        record Refused(List<PackElementViolation> graphFaults, RefusalCode code, String message, boolean atDescriptor)
                implements Assembly {
            /**
             * Checks the fields and copies the faults.
             *
             * @param graphFaults the graph faults
             * @param code the code
             * @param message the message
             * @param atDescriptor whether the fault is the descriptor's
             */
            public Refused {
                graphFaults = List.copyOf(graphFaults);
                Objects.requireNonNull(code, "code");
                Objects.requireNonNull(message, "message");
            }
        }

        /**
         * What the load holds, and every fault it met, in the load's order.
         *
         * @param id the pack's identity, the {@code pack_id} its descriptor declares
         * @param descriptor the descriptor
         * @param artifacts each held artifact with the file it is stored in, in assembly order
         * @param assetFiles the held asset files
         * @param presets the held presets
         * @param document the pack document, when held
         * @param violations the faults the assembly met at its files (a broken file, a value-object breach, a
         *     reference that does not close, a repeated id, a load rule's fault), then the manifest's and the named
         *     policy file's
         * @param assembled how many of {@code violations} the assembly met before the manifest's and the policy file's
         * @param graphFaults the faults of the graph documents that could not be read, in read order
         * @param warnings the load's findings that do not refuse the pack
         * @param storeOnly the faults of {@code violations} only the store half meets, which no reader or rule states
         *     (#554, A-M1 rows 16 to 19 and 32): what the registration's verdict states of the store half
         * @param policyText the named policy file's text, when it is valid
         * @param policyFault why the named policy file cannot be read, when it cannot
         * @param facts the composition facts, when every graph document was read and they could be built
         * @param factsRefusal why the composition facts could not be built, when they could not
         * @param references what the reference closure judges of the read (#355): the candidates the load meets before
         *     it leaves any out, from the same read the aggregate is built from
         */
        record Assembled(
                PackIdentifier id,
                PackDescriptor descriptor,
                List<Placed> artifacts,
                List<AssetFile> assetFiles,
                List<Preset> presets,
                Optional<PackDocument> document,
                List<PackElementViolation> violations,
                int assembled,
                List<PackElementViolation> graphFaults,
                List<Reason> warnings,
                List<PackElementViolation> storeOnly,
                Optional<String> policyText,
                Optional<String> policyFault,
                Optional<PackCompositionFacts> facts,
                Optional<String> factsRefusal,
                ReferenceFacts references)
                implements Assembly {
            /**
             * Checks the fields and copies the collections.
             *
             * @param id the pack's identity
             * @param descriptor the descriptor
             * @param artifacts the held artifacts
             * @param assetFiles the asset files
             * @param presets the presets
             * @param document the document
             * @param violations the faults
             * @param assembled how many faults the assembly met
             * @param graphFaults the graph faults
             * @param warnings the warnings
             * @param storeOnly the faults only the store half meets
             * @param policyText the policy text
             * @param policyFault the policy fault
             * @param facts the composition facts
             * @param factsRefusal why the facts could not be built
             * @param references what the reference closure judges
             */
            public Assembled {
                Objects.requireNonNull(id, "id");
                Objects.requireNonNull(descriptor, "descriptor");
                artifacts = List.copyOf(artifacts);
                assetFiles = List.copyOf(assetFiles);
                presets = List.copyOf(presets);
                Objects.requireNonNull(document, "document");
                violations = List.copyOf(violations);
                if (assembled < 0 || assembled > violations.size()) {
                    throw new IllegalArgumentException("assembled is out of range: " + assembled);
                }
                graphFaults = List.copyOf(graphFaults);
                warnings = List.copyOf(warnings);
                storeOnly = List.copyOf(storeOnly);
                Objects.requireNonNull(policyText, "policyText");
                Objects.requireNonNull(policyFault, "policyFault");
                Objects.requireNonNull(facts, "facts");
                Objects.requireNonNull(factsRefusal, "factsRefusal");
                Objects.requireNonNull(references, "references");
            }
        }

        /**
         * A held artifact and the pack-relative file it is stored in.
         *
         * @param file the file
         * @param artifact the artifact
         */
        record Placed(String file, CharterArtifact artifact) {
            /**
             * Checks the fields.
             *
             * @param file the file
             * @param artifact the artifact
             */
            public Placed {
                Objects.requireNonNull(file, "file");
                Objects.requireNonNull(artifact, "artifact");
            }
        }
    }

    /**
     * The slots one step prompt declares, parsed from its canonical text.
     *
     * @param file the prompt's pack-relative path
     * @param parse the slots and the faults of the canonical text ({@code MarkdownCodec.write} of the parsed prompt,
     *     frontmatter included, A5)
     */
    public record PromptSlotFacts(String file, PromptSlots.Parse parse) {
        /**
         * Checks the fields.
         *
         * @param file the prompt's pack-relative path
         * @param parse the parse
         */
        public PromptSlotFacts {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(parse, "parse");
        }
    }

    /**
     * What reading one file gave. A rule fires only on {@link Absent} or {@link Read}.
     *
     * @param <T> what a successful read yields
     */
    public sealed interface FileRead<T> {

        /**
         * The file's path relative to the pack root.
         *
         * @return the pack-relative path
         */
        String file();

        /**
         * The file does not exist.
         *
         * @param file the pack-relative path
         * @param <T> what a successful read would have yielded
         */
        record Absent<T>(String file) implements FileRead<T> {
            /**
             * Checks the path.
             *
             * @param file the pack-relative path
             */
            public Absent {
                Objects.requireNonNull(file, "file");
            }
        }

        /**
         * The file exists but could not be parsed, or broke its schema: exactly one of {@code parse} and
         * {@code violations} says why.
         *
         * @param file the pack-relative path
         * @param parse the parse failure; {@code null} when the file parsed
         * @param violations the schema violations; empty when the file did not parse
         * @param <T> what a successful read would have yielded
         */
        record Unreadable<T>(String file, @Nullable ParseFailure parse, List<SchemaViolation> violations)
                implements FileRead<T> {
            /**
             * Checks the path, copies the violations and checks that exactly one of the parse failure and the
             * violations is given: a file that is unreadable has a reason, and only one.
             *
             * @param file the pack-relative path
             * @param parse the parse failure, possibly {@code null}
             * @param violations the schema violations
             * @throws IllegalArgumentException when both or neither of the parse failure and the violations are given
             */
            public Unreadable {
                Objects.requireNonNull(file, "file");
                violations = List.copyOf(violations);
                if ((parse != null) == !violations.isEmpty()) {
                    throw new IllegalArgumentException(
                            "exactly one of a parse failure and schema violations must be given: " + file);
                }
            }
        }

        /**
         * The file was read.
         *
         * @param file the pack-relative path
         * @param value what the file gave
         * @param <T> what the file yields
         */
        record Read<T>(String file, T value) implements FileRead<T> {
            /**
             * Checks the fields.
             *
             * @param file the pack-relative path
             * @param value what the file gave
             */
            public Read {
                Objects.requireNonNull(file, "file");
                Objects.requireNonNull(value, "value");
            }
        }
    }

    /**
     * A schema validator's error reduced to facts; no message text crosses the boundary.
     *
     * @param file the pack-relative path
     * @param pointer the JSON pointer segments of the offending place
     * @param keyword the schema keyword that failed
     * @param property the offending property name, when the keyword names one
     * @param value the offending value, when the keyword reports one
     * @param args any further keyword arguments
     */
    public record SchemaViolation(
            String file,
            List<String> pointer,
            String keyword,
            Optional<String> property,
            Optional<String> value,
            Map<String, String> args) {
        /**
         * Checks the fields and copies the collections.
         *
         * @param file the pack-relative path
         * @param pointer the pointer segments
         * @param keyword the failed keyword
         * @param property the offending property, possibly empty
         * @param value the offending value, possibly empty
         * @param args the keyword arguments
         */
        public SchemaViolation {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(keyword, "keyword");
            Objects.requireNonNull(property, "property");
            Objects.requireNonNull(value, "value");
            pointer = List.copyOf(pointer);
            args = Map.copyOf(args);
        }
    }

    /**
     * A parser failure, reduced to the parser's first line so that no host path appears; or a file the store's own
     * rules refuse though it parses: a file the file-kinds table retires (#194, ADR-0044 decision 6), an asset sidecar
     * with no payload, a reserved shard, or a mission type whose companions or named templates the load refuses.
     * Validate mode refuses what the load refuses, under the code the storage edge gives the refusal (#554, R2).
     *
     * @param file the pack-relative path
     * @param firstLine the first line of the parser's message, or of the refusal
     * @param refusal what the store's rule says of the file when it is refused rather than unparseable; empty for a
     *     parse failure
     * @param code the code of the refusal, which the reader that refused the file names; empty for a parse failure
     */
    public record ParseFailure(String file, String firstLine, Optional<String> refusal, Optional<ReasonCode> code) {
        /**
         * Checks the fields.
         *
         * @param file the pack-relative path
         * @param firstLine the first line of the message
         * @param refusal the store rule's refusal, possibly empty
         * @param code the refusal's code, possibly empty
         */
        public ParseFailure {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(firstLine, "firstLine");
            Objects.requireNonNull(refusal, "refusal");
            Objects.requireNonNull(code, "code");
        }

        /**
         * A parser failure.
         *
         * @param file the pack-relative path
         * @param firstLine the first line of the parser's message
         */
        public ParseFailure(String file, String firstLine) {
            this(file, firstLine, Optional.empty(), Optional.empty());
        }

        /**
         * The failure of a file that parses but that a store rule refuses, as the load does, under the rule's code.
         *
         * @param file the pack-relative path
         * @param refusal what the rule says, read after the file ({@code <file>: <refusal>})
         * @param code the code the load refuses the file with
         * @return the failure
         */
        public static ParseFailure refused(String file, String refusal, ReasonCode code) {
            return new ParseFailure(file, refusal, Optional.of(refusal), Optional.of(code));
        }
    }

    /**
     * A graph document as written, before any binding or validation.
     *
     * @param file the pack-relative path
     * @param shape whether the file is a root shard or the org fragment
     * @param nodes the nodes as written
     * @param edges the edges as written
     */
    public record RawGraph(String file, Shape shape, List<RawNode> nodes, List<RawEdge> edges) {
        /** The two shapes a graph document takes. */
        public enum Shape {
            /** A root {@code *.graph.yaml} shard: nodes carry URNs. */
            SHARD,
            /** {@code drg/fragment.yaml}: nodes carry an id and a plural kind. */
            FRAGMENT
        }

        /**
         * Checks the fields and copies the lists.
         *
         * @param file the pack-relative path
         * @param shape the document shape
         * @param nodes the nodes as written
         * @param edges the edges as written
         */
        public RawGraph {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(shape, "shape");
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
        }
    }

    /**
     * A node as written.
     *
     * @param ref the URN of a shard node, or the id of a fragment node
     * @param kind the kind as written
     * @param label the label (a shard's {@code label}, a fragment's {@code title}), when there is one
     * @param provenance a shard node's {@code provenance}, when written
     * @param tags a shard node's {@code tags} in written order; empty when none are written
     * @param bodyPath a fragment node's {@code body_path}, when written
     */
    public record RawNode(
            String ref,
            String kind,
            Optional<String> label,
            Optional<String> provenance,
            List<String> tags,
            Optional<String> bodyPath) {
        /**
         * Checks the fields and copies the tags.
         *
         * @param ref the URN or id
         * @param kind the kind as written
         * @param label the label, possibly empty
         * @param provenance the provenance, possibly empty
         * @param tags the tags, possibly empty
         * @param bodyPath the body path, possibly empty
         */
        public RawNode {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(provenance, "provenance");
            tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
            Objects.requireNonNull(bodyPath, "bodyPath");
        }

        /**
         * A node with no provenance, no tags and no body path.
         *
         * @param ref the URN or id
         * @param kind the kind as written
         * @param label the label, possibly empty
         */
        public RawNode(String ref, String kind, Optional<String> label) {
            this(ref, kind, label, Optional.empty(), List.of(), Optional.empty());
        }
    }

    /**
     * An edge as written.
     *
     * @param source the source endpoint as written
     * @param target the target endpoint as written
     * @param relation the relation as written
     * @param when the {@code when} text, when written
     * @param reason the {@code reason} text, when written
     */
    public record RawEdge(
            String source, String target, String relation, Optional<String> when, Optional<String> reason) {
        /**
         * Checks the fields.
         *
         * @param source the source endpoint
         * @param target the target endpoint
         * @param relation the relation
         * @param when the {@code when} text, possibly empty
         * @param reason the {@code reason} text, possibly empty
         */
        public RawEdge {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(relation, "relation");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * The kind and id one artifact declares.
     *
     * @param file the pack-relative path
     * @param kind the artifact kind
     * @param id the artifact id
     */
    public record ArtifactFacts(String file, String kind, String id) {
        /**
         * Checks the fields.
         *
         * @param file the pack-relative path
         * @param kind the artifact kind
         * @param id the artifact id
         */
        public ArtifactFacts {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * One asset a manifest declares.
     *
     * @param file the pack-relative path of the manifest
     * @param id the asset id
     * @param path the declared asset path
     * @param mime the declared MIME type
     * @param containedInAssetsRoot whether the path stays inside the assets root; the adapter computes it,
     *     following symlinks
     */
    public record AssetFacts(String file, String id, String path, String mime, boolean containedInAssetsRoot) {
        /**
         * Checks the fields.
         *
         * @param file the pack-relative path of the manifest
         * @param id the asset id
         * @param path the declared path
         * @param mime the declared MIME type
         * @param containedInAssetsRoot whether the path stays inside the assets root
         */
        public AssetFacts {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(mime, "mime");
        }
    }
}
