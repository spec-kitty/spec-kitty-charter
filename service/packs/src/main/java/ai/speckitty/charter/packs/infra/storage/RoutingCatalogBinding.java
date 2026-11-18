package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.routing.domain.AccessMethod;
import ai.speckitty.charter.routing.domain.CatalogSource;
import ai.speckitty.charter.routing.domain.Grade;
import ai.speckitty.charter.routing.domain.ModelCost;
import ai.speckitty.charter.routing.domain.ModelEntry;
import ai.speckitty.charter.routing.domain.RoutingCatalog;
import ai.speckitty.charter.routing.domain.RoutingObjective;
import ai.speckitty.charter.routing.domain.RoutingPolicy;
import ai.speckitty.charter.routing.domain.RoutingWeights;
import ai.speckitty.charter.routing.domain.TaskFit;
import ai.speckitty.charter.routing.domain.TaskType;
import ai.speckitty.charter.routing.domain.TierConstraint;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Function;

/**
 * Binds the {@link ArtifactKind#ROUTING_CATALOG} kind (ADR-0080 decision 5): one file per catalog at
 * {@code routing_catalogs/<id>.routing-catalog.yaml}, whose stem is its id. A number the file holds becomes an exact
 * decimal ({@link BigDecimal#valueOf(double)} for a YAML float, which gives back the authored decimal for any value of up
 * to fifteen significant digits); the ranking then never leaves decimal arithmetic. The way back writes an integral value
 * as an integer and any other as a float, so a file round-trips as it was read.
 */
enum RoutingCatalogBinding implements KindBinding<RoutingCatalogDso, RoutingCatalog> {
    INSTANCE;

