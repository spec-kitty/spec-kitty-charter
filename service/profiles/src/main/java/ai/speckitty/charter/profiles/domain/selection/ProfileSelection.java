package ai.speckitty.charter.profiles.domain.selection;

import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.RoleFit;
import ai.speckitty.charter.profiles.domain.SpecializationContext;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Which in-force profile fills a role: ADR-0069 decision 4's order, as ADR-0078 rules it (D2 to D4). Every filter runs
 * before the tier is picked:
 *
 * <ol>
 *   <li>reserved roles: {@code generic} is refused; {@code operator} never falls back, and keeps human-performed
 *       candidates only: the entity already refuses an agent-performed profile that takes {@code operator}
 *       ({@link AgentProfile#OPERATOR_ROLE_HUMAN_ONLY}), so role fit alone leaves only humans and no filter repeats it;
 *   <li>pin: only the pinned profile and its in-force specializations;
 *   <li>performer: a human-performed candidate only for {@code operator} or when pinned;
 *   <li>role fit: a candidate that takes the role, as primary or alt-role;
 *   <li>context: a specialization that declares {@code languages} or {@code frameworks} must fit the asked context;
 *   <li>tier: the best role fit;
 *   <li>depth: the longest in-force chain;
 *   <li>priority: the highest {@code routing-priority}, absent counting as {@value #DEFAULT_ROUTING_PRIORITY};
 *   <li>tie: every candidate left, sorted by closure position then id;
 *   <li>none: the fallback, or no match.
 * </ol>
 *
 * <p>The pinned profile is never among the tied: a surviving specialization of it is deeper, so ADR-0032 decision 9's
 * pin tie-break is unreachable and has no branch here. Selection reads each candidate's declared fields only (D6).
 */
public final class ProfileSelection {

    /** The {@code routing-priority} the schema reads when a profile declares none. */
    public static final int DEFAULT_ROUTING_PRIORITY = 50;

    private static final Comparator<Scored> TIE_ORDER = Comparator.<Scored>comparingInt(
                    scored -> scored.candidate().closurePosition())
            .thenComparing(scored -> scored.candidate().profileId());

    private ProfileSelection() {}

    /**
     * Selects the profile that fills a role.
     *
     * @param role the asked role
     * @param context the asked context
     * @param candidates the candidates, pin and fallback in force
     * @return the picked profiles, the fallback, or no match
     * @throws IllegalArgumentException when the role is the reserved {@value AgentProfile#GENERIC_ROLE}
     */
    public static SelectionAnswer select(String role, SelectionContext context, Candidates candidates) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(candidates, "candidates");
        if (AgentProfile.GENERIC_ROLE.equals(role)) {
            throw new IllegalArgumentException(
                    "The role " + AgentProfile.GENERIC_ROLE + " is served only as a fallback.");
        }
        boolean operator = AgentProfile.OPERATOR_ROLE.equals(role);
        List<SelectionCandidate> filtered = candidates.candidates().stream()
                .filter(pinned(candidates))
                .filter(performer(operator, candidates))
                .filter(candidate -> candidate.profile().roleFit(role).fits())
                .filter(fitsContext(context, candidates))
                .toList();
        List<Scored> left = byPriority(byDepth(byTier(filtered.stream()
                .map(candidate -> new Scored(candidate, candidate.profile().roleFit(role)))
                .toList())));
        if (!left.isEmpty()) {
            return matched(left);
        }
        return fallback(operator, candidates);
    }

    // Step 2: a pin keeps the pinned profile and its in-force specializations.
    private static Predicate<SelectionCandidate> pinned(Candidates candidates) {
        return candidate -> candidates.pinnedProfileId().isEmpty()
                || candidates.isPinned(candidate)
                || candidates.pinnedSpecializationIds().contains(candidate.profileId());
    }

    // Step 3: a human performs only the operator role, or a role pinned to it.
    private static Predicate<SelectionCandidate> performer(boolean operator, Candidates candidates) {
        return candidate -> operator || !candidate.profile().humanPerformed() || candidates.isPinned(candidate);
    }

    // Step 5: only a specialization that declares languages or frameworks, and is not pinned, is judged.
    private static Predicate<SelectionCandidate> fitsContext(SelectionContext context, Candidates candidates) {
        return candidate -> {
            SpecializationContext declared = candidate.profile().specializationContext();
            if (candidate.depth() == 0
                    || (declared.languages().isEmpty() && declared.frameworks().isEmpty())
                    || candidates.isPinned(candidate)) {
                return true;
            }
            return !contradicts(context.language(), declared.languages())
                    && !contradicts(context.framework(), declared.frameworks())
                    && (matches(context.language(), declared.languages())
                            || matches(context.framework(), declared.frameworks()));
        };
    }

    // An asked dimension the candidate declares, without the asked value.
    private static boolean contradicts(Optional<String> asked, List<String> declared) {
        return asked.isPresent() && !declared.isEmpty() && !declared.contains(asked.get());
    }

    // An asked dimension the candidate declares, with the asked value.
    private static boolean matches(Optional<String> asked, List<String> declared) {
        return asked.filter(declared::contains).isPresent();
    }

    // Step 6: the best role fit left.
    private static List<Scored> byTier(List<Scored> scored) {
        RoleFit best = scored.stream()
                .map(Scored::fit)
                .reduce(RoleFit.NONE, (kept, next) -> next.outranks(kept) ? next : kept);
        return scored.stream().filter(candidate -> candidate.fit() == best).toList();
    }

    // Step 7: the longest in-force chain.
    private static List<Scored> byDepth(List<Scored> scored) {
        int deepest = scored.stream()
                .mapToInt(candidate -> candidate.candidate().depth())
                .max()
                .orElse(0);
        return scored.stream()
                .filter(candidate -> candidate.candidate().depth() == deepest)
                .toList();
    }

    // Step 8: the highest routing priority, absent counting as the default.
    private static List<Scored> byPriority(List<Scored> scored) {
        int highest = scored.stream().mapToInt(ProfileSelection::priority).max().orElse(0);
        return scored.stream()
                .filter(candidate -> priority(candidate) == highest)
                .toList();
    }

    private static int priority(Scored scored) {
        return scored.candidate().profile().routingPriority().orElse(DEFAULT_ROUTING_PRIORITY);
    }

    // Step 9: one pick, or every tied one in closure order, then by id.
    private static SelectionAnswer matched(List<Scored> left) {
        List<Pick> picks = left.stream()
                .sorted(TIE_ORDER)
                .map(scored -> new Pick(
                        scored.candidate().profileId(), scored.candidate().pack(), Fit.of(scored.fit())))
                .toList();
        return new SelectionAnswer.Matched(picks, picks.size() > 1);
    }

    // Step 10: the fallback, never for operator.
    private static SelectionAnswer fallback(boolean operator, Candidates candidates) {
        return candidates
                .fallback()
                .filter(fallback -> !operator)
                .<SelectionAnswer>map(fallback -> new SelectionAnswer.Matched(
                        List.of(new Pick(fallback.profileId(), fallback.pack(), Fit.FALLBACK)), false))
                .orElseGet(SelectionAnswer.NoMatch::new);
    }

    private record Scored(SelectionCandidate candidate, RoleFit fit) {}
}
