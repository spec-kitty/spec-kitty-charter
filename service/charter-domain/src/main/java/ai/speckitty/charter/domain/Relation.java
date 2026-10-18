package ai.speckitty.charter.domain;

import java.util.Arrays;
import java.util.Optional;

/** The relation of a DRG edge (docs/architecture/logical-model.md, "Relations"). */
public enum Relation {
    REQUIRES, SUGGESTS, APPLIES, SCOPE, VOCABULARY, INSTANTIATES, REFINES, SPECIALIZES_FROM, DELEGATES_TO,
    ENHANCES, OVERRIDES, REPLACES, IN_TENSION_WITH, RECONCILES_TENSION, REJECTS;

    public String token() { return name().toLowerCase(java.util.Locale.ROOT); }

    public static Optional<Relation> fromToken(String token) {
        return Arrays.stream(values()).filter(r -> r.token().equals(token)).findFirst();
    }
}