    /** The directory and file token of a catalog: {@code routing_catalogs/<id>.routing-catalog.yaml}. */
    private static final String TOKEN = "routing-catalog";

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.ROUTING_CATALOG;
    }

    @Override
    public Class<RoutingCatalog> entityType() {
        return RoutingCatalog.class;
    }

    @Override
    public String fileToken() {
        return TOKEN;
    }

    @Override
    public String schemaName() {
        return TOKEN;
    }

    /** A catalog's place: {@code routing_catalogs/<id>.routing-catalog.yaml} (ADR-0080 decision 5). */
    @Override
    public String fileName(String elementId) {
        return ArtifactKind.ROUTING_CATALOG.plural() + "/" + elementId + "." + TOKEN + ".yaml";
    }

    @Override
    public boolean idIsFileStem() {
        return true;
    }

    @Override
    public boolean placedAtItsFileName() {
        return true;
    }

    @Override
    public Class<RoutingCatalogDso> dsoType() {
        return RoutingCatalogDso.class;
    }

    @Override
    public String idOf(RoutingCatalogDso dso) {
        return dso.id();
    }

    @Override
    public RoutingCatalog toEntity(PackIdentifier pack, RoutingCatalogDso dso) {
        return new RoutingCatalog(
                dso.id(),
                pack,
                dso.description(),
                dso.generatedAt(),
                dso.taskTypes().stream()
                        .map(t -> new TaskType(
                                t.id(),
                                t.title(),
                                t.description(),
                                t.qualitySensitivity().map(token -> parse(token, Grade::ofToken, "a grade")),
                                t.costSensitivity().map(token -> parse(token, Grade::ofToken, "a grade"))))
                        .toList(),
                dso.models().stream().map(RoutingCatalogBinding::model).toList(),
                new RoutingPolicy(
                        parse(dso.policy().objective(), RoutingObjective::ofToken, "an objective"),
                        new RoutingWeights(
                                decimal(dso.policy().weights().quality()),
                                decimal(dso.policy().weights().cost()),
                                decimal(dso.policy().weights().risk()),
                                decimal(dso.policy().weights().latency())),
                        dso.policy().tierConstraints().stream()
                                .map(c -> new TierConstraint(
                                        c.taskType(), parse(c.maxTier(), CostTier::ofToken, "a cost tier")))
                                .toList()),
                dso.sources().stream()
                        .map(s -> new CatalogSource(
                                s.name(),
                                s.url(),
                                parse(s.accessMethod(), AccessMethod::ofToken, "an access method"),
                                s.snapshotAt(),
                                s.licenseNotes()))
                        .toList());
    }

    @Override
    public RoutingCatalogDso toDso(RoutingCatalog catalog) {
        return new RoutingCatalogDso(
                catalog.id(),
                catalog.description(),
                catalog.generatedAt(),
                catalog.taskTypes().stream()
                        .map(t -> new RoutingCatalogDso.TaskType(
                                t.id(),
                                t.title(),
                                t.description(),
                                t.qualitySensitivity().map(Grade::token),
                                t.costSensitivity().map(Grade::token)))
                        .toList(),
                catalog.models().stream().map(RoutingCatalogBinding::modelDso).toList(),
                new RoutingCatalogDso.Policy(
                        catalog.policy().objective().token(),
                        new RoutingCatalogDso.Weights(
                                number(catalog.policy().weights().quality()),
                                number(catalog.policy().weights().cost()),
                                number(catalog.policy().weights().risk()),
                                number(catalog.policy().weights().latency())),
                        catalog.policy().tierConstraints().stream()
                                .map(c -> new RoutingCatalogDso.Constraint(
                                        c.taskType(), c.maxTier().token()))
                                .toList()),
                catalog.sources().stream()
                        .map(s -> new RoutingCatalogDso.Source(
                                s.name(), s.url(), s.accessMethod().token(), s.snapshotAt(), s.licenseNotes()))
                        .toList());
    }

    private static ModelEntry model(RoutingCatalogDso.Model m) {
        return new ModelEntry(
                m.id(),
                m.provider(),
                m.family(),
                m.tools(),
                m.strengths(),
                m.weaknesses(),
                m.taskFit().stream()
                        .map(f -> new TaskFit(
                                f.taskType(),
                                decimal(f.score()),
                                f.confidence().map(token -> parse(token, Grade::ofToken, "a grade")),
                                f.rationale()))
                        .toList(),
                new ModelCost(
                        parse(m.cost().tier(), CostTier::ofToken, "a cost tier"),
                        m.cost().inputPer1mUsd().map(RoutingCatalogBinding::decimal),
                        m.cost().outputPer1mUsd().map(RoutingCatalogBinding::decimal),
                        m.cost().currency(),
                        m.cost().pricingSourceUrl()),
                m.latencyTier().map(token -> parse(token, Grade::ofToken, "a grade")));
    }

    private static RoutingCatalogDso.Model modelDso(ModelEntry m) {
        return new RoutingCatalogDso.Model(
                m.id(),
                m.provider(),
                m.family(),
                m.tools(),
                m.strengths(),
                m.weaknesses(),
                m.taskFit().stream()
                        .map(f -> new RoutingCatalogDso.Fit(
                                f.taskType(), number(f.score()), f.confidence().map(Grade::token), f.rationale()))
                        .toList(),
                new RoutingCatalogDso.Cost(
                        m.cost().tier().token(),
                        m.cost().inputPer1mUsd().map(RoutingCatalogBinding::number),
                        m.cost().outputPer1mUsd().map(RoutingCatalogBinding::number),
                        m.cost().currency(),
                        m.cost().pricingSourceUrl()),
                m.latencyTier().map(Grade::token));
    }

    /**
     * A token the schema admits, as its value. The schema refuses any other token at load, so an unknown one here is a
     * defect, not content.
     */
    private static <T> T parse(String token, Function<String, Optional<T>> of, String what) {
        return of.apply(token)
                .orElseThrow(() -> new IllegalStateException(
                        "a routing catalog's \"" + token + "\" passed the schema but names no " + what));
    }

    /** A number as the loader read it, as an exact decimal. */
    static BigDecimal decimal(Number number) {
        return switch (number) {
            case Integer i -> BigDecimal.valueOf(i.longValue());
            case Long l -> BigDecimal.valueOf(l);
            case BigInteger b -> new BigDecimal(b);
            case BigDecimal d -> d;
            default ->
                throw new IllegalStateException("the loader reads a finite number as an integer or a"
                        + " BigDecimal, not as a " + number.getClass().getSimpleName());
        };
    }

    /**
     * A decimal as the loader would read it back: one without a fraction ({@code 1}) as an integer, one written with
     * a fraction ({@code 1.0}, {@code 0.66}) as itself, so the file writes back with the digits it was read with.
     */
    static Number number(BigDecimal decimal) {
        if (decimal.scale() <= 0) {
            BigInteger integral = decimal.toBigIntegerExact();
            if (integral.bitLength() < Integer.SIZE) {
                return integral.intValue();
            }
            if (integral.bitLength() < Long.SIZE) {
                return integral.longValue();
            }
            return integral;
        }
        return decimal;
    }
}
