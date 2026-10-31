package ai.speckitty.charter.deployable;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The dependency rule of docs/architecture/hexagonal-service.md, as a gate. */
class HexagonalArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("ai.speckitty.charter");
    }

    // ---- ADR-0006 rules -------------------------------------------------------------------------------------
    // Each rule is a named ArchRule over the final packages. The known-bad control of a rule is a class in a
    // `badfixture` package inside the very package the rule selects (test-only, so the production import below
    // never sees it); the control runs the unchanged production rule against it and must fail naming it.

    private static final String ROOT = "ai.speckitty.charter";
    private static final String KERNEL = ROOT + ".kernel..";
    private static final String PACKS_DOMAIN = ROOT + ".packs.domain..";
    private static final String PACKS_APPLICATION = ROOT + ".packs.application..";
    private static final String PACKS_STORAGE = ROOT + ".packs.infra.storage..";
    private static final String PACKS_API = ROOT + ".packs.infra.api..";
    /** The outbound adapter that fetches a guide by URI (#194). */
    private static final String PACKS_FETCH = ROOT + ".packs.infra.fetch..";

    private static final String PROFILES_DOMAIN = ROOT + ".profiles.domain..";
    /** The missions slice's domain (ADR-0044). */
    private static final String MISSIONS_DOMAIN = ROOT + ".missions.domain..";

    private static final String GENERATED_API = ROOT + ".api..";
    private static final String DEPLOYABLE = ROOT + ".deployable..";
    private static final String JAVA = "java..";
    private static final String FRAMEWORK = "org.springframework..";
    private static final String JSPECIFY = "org.jspecify.annotations..";
    /** Every slice domain and the shared kernel (including {@code kernel.validation}). */
    private static final String[] DOMAIN_RINGS = {KERNEL, PACKS_DOMAIN, PROFILES_DOMAIN, MISSIONS_DOMAIN};
    /** The vertical slices; each slice's domain may depend only on itself and the kernel. */
    private static final List<String> SLICES = List.of("packs", "profiles", "missions");

    private static void assertBites(ArchRule rule, String fixturePackage, String... violators) {
        JavaClasses fixtures = new ClassFileImporter().importPackages(fixturePackage);
        AbstractThrowableAssert<?, ?> failure =
                assertThatThrownBy(() -> rule.check(fixtures)).isInstanceOf(AssertionError.class);
        for (String violator : violators) {
            failure.hasMessageContaining(violator);
        }
    }

    /** ONION_DEPENDS_INWARD (1/3): a domain ring depends on nothing outward: not on application, infra or deployable. */
    private static final ArchRule ONION_DOMAIN_DEPENDS_ON_NOTHING_OUTWARD = noClasses()
            .that()
            .resideInAnyPackage(DOMAIN_RINGS)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infra..", DEPLOYABLE);

    /** ONION_DEPENDS_INWARD (2/3): the application ring depends on the domain, never on infra or deployable. */
    private static final ArchRule ONION_APPLICATION_DEPENDS_ON_DOMAIN_ONLY = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infra..", DEPLOYABLE);

    /** ONION_DEPENDS_INWARD (3/3): only the composition root composes the adapters. */
    private static final ArchRule ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS = noClasses()
            .that()
            .resideOutsideOfPackages(PACKS_API, PACKS_STORAGE, PACKS_FETCH, DEPLOYABLE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(PACKS_API, PACKS_STORAGE, PACKS_FETCH);

    /** ONION_DEPENDS_INWARD (3/3): nothing, the edge rings included, depends on the composition root. */
    private static final ArchRule ONION_NOTHING_DEPENDS_ON_COMPOSITION_ROOT = noClasses()
            .that()
            .resideOutsideOfPackage(DEPLOYABLE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(DEPLOYABLE);

    @Test
    @DisplayName("ONION_DEPENDS_INWARD")
    void onionDependsInward() {
        ONION_DOMAIN_DEPENDS_ON_NOTHING_OUTWARD.check(classes);
        ONION_APPLICATION_DEPENDS_ON_DOMAIN_ONLY.check(classes);
        ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS.check(classes);
        ONION_NOTHING_DEPENDS_ON_COMPOSITION_ROOT.check(classes);
    }

    @Test
    void theOnionRulesFailOnAClassThatReachesOutward() {
        assertBites(ONION_DOMAIN_DEPENDS_ON_NOTHING_OUTWARD, ROOT + ".packs.domain.badfixture", "DomainReachesOutward");
        assertBites(ONION_APPLICATION_DEPENDS_ON_DOMAIN_ONLY, ROOT + ".packs.application.badfixture", "ReachesStorage");
        assertBites(ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS, ROOT + ".packs.application.badfixture", "ReachesStorage");
        assertBites(
                ONION_NOTHING_DEPENDS_ON_COMPOSITION_ROOT,
                ROOT + ".packs.infra.api.badfixture",
                "ReachesCompositionRoot");
    }

    /**
     * DOMAIN_IS_PURE: a domain ring depends only on {@code java}, JSpecify, the kernel and its own slice's domain.
     * The kernel (with {@code kernel.validation}) depends on itself alone.
     */
    private static final ArchRule KERNEL_IS_PURE = noClasses()
            .that()
            .resideInAPackage(KERNEL)
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(KERNEL, JAVA, JSPECIFY);

    private static final ArchRule PACKS_DOMAIN_IS_PURE = noClasses()
            .that()
            .resideInAPackage(PACKS_DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(PACKS_DOMAIN, KERNEL, JAVA, JSPECIFY);

    /** The profiles slice's domain (ADR-0035): only itself, the kernel, {@code java} and JSpecify. */
    private static final ArchRule PROFILES_DOMAIN_IS_PURE = noClasses()
            .that()
            .resideInAPackage(PROFILES_DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(PROFILES_DOMAIN, KERNEL, JAVA, JSPECIFY);

    /** The missions slice's domain (ADR-0044): only itself, the kernel, {@code java} and JSpecify. */
    private static final ArchRule MISSIONS_DOMAIN_IS_PURE = noClasses()
            .that()
            .resideInAPackage(MISSIONS_DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(MISSIONS_DOMAIN, KERNEL, JAVA, JSPECIFY);

    /** DOMAIN_IS_PURE, annotation half: no domain class, field, method or constructor carries a framework annotation. */
    private static final ArchRule NO_FRAMEWORK_ANNOTATIONS_IN_DOMAIN = classes()
            .that()
            .resideInAnyPackage(DOMAIN_RINGS)
            .should(new ArchCondition<JavaClass>("carry no annotation beyond java and JSpecify") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    Stream.of(
                                    item.getAnnotations().stream(),
                                    item.getMembers().stream().flatMap(m -> m.getAnnotations().stream()))
                            .flatMap(s -> s)
                            .map(a -> a.getRawType())
                            .filter(t -> !t.getPackageName().startsWith("java.")
                                    && !t.getPackageName().startsWith("org.jspecify.annotations"))
                            .forEach(t -> events.add(SimpleConditionEvent.violated(
                                    item, item.getName() + " carries the framework annotation " + t.getName())));
                }
            });

    @Test
    @DisplayName("DOMAIN_IS_PURE")
    void domainIsPure() {
        KERNEL_IS_PURE.check(classes);
        PACKS_DOMAIN_IS_PURE.check(classes);
        PROFILES_DOMAIN_IS_PURE.check(classes);
        MISSIONS_DOMAIN_IS_PURE.check(classes);
        NO_FRAMEWORK_ANNOTATIONS_IN_DOMAIN.check(classes);
    }

    @Test
    void theDomainPurityRulesFailOnADomainClassWithAnOutsideDependency() {
        assertBites(
                PACKS_DOMAIN_IS_PURE, ROOT + ".packs.domain.badfixture", "DomainUsesSpring", "DomainReachesOutward");
        assertBites(PROFILES_DOMAIN_IS_PURE, ROOT + ".profiles.domain.badfixture", "ProfileDomainReachesPacks");
        assertBites(MISSIONS_DOMAIN_IS_PURE, ROOT + ".missions.domain.badfixture", "MissionDomainReachesPacks");
        assertBites(
                NO_FRAMEWORK_ANNOTATIONS_IN_DOMAIN,
                ROOT + ".packs.domain.badfixture",
                "DomainUsesSpring",
                "org.springframework.stereotype.Component");
    }

    /**
     * VALIDATION_LIBRARY_IS_PURE: {@code kernel.validation} depends only on itself, {@code java} and JSpecify, so
     * the library stays extractable and carries no third-party coupling.
     */
    private static final ArchRule VALIDATION_LIBRARY_IS_PURE = classes()
            .that()
            .resideInAPackage(ROOT + ".kernel.validation..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(ROOT + ".kernel.validation..", JAVA, JSPECIFY);

    @Test
    @DisplayName("VALIDATION_LIBRARY_IS_PURE")
    void validationLibraryIsPure() {
        VALIDATION_LIBRARY_IS_PURE.check(classes);
    }

    // The stand-in exists only to prove that the rule bites without the real third-party dependency.
    @Test
    void theValidationPurityRuleFailsOnAValidationClassWithAThirdPartyDependency() {
        JavaClasses fixtures =
                new ClassFileImporter().importPackages(ROOT + ".kernel.validation.badfixture", "com.networknt.fixture");
        assertThatThrownBy(() -> VALIDATION_LIBRARY_IS_PURE.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UsesThirdPartyType");
    }

    /**
     * APPLICATION_IS_EDGE: the application ring is a Spring-managed edge. It may depend on itself, its slice's
     * domain, the kernel, {@code java}, JSpecify and the Spring framework, and on nothing else: not on any
     * {@code infra} adapter, not on another slice's domain, not on the deployable composition root. Purity stays
     * with the domain rings (DOMAIN_IS_PURE, KERNEL_IS_PURE), which are gated separately and unchanged.
     */
    private static final ArchRule APPLICATION_IS_EDGE = noClasses()
            .that()
            .resideInAPackage(PACKS_APPLICATION)
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(PACKS_APPLICATION, PACKS_DOMAIN, KERNEL, JAVA, JSPECIFY, FRAMEWORK);

    @Test
    void theApplicationRingIsAnEdge() {
        APPLICATION_IS_EDGE.check(classes);
    }

    @Test
    void theApplicationEdgeRuleFailsOnAnApplicationClassThatReachesStorage() {
        assertBites(APPLICATION_IS_EDGE, ROOT + ".packs.application.badfixture", "ReachesStorage");
    }

    /**
     * PORTS_LIVE_IN_DOMAIN (1/2): a repository or gateway interface resides in a domain ring. (2/2): a class
     * implementing such a port resides in the storage adapter.
     */
    private static final ArchRule PORT_INTERFACES_RESIDE_IN_DOMAIN = classes()
            .that()
            .areInterfaces()
            .and()
            .haveSimpleNameEndingWith("Repository")
            .or()
            .haveSimpleNameEndingWith("Gateway")
            .should()
            .resideInAnyPackage(DOMAIN_RINGS);

    private static final ArchRule PORT_IMPLEMENTATIONS_RESIDE_IN_STORAGE = noClasses()
            .that()
            .resideOutsideOfPackages(PACKS_STORAGE)
            .and()
            .areNotInterfaces()
            .should(new ArchCondition<JavaClass>("implement a repository or gateway port of the domain") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    item.getAllRawInterfaces().stream()
                            .filter(i -> i.getPackageName().startsWith(ROOT)
                                    && i.getPackageName().contains(".domain")
                                    && (i.getSimpleName().endsWith("Repository")
                                            || i.getSimpleName().endsWith("Gateway")))
                            .forEach(i -> events.add(SimpleConditionEvent.satisfied(
                                    item, item.getName() + " implements the port " + i.getName())));
                }
            });

    @Test
    @DisplayName("PORTS_LIVE_IN_DOMAIN")
    void portsLiveInDomain() {
        PORT_INTERFACES_RESIDE_IN_DOMAIN.check(classes);
        PORT_IMPLEMENTATIONS_RESIDE_IN_STORAGE.check(classes);
    }

    @Test
    void thePortPlacementRulesFailOnAMisplacedPortOrImplementation() {
        assertBites(PORT_INTERFACES_RESIDE_IN_DOMAIN, ROOT + ".packs.application.badfixture", "MisplacedRepository");
        assertBites(
                PORT_IMPLEMENTATIONS_RESIDE_IN_STORAGE,
                ROOT + ".packs.infra.api.badfixture",
                "ImplementsPortInEntrypoint");
    }

    /** STORAGE_RETURNS_DOMAIN: the public methods of a storage class implementing a domain port return only domain, kernel or JDK types. */
    private static final ArchRule STORAGE_RETURNS_DOMAIN = classes()
            .that()
            .resideInAPackage(PACKS_STORAGE)
            .and()
            .areNotInterfaces()
            .and(DescribedPredicate.describe(
                    "implement a domain port",
                    (JavaClass c) -> c.getAllRawInterfaces().stream()
                            .anyMatch(i -> i.getPackageName().startsWith(ROOT + ".packs.domain"))))
            .should(new ArchCondition<JavaClass>("return only domain, kernel and java types from public methods") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    item.getMethods().stream()
                            .filter(m -> m.getModifiers().contains(JavaModifier.PUBLIC))
                            .forEach(m -> m.getReturnType().getAllInvolvedRawTypes().stream()
                                    .filter(t -> !t.isPrimitive()
                                            && !t.getPackageName().startsWith("java.")
                                            && !t.getPackageName().startsWith(ROOT + ".kernel")
                                            && !t.getPackageName().startsWith(ROOT + ".packs.domain")
                                            && !t.getPackageName().startsWith("org.jspecify"))
                                    .forEach(t -> events.add(SimpleConditionEvent.violated(
                                            m, m.getFullName() + " returns the non-domain type " + t.getName()))));
                }
            });

    @Test
    @DisplayName("STORAGE_RETURNS_DOMAIN")
    void storageReturnsDomain() {
        STORAGE_RETURNS_DOMAIN.check(classes);
    }

    @Test
    void theStorageRuleFailsOnAPortImplementationReturningATransportType() {
        assertBites(
                STORAGE_RETURNS_DOMAIN,
                ROOT + ".packs.infra.storage.badfixture",
                "StorageLeaksTransportType",
                "org.springframework.http.ResponseEntity");
    }

    /** ENTRYPOINT_OVER_APPLICATION: the inbound adapter goes through the application ring, not the storage adapter. */
    private static final ArchRule ENTRYPOINT_OVER_APPLICATION = noClasses()
            .that()
            .resideInAPackage(PACKS_API)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(PACKS_STORAGE, PACKS_FETCH);

    @Test
    @DisplayName("ENTRYPOINT_OVER_APPLICATION")
    void entrypointOverApplication() {
        ENTRYPOINT_OVER_APPLICATION.check(classes);
    }

    /**
     * FETCH_ADAPTER_STANDS_ALONE (#194): the outbound fetch adapter is reached only through its application-ring port
     * ({@code GuideFetcher}) and composed by the composition root; no other adapter names it, and it names none.
     */
    private static final ArchRule FETCH_ADAPTER_STANDS_ALONE = noClasses()
            .that()
            .resideInAnyPackage(PACKS_API, PACKS_STORAGE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(PACKS_FETCH);

    private static final ArchRule FETCH_ADAPTER_NAMES_NO_OTHER_ADAPTER = noClasses()
            .that()
            .resideInAPackage(PACKS_FETCH)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(PACKS_API, PACKS_STORAGE);

    @Test
    @DisplayName("FETCH_ADAPTER_STANDS_ALONE")
    void fetchAdapterStandsAlone() {
        FETCH_ADAPTER_STANDS_ALONE.check(classes);
        FETCH_ADAPTER_NAMES_NO_OTHER_ADAPTER.check(classes);
    }

    @Test
    void theFetchAdapterRulesFailOnAnAdapterThatNamesTheFetcher() {
        assertBites(ENTRYPOINT_OVER_APPLICATION, ROOT + ".packs.infra.api.badfixture", "EntrypointReachesFetcher");
        assertBites(FETCH_ADAPTER_STANDS_ALONE, ROOT + ".packs.infra.api.badfixture", "EntrypointReachesFetcher");
        assertBites(FETCH_ADAPTER_STANDS_ALONE, ROOT + ".packs.infra.storage.badfixture", "StorageReachesFetcher");
    }

    /**
     * ENTRYPOINT_STAYS_IN_ITS_SLICE (ADR-0037, amendment of 2026-10-27): the pack store binds the kinds of other slices,
     * so {@code packs.infra.storage} may name the profiles and missions domains; the inbound adapter may not.
     */
    private static final ArchRule ENTRYPOINT_STAYS_IN_ITS_SLICE = noClasses()
            .that()
            .resideInAPackage(PACKS_API)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(PROFILES_DOMAIN, MISSIONS_DOMAIN);

    @Test
    @DisplayName("ENTRYPOINT_STAYS_IN_ITS_SLICE")
    void entrypointStaysInItsSlice() {
        ENTRYPOINT_STAYS_IN_ITS_SLICE.check(classes);
    }

    @Test
    void theEntrypointSliceRuleFailsOnAnEntrypointThatNamesAnotherSlicesDomain() {
        assertBites(ENTRYPOINT_STAYS_IN_ITS_SLICE, ROOT + ".packs.infra.api.badfixture", "EntrypointReachesProfiles");
    }

    @Test
    void theEntrypointRuleFailsOnAnEntrypointThatReachesStorage() {
        assertBites(ENTRYPOINT_OVER_APPLICATION, ROOT + ".packs.infra.api.badfixture", "EntrypointReachesStorage");
    }

    /** SLICES_DO_NOT_CROSS: a slice's domain depends on no other slice, only on the kernel and itself. */
    private static ArchRule sliceDomainDoesNotCross(String slice) {
        return noClasses()
                .that()
                .resideInAPackage(ROOT + "." + slice + ".domain..")
                .should()
                .dependOnClassesThat(DescribedPredicate.describe(
                        "belong to a slice other than " + slice, (JavaClass c) -> {
                            String name = c.getPackageName();
                            if (!name.startsWith(ROOT + ".")) {
                                return false;
                            }
                            String rest = name.substring(ROOT.length() + 1);
                            int dot = rest.indexOf('.');
                            String owner = dot < 0 ? rest : rest.substring(0, dot);
                            return !owner.equals(slice)
                                    && !owner.equals("kernel")
                                    && !owner.equals("api")
                                    && !owner.equals("deployable");
                        }));
    }

    @Test
    @DisplayName("SLICES_DO_NOT_CROSS")
    void slicesDoNotCross() {
        SLICES.forEach(slice -> sliceDomainDoesNotCross(slice).check(classes));
    }

    @Test
    void theSliceRuleFailsOnADomainThatNamesAnotherSlice() {
        assertBites(sliceDomainDoesNotCross("packs"), ROOT + ".packs.domain.badfixture", "DomainCrossesSlice");
        assertBites(
                sliceDomainDoesNotCross("profiles"), ROOT + ".profiles.domain.badfixture", "ProfileDomainReachesPacks");
        assertBites(
                sliceDomainDoesNotCross("missions"), ROOT + ".missions.domain.badfixture", "MissionDomainReachesPacks");
    }

    /**
     * ADR-0035 (issue #171): the shared artifact interface lives in the kernel, so a domain type of a slice other than
     * packs can implement it without crossing a slice. The positive control runs the unchanged cross-slice rule on
     * such a type and checks that it is a {@link CharterArtifact}.
     */
    @Test
    void aSecondSliceCanImplementTheSharedArtifactInterface() {
        JavaClasses fixture = new ClassFileImporter().importPackages(ROOT + ".profiles.domain.sharedfixture");
        sliceDomainDoesNotCross("profiles").check(fixture);
        classes()
                .that()
                .resideInAPackage(ROOT + ".profiles.domain.sharedfixture")
                .and()
                .areTopLevelClasses()
                .and()
                .doNotHaveSimpleName("package-info")
                .should()
                .implement(CharterArtifact.class)
                .check(fixture);
    }

    /** The top-level owners that are not concern slices: the shared kernel, the generated contract and the root. */
    private static final java.util.Set<String> NON_SLICE_OWNERS = java.util.Set.of("kernel", "api", "deployable");

    /** The slice owning {@code c} when {@code c} resides in a slice's {@code domain} ring, otherwise {@code null}. */
    private static String domainSliceOwner(JavaClass c) {
        String prefix = ROOT + ".";
        String pkg = c.getPackageName();
        if (!pkg.startsWith(prefix)) {
            return null;
        }
        String rest = pkg.substring(prefix.length());
        int dot = rest.indexOf('.');
        if (dot < 0) {
            return null;
        }
        String owner = rest.substring(0, dot);
        String afterOwner = rest.substring(dot + 1);
        boolean inDomain = afterOwner.equals("domain") || afterOwner.startsWith("domain.");
        return inDomain && !NON_SLICE_OWNERS.contains(owner) ? owner : null;
    }

    /**
     * SLICE_COVERAGE: every vertical concern slice present in the source is registered in {@link #SLICES}. A slice
     * is any top-level concern under {@code ai.speckitty.charter} that owns a {@code domain} ring; the kernel, the
     * generated contract ({@code api}) and the composition root ({@code deployable}) are not slices. {@code SLICES}
     * is otherwise a hand-kept list, so a second slice whose module is added but whose name is never registered
     * would be silently skipped by the slice-scoped rules ({@code SLICES_DO_NOT_CROSS}, and the {@code packs}-pinned
     * {@code APPLICATION_IS_EDGE} / {@code PACKS_DOMAIN_IS_PURE}) and ship ungated on a green build. This rule closes
     * that gap in one direction — a present slice absent from {@code SLICES} fails the build — which makes the list
     * self-checking. Parameterizing the {@code packs}-pinned rules over {@code SLICES} so each registered slice is
     * also purity- and edge-gated is the companion follow-up (#140); until then, registering a slice here is
     * necessary but not on its own sufficient to gate it.
     *
     * <p>Scope: a slice is discovered only by its {@code domain} ring at {@code charter.<owner>.domain}. A would-be
     * slice with no {@code domain} ring yet, or whose domain ring sits at a non-conventional path, is out of scope
     * and this rule does not see it.
     */
    private static final ArchRule EVERY_SLICE_IS_REGISTERED = classes()
            .should(new ArchCondition<JavaClass>("belong to a concern slice registered in SLICES") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    String owner = domainSliceOwner(item);
                    if (owner != null && !SLICES.contains(owner)) {
                        events.add(SimpleConditionEvent.violated(
                                item,
                                item.getName() + " is in the domain of slice '" + owner
                                        + "', which is not registered in SLICES; add it so the slice-scoped rules"
                                        + " gate the slice"));
                    }
                }
            });

    @Test
    @DisplayName("SLICE_COVERAGE")
    void everySliceIsRegistered() {
        EVERY_SLICE_IS_REGISTERED.check(classes);
    }

    // The control reuses the foreign-slice fixture (ai.speckitty.charter.foreign.domain.badfixture): a domain type
    // of a slice that is not in SLICES, which is exactly the ungated-slice shape this rule must bite on.
    @Test
    void theSliceCoverageRuleFailsOnADomainOfAnUnregisteredSlice() {
        assertBites(EVERY_SLICE_IS_REGISTERED, ROOT + ".foreign.domain.badfixture", "ForeignSliceType");
    }

    /** TRANSPORT_STAYS_IN_ADAPTER: the generated contract types are referenced only by the inbound adapter. */
    private static final ArchRule TRANSPORT_STAYS_IN_ADAPTER = noClasses()
            .that()
            .resideOutsideOfPackages(GENERATED_API, PACKS_API)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(GENERATED_API);

    @Test
    @DisplayName("TRANSPORT_STAYS_IN_ADAPTER")
    void transportTypesStayInTheInboundAdapter() {
        TRANSPORT_STAYS_IN_ADAPTER.check(classes);
    }

    @Test
    void theTransportRuleFailsOnAClassOutsideTheInboundAdapterThatNamesADto() {
        assertBites(TRANSPORT_STAYS_IN_ADAPTER, ROOT + ".packs.application.badfixture", "UsesTransportDto");
    }

    /** GENERATED_API_NOT_IN_DOMAIN: no domain ring imports anything from the generated {@code charter-api} package. */
    private static final ArchRule GENERATED_API_NOT_IN_DOMAIN = noClasses()
            .that()
            .resideInAnyPackage(DOMAIN_RINGS)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(GENERATED_API);

    @Test
    @DisplayName("GENERATED_API_NOT_IN_DOMAIN")
    void generatedApiNotInDomain() {
        GENERATED_API_NOT_IN_DOMAIN.check(classes);
    }

    @Test
    void theGeneratedApiRuleFailsOnADomainClassThatNamesAGeneratedType() {
        assertBites(GENERATED_API_NOT_IN_DOMAIN, ROOT + ".packs.domain.badfixture", "DomainUsesGeneratedApi");
    }

    // ---- Code-ownership supplements (kept from the incremental rules) ---------------------------------------

    /**
     * The port signatures of the domain name only domain, kernel, {@code java} and JSpecify types (belt and braces
     * atop DOMAIN_IS_PURE). The known-bad sits in the packs domain itself, so the unchanged rule bites on it.
     */
    private static final ArchRule PORT_SIGNATURES_NAME_ONLY_DOMAIN_TYPES = classes()
            .that()
            .areInterfaces()
            .and()
            .resideInAnyPackage(DOMAIN_RINGS)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(KERNEL, PACKS_DOMAIN, JAVA, JSPECIFY);

    @Test
    void portSignaturesNameOnlyDomainTypes() {
        PORT_SIGNATURES_NAME_ONLY_DOMAIN_TYPES.check(classes);
    }

    @Test
    void thePortSignatureRuleFailsOnAPortThatNamesAnInfrastructureType() {
        JavaClasses sample = new ClassFileImporter().importPackages(ROOT + ".packs.domain.badfixture");
        assertThatThrownBy(() -> PORT_SIGNATURES_NAME_ONLY_DOMAIN_TYPES.check(sample))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("DomainWithInfraImport")
                .hasMessageContaining("DomainRepositoryService");
    }

    /**
     * {@code internal_error} is the refusal of a failure no other code names, and only the inbound adapter, which
     * answers the failure, may name it: a use case or an outbound adapter that refused with it would say, in the
     * domain's own words, that it does not know what went wrong. The enum itself is exempt.
     */
    private static ArchRule onlyTheInboundAdapterNamesInternalError() {
        return noClasses()
                .that()
                .resideOutsideOfPackage(PACKS_API)
                .and()
                .doNotHaveFullyQualifiedName(RefusalCode.class.getName())
                .should()
                .accessField(RefusalCode.class, "INTERNAL_ERROR");
    }

    @Test
    void onlyTheInboundAdapterRefusesWithInternalError() {
        onlyTheInboundAdapterNamesInternalError().check(classes);
    }

    @Test
    void theInternalErrorRuleFailsOnAClassOutsideTheInboundAdapter() {
        JavaClasses outside = new ClassFileImporter().importPackages(ROOT + ".packs.application.badfixture");
        assertThatThrownBy(() -> onlyTheInboundAdapterNamesInternalError().check(outside))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UsesInternalError");
    }

    private static DescribedPredicate<JavaClass> namedWithPrefix(String... prefixes) {
        return DescribedPredicate.describe(
                "named with one of the prefixes " + String.join(", ", prefixes),
                c -> java.util.Arrays.stream(prefixes).anyMatch(c.getName()::startsWith));
    }

    /**
     * Rule A: only the pack policy and the enums themselves name {@code Diagnostic.Code.PACK_LEVEL_MISSING}. The
     * prefix match covers the synthetic {@code $1} and {@code $SwitchMap} classes of a switch over the enum.
     */
    private static ArchRule onlyThePolicyNamesTheMissingDiagnosticRule() {
        return noClasses()
                .that(DescribedPredicate.not(namedWithPrefix(
                        "ai.speckitty.charter.packs.domain.PackPolicy",
                        "ai.speckitty.charter.packs.domain.PackFindingCode",
                        "ai.speckitty.charter.packs.domain.Diagnostic")))
                .should()
                .accessField(Diagnostic.Code.class, "PACK_LEVEL_MISSING");
    }

    /**
     * Rule B: only the pack policy, the enums and the inbound adapter name {@code RefusalCode.PACK_LEVEL_MISMATCH};
     * {@code RefusalHandler$1} reads it with {@code getstatic}.
     */
    private static ArchRule onlyThePolicyNamesTheMismatchRefusalRule() {
        return noClasses()
                .that(DescribedPredicate.not(namedWithPrefix(
                        "ai.speckitty.charter.packs.domain.PackPolicy",
                        "ai.speckitty.charter.packs.domain.PackFindingCode",
                        "ai.speckitty.charter.kernel.domain.RefusalCode",
                        "ai.speckitty.charter.packs.infra.api.")))
                .should()
                .accessField(RefusalCode.class, "PACK_LEVEL_MISMATCH");
    }

    /**
     * Rule C: only the pack policy and the finding enum name the pack-level finding codes, which closes the
     * {@code new PackFinding(...).toDiagnostic()} bypass.
     */
    private static ArchRule onlyThePolicyNamesThePackLevelFindingsRule() {
        return noClasses()
                .that(DescribedPredicate.not(namedWithPrefix(
                        "ai.speckitty.charter.packs.domain.PackPolicy",
                        "ai.speckitty.charter.packs.domain.PackFindingCode")))
                .should()
                .accessField(PackFindingCode.class, "PACK_LEVEL_MISSING")
                .orShould()
                .accessField(PackFindingCode.class, "PACK_LEVEL_MISMATCH");
    }

    @Test
    void onlyThePolicyNamesThePackLevelFindings() {
        onlyThePolicyNamesThePackLevelFindingsRule().check(classes);
    }

    @Test
    void thePackLevelFindingRuleFailsOnAClassOutsideThePolicy() {
        JavaClasses outside =
                new ClassFileImporter().importPackages("ai.speckitty.charter.deployable.packlevelfixture");
        assertThatThrownBy(() -> onlyThePolicyNamesThePackLevelFindingsRule().check(outside))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BuildsFindingDirectly");
    }

    /**
     * RESOLVER_OWNS_NETWORK (Rule D): only the storage adapter depends on {@code com.networknt}, so the validator never leaks into the
     * domain or the other adapters.
     */
    private static ArchRule onlyTheYamlAdapterUsesNetworkntRule() {
        return noClasses()
                .that()
                .resideOutsideOfPackage(PACKS_STORAGE)
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.networknt..");
    }

    @Test
    void onlyThePolicyNamesTheMissingDiagnostic() {
        onlyThePolicyNamesTheMissingDiagnosticRule().check(classes);
    }

    @Test
    void onlyThePolicyNamesTheMismatchRefusal() {
        onlyThePolicyNamesTheMismatchRefusalRule().check(classes);
    }

    @Test
    @DisplayName("RESOLVER_OWNS_NETWORK")
    void onlyTheYamlAdapterUsesNetworknt() {
        onlyTheYamlAdapterUsesNetworkntRule().check(classes);
    }

    // The rules check field access. Enum.valueOf("PACK_LEVEL_MISSING") or values() would get around them; no
    // production code does that today.
    @Test
    void theMissingDiagnosticRuleFailsOnAClassOutsideThePolicy() {
        JavaClasses outside =
                new ClassFileImporter().importPackages("ai.speckitty.charter.deployable.packlevelfixture");
        assertThatThrownBy(() -> onlyThePolicyNamesTheMissingDiagnosticRule().check(outside))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BuildsMissingDiagnostic");
    }

    @Test
    void theMismatchRefusalRuleFailsOnAClassOutsideThePolicy() {
        JavaClasses outside =
                new ClassFileImporter().importPackages("ai.speckitty.charter.deployable.packlevelfixture");
        assertThatThrownBy(() -> onlyThePolicyNamesTheMismatchRefusalRule().check(outside))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RefusesWithMismatch");
    }

    // The stand-in exists only to prove that the networknt rule bites without the real dependency.
    @Test
    void theNetworkntRuleFailsOnAClassOutsideTheYamlAdapter() {
        JavaClasses outside =
                new ClassFileImporter().importPackages(ROOT + ".packs.application.badfixture", "com.networknt.fixture");
        assertThatThrownBy(() -> onlyTheYamlAdapterUsesNetworkntRule().check(outside))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UsesNetworkntStandIn");
    }

    /**
     * NullAway runs only on {@code @NullMarked} code (OnlyNullMarked), so every hand-written package must be
     * marked; only the generated contract package is outside NullAway by construction.
     */
    private static ArchRule everyHandWrittenPackageIsNullMarked() {
        return classes()
                .that()
                .resideOutsideOfPackage(GENERATED_API)
                .should(new ArchCondition<JavaClass>("reside in a package annotated @NullMarked") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!item.getPackage().isAnnotatedWith("org.jspecify.annotations.NullMarked")) {
                            events.add(SimpleConditionEvent.violated(
                                    item, item.getName() + " resides in a package that is not @NullMarked"));
                        }
                    }
                });
    }

    @Test
    void everyHandWrittenMainPackageIsNullMarked() {
        everyHandWrittenPackageIsNullMarked().check(classes);
    }

    @Test
    void thePackageRuleFailsOnAPackageWithoutNullMarked() {
        JavaClasses unmarked =
                new ClassFileImporter().importPackages("ai.speckitty.charter.deployable.unmarkedfixture");
        assertThatThrownBy(() -> everyHandWrittenPackageIsNullMarked().check(unmarked))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "ai.speckitty.charter.deployable.unmarkedfixture.UnmarkedFixture resides in a package that is not"
                                + " @NullMarked");
    }

    /**
     * A test seam never lives in main: no main class depends on a class of a {@code ..bdd..} package, or on a class
     * whose name ends in {@code Drifting} or {@code Scenario}. The seam that makes a read see a file change is test
     * code, so the code it tests cannot have been written to cooperate with it.
     */
    private static ArchRule noTestSeamInMain() {
        return noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..bdd..")
                .orShould()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Drifting")
                .orShould()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Scenario");
    }

    @Test
    void noMainClassDependsOnATestSeam() {
        noTestSeamInMain().check(classes);
    }

    @Test
    void theTestSeamRuleFailsOnAClassThatDependsOnOne() {
        JavaClasses seamUser = new ClassFileImporter().importPackages("ai.speckitty.charter.deployable.seamfixture");
        assertThatThrownBy(() -> noTestSeamInMain().check(seamUser))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UsesSeam")
                .hasMessageContaining("ScenarioPacks");
    }
}
