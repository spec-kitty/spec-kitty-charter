package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What an adapter read from one pack root: facts only, with no verdict. {@link PackPolicy} decides every code and
 * every text from these.
 *
 * @param root the pack that was read
 * @param descriptor what {@code pack.yaml} gave
 * @param shards the root {@code *.graph.yaml} files, in name order
 * @param fragment what {@code drg/fragment.yaml} gave
 * @param files every file of the pack with its classification; filled by a full read only
 * @param stepContracts the step ids each step contract declares; filled by a full read only
 * @param artifacts the kind and id each artifact declares; filled by a full read only
 * @param assets the path and MIME type each asset declares; filled by a full read only
 * @param unreadableFiles every other classified file that could not be parsed or broke its schema: an artifact, an
 *     org charter, a project charter or a graph document outside the load path; filled by a full read only
 */
public record PackContents(
        PackRoot root,
        FileRead<PackDescriptor> descriptor,
        List<FileRead<RawGraph>> shards,
        FileRead<RawGraph> fragment,
        List<ClassifiedFile> files,
        List<StepContractFacts> stepContracts,
        List<ArtifactFacts> artifacts,
        List<AssetFacts> assets,
        List<FileRead.Unreadable<?>> unreadableFiles) {
    /**
     * Checks the fields and copies the lists.
     *
     * @param root the pack that was read
     * @param descriptor what {@code pack.yaml} gave
     * @param shards the root shards
     * @param fragment what {@code drg/fragment.yaml} gave
     * @param files the classified files
     * @param stepContracts the step contract facts
     * @param artifacts the artifact facts
     * @param assets the asset facts
     * @param unreadableFiles the other files that could not be read
     */
    public PackContents {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(fragment, "fragment");
        shards = List.copyOf(shards);
        files = List.copyOf(files);
        stepContracts = List.copyOf(stepContracts);
        artifacts = List.copyOf(artifacts);
        assets = List.copyOf(assets);
        unreadableFiles = List.copyOf(unreadableFiles);
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
     * rules refuse though it parses: a file the file-kinds table retires (#194, ADR-0044 decision 6), or a mission type
     * whose companions or named templates the load refuses. Validate mode refuses what the load refuses.
     *
     * @param file the pack-relative path
     * @param firstLine the first line of the parser's message, or of the refusal
     * @param refusal what the store's rule says of the file when it is refused rather than unparseable; empty for a
     *     parse failure
     */
    public record ParseFailure(String file, String firstLine, Optional<String> refusal) {
        /**
         * Checks the fields.
         *
         * @param file the pack-relative path
         * @param firstLine the first line of the message
         * @param refusal the store rule's refusal, possibly empty
         */
        public ParseFailure {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(firstLine, "firstLine");
            Objects.requireNonNull(refusal, "refusal");
        }

        /**
         * A parser failure.
         *
         * @param file the pack-relative path
         * @param firstLine the first line of the parser's message
         */
        public ParseFailure(String file, String firstLine) {
            this(file, firstLine, Optional.empty());
        }

        /**
         * The failure of a file the file-kinds table retires: it is refused by name, unread.
         *
         * @param file the pack-relative path
         * @param reason the retired rule's reason
         * @return the failure, refused as {@code is retired: <reason>}
         */
        public static ParseFailure retired(String file, String reason) {
            return refused(file, "is retired: " + reason);
        }

        /**
         * The failure of a file that parses but that a store rule refuses, as the load does.
         *
         * @param file the pack-relative path
         * @param refusal what the rule says, read after the file's origin ({@code is retired: ...})
         * @return the failure
         */
        public static ParseFailure refused(String file, String refusal) {
            return new ParseFailure(file, refusal, Optional.of(refusal));
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
     * @param label the label, when there is one
     */
    public record RawNode(String ref, String kind, Optional<String> label) {
        /**
         * Checks the fields.
         *
         * @param ref the URN or id
         * @param kind the kind as written
         * @param label the label, possibly empty
         */
        public RawNode {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(label, "label");
        }
    }

    /**
     * An edge as written.
     *
     * @param source the source endpoint as written
     * @param target the target endpoint as written
     * @param relation the relation as written
     * @param impacts the {@code impacts} weight, when written
     * @param when the {@code when} text, when written
     * @param reason the {@code reason} text, when written
     */
    public record RawEdge(
            String source,
            String target,
            String relation,
            Optional<Double> impacts,
            Optional<String> when,
            Optional<String> reason) {
        /**
         * Checks the fields.
         *
         * @param source the source endpoint
         * @param target the target endpoint
         * @param relation the relation
         * @param impacts the weight, possibly empty
         * @param when the {@code when} text, possibly empty
         * @param reason the {@code reason} text, possibly empty
         */
        public RawEdge {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(relation, "relation");
            Objects.requireNonNull(impacts, "impacts");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * A pack file with what it is: a kind, or the reason it is not validated.
     *
     * @param file the pack-relative path
     * @param kind the file kind; empty for an excluded file
     * @param exclusionClass why the file is excluded; empty for a classified file
     */
    public record ClassifiedFile(String file, Optional<String> kind, Optional<String> exclusionClass) {
        /**
         * Checks that exactly one of kind and exclusion class is set.
         *
         * @param file the pack-relative path
         * @param kind the file kind, possibly empty
         * @param exclusionClass the exclusion class, possibly empty
         */
        public ClassifiedFile {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(exclusionClass, "exclusionClass");
            if (kind.isPresent() == exclusionClass.isPresent()) {
                throw new IllegalArgumentException("exactly one of kind and exclusionClass must be set: " + file);
            }
        }
    }

    /**
     * The step ids one step contract declares.
     *
     * @param file the pack-relative path
     * @param stepIds the step ids, in file order
     */
    public record StepContractFacts(String file, List<String> stepIds) {
        /**
         * Checks the fields and copies the ids.
         *
         * @param file the pack-relative path
         * @param stepIds the step ids
         */
        public StepContractFacts {
            Objects.requireNonNull(file, "file");
            stepIds = List.copyOf(stepIds);
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
