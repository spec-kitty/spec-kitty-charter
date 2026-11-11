package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The kinds a cascade walks into (the contract's {@code CascadeScope}): none when the request has no {@code cascade},
 * every activatable kind, {@code mission_type} included (rule W21, A7), for {@code ["all"]}, or the kinds named.
 * {@code all} stands alone: mixed with kinds it is an {@code all_mixed_with_kinds} violation at {@code /cascade} (422).
 */
public final class CascadeScope {

    /** The contract's token for every kind. */
    public static final String ALL_TOKEN = "all";

    private static final CascadeScope NONE = new CascadeScope(false, Set.of());

    /** The kinds a cascade walks into: every activatable kind, {@code mission_type} included. */
    private static final Set<ArtifactKind> CASCADE_KINDS =
            Collections.unmodifiableSet(EnumSet.copyOf(Arrays.stream(ArtifactKind.values())
                    .filter(ArtifactKind::activatable)
                    .toList()));

    private final boolean cascades;
    private final Set<ArtifactKind> kinds;

    private CascadeScope(boolean cascades, Set<ArtifactKind> kinds) {
        this.cascades = cascades;
        this.kinds = kinds.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(kinds));
    }

    /**
     * No cascade: the write touches the named artifact only.
     *
     * @return the empty scope
     */
    public static CascadeScope none() {
        return NONE;
    }

    /**
     * Every kind ({@code ["all"]}).
     *
     * @return the scope of every kind
     */
    public static CascadeScope all() {
        return new CascadeScope(true, CASCADE_KINDS);
    }

    /**
     * The scope a request names.
     *
     * @param written the {@code cascade} member as written; empty when absent, which means no cascade
     * @return the scope and the violations met; the scope is {@link #none()} when there is a violation
     */
    public static Parsed parse(Optional<List<String>> written) {
        List<String> tokens = written.orElseGet(List::of);
        if (tokens.isEmpty()) {
            return new Parsed(NONE, List.of());
        }
        if (tokens.contains(ALL_TOKEN)) {
            return tokens.stream().allMatch(ALL_TOKEN::equals)
                    ? new Parsed(all(), List.of())
                    : refused(new RefusalViolation(
                            "/cascade",
                            "all_mixed_with_kinds",
                            "A cascade is [\"all\"] or a list of kinds, not both."));
        }
        return kinds(tokens);
    }

    private static Parsed kinds(List<String> tokens) {
        Set<ArtifactKind> kinds = EnumSet.noneOf(ArtifactKind.class);
        List<RefusalViolation> violations = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Optional<ArtifactKind> kind = ArtifactKind.fromToken(tokens.get(i)).filter(CASCADE_KINDS::contains);
            kind.ifPresent(kinds::add);
            if (kind.isEmpty()) {
                violations.add(new RefusalViolation(
                        "/cascade/" + i, "unknown_kind", "A cascade walks into no '" + tokens.get(i) + "' artifacts."));
            }
        }
        return violations.isEmpty() ? new Parsed(new CascadeScope(true, kinds), List.of()) : refused(violations);
    }

    private static Parsed refused(RefusalViolation violation) {
        return refused(List.of(violation));
    }

    private static Parsed refused(List<RefusalViolation> violations) {
        return new Parsed(NONE, violations);
    }

    /**
     * Whether the write cascades at all.
     *
     * @return {@code false} for {@link #none()}
     */
    public boolean cascades() {
        return cascades;
    }

    /**
     * Whether the cascade walks into a kind.
     *
     * @param kind the kind
     * @return {@code true} when the scope names it
     */
    public boolean admits(ArtifactKind kind) {
        return kinds.contains(kind);
    }

    /**
     * The kinds the cascade walks into.
     *
     * @return the kinds, unmodifiable; empty for {@link #none()}
     */
    public Set<ArtifactKind> kinds() {
        return kinds;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof CascadeScope that && cascades == that.cascades && kinds.equals(that.kinds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cascades, kinds);
    }

    @Override
    public String toString() {
        return cascades ? "CascadeScope" + kinds : "CascadeScope[none]";
    }

    /**
     * A scope as parsed, with the violations met.
     *
     * @param scope the scope; {@link #none()} when a violation was met
     * @param violations the violations, empty when the scope is well formed
     */
    public record Parsed(CascadeScope scope, List<RefusalViolation> violations) {

        /**
         * Checks and copies the fields.
         *
         * @param scope the scope
         * @param violations the violations
         */
        public Parsed {
            Objects.requireNonNull(scope, "scope");
            violations = List.copyOf(violations);
        }
    }
}
