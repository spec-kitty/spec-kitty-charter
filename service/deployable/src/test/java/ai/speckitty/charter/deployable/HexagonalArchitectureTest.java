package ai.speckitty.charter.deployable;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.ReservedOperation;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
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
        classes = ServiceClasses.main();
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
    /** The adapter that extracts an uploaded pack archive into staging (IC-03, WP13). */
    private static final String PACKS_ARCHIVE = ROOT + ".packs.infra.archive..";

    private static final String PROFILES_DOMAIN = ROOT + ".profiles.domain..";
    /** The missions slice's domain (ADR-0044). */
    private static final String MISSIONS_DOMAIN = ROOT + ".missions.domain..";
    /** The activations slice's domain (ADR-0020), over the packs slice. */
    private static final String ACTIVATIONS_DOMAIN = ROOT + ".activations.domain..";
    /** The activations slice's adapter over the packs slice: the one package of the slice that may name packs. */
    private static final String ACTIVATIONS_PACKS_ADAPTER = ROOT + ".activations.infra.packs..";
    /** The inbound REST adapter of every slice. */
    private static final String SLICE_APIS = ROOT + "..infra.api..";

    private static final String GENERATED_API = ROOT + ".api..";
    /** The shared web transport (problems, refusals, body guard): an adapter ring of every slice's inbound adapter. */
    private static final String WEB = ROOT + ".web..";
    /** The kernel's transport vocabulary (preconditions, entity tags, body limit, the reserved-operation marker). */
    private static final String KERNEL_TRANSPORT = ROOT + ".kernel.transport..";

    private static final String DEPLOYABLE = ROOT + ".deployable..";
    private static final String JAVA = "java..";
    private static final String FRAMEWORK = "org.springframework..";
    private static final String JSPECIFY = "org.jspecify.annotations..";
    /** Every slice domain and the shared kernel (including {@code kernel.validation}). */
    private static final String[] DOMAIN_RINGS = {
        KERNEL, PACKS_DOMAIN, PROFILES_DOMAIN, MISSIONS_DOMAIN, ACTIVATIONS_DOMAIN
    };
    /** The vertical slices; each slice's domain may depend only on itself and the kernel. */
    private static final List<String> SLICES = List.of("packs", "profiles", "missions", "activations");

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
            .resideOutsideOfPackages(PACKS_API, PACKS_STORAGE, PACKS_FETCH, PACKS_ARCHIVE, DEPLOYABLE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(PACKS_API, PACKS_STORAGE, PACKS_FETCH, PACKS_ARCHIVE);

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

    /** The {@code pack-validation} module: every pack check, Spring-free (ADR-0075 decision 3). */
    private static final String PACK_VALIDATION = ROOT + ".packvalidation..";
    /** The {@code pack-check} module: the standalone CLI over {@code pack-validation} (ADR-0075 decision 9). */
    private static final String PACK_CHECK = ROOT + ".packcheck..";

    /** The packs slice's commit stores and write locks, which share {@code packs.infra.storage} with its Spring-free load. */
    private static final java.util.Set<String> COMMIT_STORES_AND_LOCKS = java.util.Set.of(
            ROOT + ".packs.infra.storage.PackCommitStore",
            ROOT + ".packs.infra.storage.CommitStorePackWriteStore",
            ROOT + ".packs.infra.storage.PackWriteLocks",
            ROOT + ".packs.application.RegistryWriteLock");

    /** What neither module may use, directly or through anything it reaches: Spring, HTTP, a commit store or a lock. */
    private static final DescribedPredicate<JavaClass> FRAMEWORK_HTTP_STORE_OR_LOCK = DescribedPredicate.describe(
            "use Spring, Jakarta, HTTP or other network I/O, the generated contract, the web or kernel transport, a commit"
                    + " store or a lock",
            (JavaClass c) -> {
                String pkg = c.getPackageName();
                String name = c.getName();
                return pkg.startsWith("org.springframework")
                        || pkg.startsWith("jakarta")
                        || pkg.startsWith("java.net.http")
                        || pkg.startsWith("com.sun.net.httpserver")
                        || (pkg.equals("java.net")
                                && !name.equals("java.net.URI")
                                && !name.equals("java.net.URISyntaxException"))
                        || pkg.startsWith("java.util.concurrent.locks")
                        || name.equals("java.nio.channels.FileLock")
                        || pkg.equals(ROOT + ".api")
                        || pkg.startsWith(ROOT + ".api.")
                        || pkg.equals(ROOT + ".web")
                        || pkg.startsWith(ROOT + ".web.")
                        || pkg.startsWith(ROOT + ".kernel.transport")
                        || COMMIT_STORES_AND_LOCKS.contains(name);
            });

    /**
     * What the modules may not name directly, beyond {@link #FRAMEWORK_HTTP_STORE_OR_LOCK}: every ring of the packs slice
     * but its domain (which holds the {@code PackInspector} port of the load, ADR-0075 as amended 2026-11-13), the
     * activations slice, every other {@code application} or {@code infra} ring and the composition root. The modules'
     * own packages have no rings; a composition root plugs the load's adapter in.
     */
    private static final DescribedPredicate<JavaClass> OUTSIDE_THE_DOMAIN = DescribedPredicate.describe(
            "belong to a charter package other than the modules' own, kernel, profiles.domain, missions.domain and"
                    + " packs.domain",
            (JavaClass c) -> {
                String pkg = c.getPackageName();
                return pkg.startsWith(ROOT + ".")
                        && Stream.of(
                                        "packvalidation",
                                        "packcheck",
                                        "kernel",
                                        "profiles.domain",
                                        "missions.domain",
                                        "packs.domain")
                                .map(allowed -> ROOT + "." + allowed)
                                .noneMatch(allowed -> pkg.equals(allowed) || pkg.startsWith(allowed + "."));
            });

    /**
     * PACK_VALIDATION_IS_FRAMEWORK_FREE (ADR-0075 decision 3, as amended 2026-11-13): neither {@code pack-validation} nor
     * {@code pack-check} uses Spring, HTTP or other network I/O ({@code java.net} but {@code URI} and
     * {@code URISyntaxException}, the JDK's HTTP client and server, Jakarta, the generated contract, the web transport, the
     * kernel's transport vocabulary), a commit store or a lock, <em>directly or through any charter class it reaches</em>.
     * {@code pack-validation} reads a pack through the packs slice's {@code PackInspector} port and its domain, so the
     * direct half allows {@code packs.domain} and no other ring of the slice. The transitive half walks every charter class
     * the module reaches and fails on any that touches the refused set: it holds when a composition root
     * ({@code pack-check}'s main, #478) plugs in the file-system adapter, which shares its package with the slice's Spring
     * beans, commit stores and locks. A class of the JDK or a library is not walked into, and nor is a port's
     * implementation: the walk follows the classes a class names, so the inspector the composition root plugs in is walked
     * from its own root ({@link #THE_PACKS_LOAD_REACHES_NO_FRAMEWORK}). Until #478 adds its main, {@code pack-check} holds
     * only its {@code package-info}, so both halves pass over it on that alone. #478 also lets that main, the module's
     * composition root, name the file-system inspector, in this rule and in {@code ONION_DEPENDS_INWARD}. A lock is caught
     * by its type ({@code java.util.concurrent.locks}, {@code FileLock}); a {@code FileChannel.lock()} whose result is
     * discarded, a {@code Semaphore} or a {@code synchronized} block is not, and the commit stores and locks are a list of
     * names ({@link #COMMIT_STORES_AND_LOCKS}): review checks a new one.
     *
     * <p>Each half runs once per module's package and fails on an empty selection, so a module missing from this test's
     * classpath fails it rather than passing unchecked.
     */
    private static ArchRule packModuleIsFrameworkFree(String modulePackage) {
        return noClasses()
                .that()
                .resideInAPackage(modulePackage)
                .should()
                .dependOnClassesThat(FRAMEWORK_HTTP_STORE_OR_LOCK.or(OUTSIDE_THE_DOMAIN))
                .as("PACK_VALIDATION_IS_FRAMEWORK_FREE (" + modulePackage
                        + "), direct: only the packs domain, no Spring," + " HTTP, commit store or lock");
    }

    /**
     * The walk of the transitive half: from a class, through every charter class it reaches, to a class in
     * {@link #FRAMEWORK_HTTP_STORE_OR_LOCK}. A class outside {@code ai.speckitty.charter} is checked but never walked into.
     */
    private static final ArchCondition<JavaClass> REACHES_NO_FRAMEWORK =
            new ArchCondition<JavaClass>("reach no charter class that uses Spring, HTTP, a commit store or a lock") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    java.util.Map<JavaClass, JavaClass> via = new java.util.HashMap<>();
                    java.util.ArrayDeque<JavaClass> queue = new java.util.ArrayDeque<>(List.of(item));
                    via.put(item, item);
                    java.util.Set<JavaClass> reported = new java.util.HashSet<>();
                    while (!queue.isEmpty()) {
                        JavaClass current = queue.removeFirst();
                        for (var dependency : current.getDirectDependenciesFromSelf()) {
                            JavaClass target = dependency.getTargetClass().getBaseComponentType();
                            if (FRAMEWORK_HTTP_STORE_OR_LOCK.test(target) && reported.add(target)) {
                                events.add(SimpleConditionEvent.violated(
                                        item,
                                        item.getName() + " reaches " + target.getName() + " through "
                                                + path(via, current)));
                            } else if (!FRAMEWORK_HTTP_STORE_OR_LOCK.test(target)
                                    && target.getPackageName().startsWith(ROOT + ".")
                                    && !via.containsKey(target)) {
                                via.put(target, current);
                                queue.addLast(target);
                            }
                        }
                    }
                }
            };

    /** The transitive half of {@link #packModuleIsFrameworkFree}: no charter class the module reaches is refused. */
    private static ArchRule packModuleReachesNoFramework(String modulePackage) {
        return classes()
                .that()
                .resideInAPackage(modulePackage)
                .should(REACHES_NO_FRAMEWORK)
                .as("PACK_VALIDATION_IS_FRAMEWORK_FREE (" + modulePackage + "), transitive: nothing reached uses"
                        + " Spring, HTTP, a commit store or a lock");
    }

    /**
     * The transitive half over the load a composition root plugs into pack-validation: the packs slice's file-system
     * inspector, which {@code pack-check} runs without Spring, reaches no charter class that uses Spring, HTTP, a commit
     * store or a lock (ADR-0075 as amended 2026-11-13, #553).
     */
    private static final ArchRule THE_PACKS_LOAD_REACHES_NO_FRAMEWORK = classes()
            .that()
            .haveFullyQualifiedName(ROOT + ".packs.infra.storage.FileSystemPackInspector")
            .should(REACHES_NO_FRAMEWORK)
            .as("PACK_VALIDATION_IS_FRAMEWORK_FREE (the packs load), transitive: nothing reached uses Spring, HTTP, a"
                    + " commit store or a lock");

    /** The chain of classes from the checked class to {@code last}, by simple name. */
    private static String path(java.util.Map<JavaClass, JavaClass> via, JavaClass last) {
        java.util.Deque<String> names = new java.util.ArrayDeque<>();
        JavaClass at = last;
        while (via.get(at) != at) {
            names.addFirst(at.getSimpleName());
            at = via.get(at);
        }
        names.addFirst(at.getSimpleName());
        return String.join(" -> ", names);
    }

    @Test
    @DisplayName("PACK_VALIDATION_IS_FRAMEWORK_FREE")
    void packValidationIsFrameworkFree() {
        for (String module : List.of(PACK_VALIDATION, PACK_CHECK)) {
            packModuleIsFrameworkFree(module).check(classes);
            packModuleReachesNoFramework(module).check(classes);
        }
        THE_PACKS_LOAD_REACHES_NO_FRAMEWORK.check(classes);
    }

    @Test
    void thePackValidationRuleFailsOnAPlantedSpringHttpCommitStoreOrLockDependency() {
        assertBites(
                packModuleIsFrameworkFree(PACK_VALIDATION),
                ROOT + ".packvalidation.badfixture",
                "ValidationUsesSpring",
                "org.springframework.stereotype.Component",
                "ValidationSpeaksHttp",
                "java.net.http.HttpClient",
                "ValidationOpensAUrl",
                "java.net.HttpURLConnection",
                "ValidationReachesCommitStore",
                "CommitStorePackWriteStore",
                "ValidationTakesALock",
                "java.util.concurrent.locks.ReentrantLock",
                "java.nio.channels.FileLock",
                "RegistryWriteLock");
        // Two hops through a class the direct half allows: the fixture names a packs.domain class only, and that class
        // carries a Spring annotation. The direct half passes it; the transitive half must not.
        JavaClasses twoHops = new ClassFileImporter()
                .importClasses(
                        ai.speckitty.charter.packvalidation.badfixture.ValidationReachesSpringThroughTheDomain.class,
                        ai.speckitty.charter.packs.domain.badfixture.DomainUsesSpring.class);
        packModuleIsFrameworkFree(PACK_VALIDATION).check(twoHops);
        assertThatThrownBy(() -> packModuleReachesNoFramework(PACK_VALIDATION).check(twoHops))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ValidationReachesSpringThroughTheDomain reaches org.springframework")
                .hasMessageContaining("through ValidationReachesSpringThroughTheDomain -> DomainUsesSpring");
        assertBites(
                packModuleIsFrameworkFree(PACK_CHECK),
                ROOT + ".packcheck.badfixture",
                "CheckUsesSpring",
                "org.springframework.stereotype.Component");
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
     * A reserved controller (ADR-0020 D8, #125) declares operations and answers 501: it reads and stores nothing, so it
     * depends on no application, domain or storage type, nor on another class of its adapter: only on the generated
     * transport types, its kernel marker and the framework (its own {@code ResponseStatusException}); the interceptor
     * that answers for it lives in the composition root, which it never names. When an operation is built, its
     * controller leaves the annotation and this rule behind.
     */
    private static final ArchRule RESERVED_CONTROLLER_READS_NOTHING = noClasses()
            .that()
            .areAnnotatedWith(ReservedOperation.class)
            .should()
            .dependOnClassesThat(DescribedPredicate.not(
                            JavaClass.Predicates.resideInAnyPackage(GENERATED_API, JAVA, JSPECIFY, FRAMEWORK))
                    .and(DescribedPredicate.not(JavaClass.Predicates.belongToAnyOf(ReservedOperation.class))));

    // No production controller is reserved whole since the activation set marks each operation; the type-level
    // rule still gates one that is, so it may select nothing here. Its control below runs it unchanged.
    @Test
    void aReservedControllerReadsAndStoresNothing() {
        RESERVED_CONTROLLER_READS_NOTHING.allowEmptyShould(true).check(classes);
    }

    @Test
    void theReservedControllerRuleFailsOnOneThatReachesAUseCase() {
        assertBites(
                RESERVED_CONTROLLER_READS_NOTHING, ROOT + ".packs.infra.api.badfixture", "ReservedReachesApplication");
    }

    /** What a reserved operation never reaches: a use case, a domain type or a store. */
    private static final DescribedPredicate<JavaClass> USE_CASE_DOMAIN_OR_STORE =
            JavaClass.Predicates.resideInAnyPackage("..application..", "..domain..", "..infra.storage..");

    /** The types an access reaches: its target's owner and, for a field, the field's own type. */
    private static Stream<JavaClass> reachedTypes(JavaAccess<?> access) {
        Stream<JavaClass> owner = Stream.of(access.getTargetOwner());
        return access instanceof JavaFieldAccess field
                ? Stream.concat(owner, Stream.of(field.getTarget().getRawType()))
                : owner;
    }

    /**
     * RESERVED_METHOD_READS_NOTHING (ADR-0020 D8): an operation is reserved one method at a time, so a method marked
     * {@link ReservedOperation} calls no application, domain or storage method, touches no field of such a type, and
     * neither takes nor returns one. Its controller may serve built operations beside it.
     */
    private static final ArchRule RESERVED_METHOD_READS_NOTHING = methods()
            .that()
            .areAnnotatedWith(ReservedOperation.class)
            .should(new ArchCondition<JavaMethod>("reach no application, domain or storage type") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    Stream.of(
                                    method.getAccessesFromSelf().stream().flatMap(a -> reachedTypes(a)),
                                    Stream.of(method.getRawReturnType()),
                                    method.getRawParameterTypes().stream())
                            .flatMap(s -> s)
                            .filter(USE_CASE_DOMAIN_OR_STORE)
                            .distinct()
                            .forEach(t -> events.add(SimpleConditionEvent.violated(
                                    method, method.getFullName() + " reaches " + t.getName())));
                }
            });

    @Test
    @DisplayName("RESERVED_METHOD_READS_NOTHING")
    void aReservedOperationReadsAndStoresNothing() {
        // PR C built the last two reserved operations (IC-11), so the rule may select no method here; it still gates
        // one that is marked, and its control below runs it unchanged on a marked method that reaches a use case.
        RESERVED_METHOD_READS_NOTHING.allowEmptyShould(true).check(classes);
    }

    @Test
    void theReservedMethodRuleFailsOnAMarkedMethodThatReachesAUseCase() {
        assertBites(
                RESERVED_METHOD_READS_NOTHING,
                ROOT + ".activations.infra.api.badfixture",
                "ReservedMethodReachesApplication.reads",
                "AvailablePacks");
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
                    // Pinned to the packs slice's ports; every slice, packs included, is held to the same rule by
                    // portImplementationsResideInStorage(slice).
                    item.getAllRawInterfaces().stream()
                            .filter(i -> isPortOf("packs", i))
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
            .resideInAnyPackage(PACKS_STORAGE, PACKS_FETCH, PACKS_ARCHIVE);

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
     * ARCHIVE_ADAPTER_STANDS_ALONE (IC-03): the archive extractor is reached only through its application-ring port
     * ({@code PackArchiveReader}) and composed by the composition root; no other adapter names it, and it names none.
     */
    private static final ArchRule ARCHIVE_ADAPTER_STANDS_ALONE = noClasses()
            .that()
            .resideInAnyPackage(PACKS_API, PACKS_STORAGE, PACKS_FETCH)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(PACKS_ARCHIVE);

    private static final ArchRule ARCHIVE_ADAPTER_NAMES_NO_OTHER_ADAPTER = noClasses()
            .that()
            .resideInAPackage(PACKS_ARCHIVE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(PACKS_API, PACKS_STORAGE, PACKS_FETCH);

    @Test
    @DisplayName("ARCHIVE_ADAPTER_STANDS_ALONE")
    void archiveAdapterStandsAlone() {
        ARCHIVE_ADAPTER_STANDS_ALONE.check(classes);
        ARCHIVE_ADAPTER_NAMES_NO_OTHER_ADAPTER.check(classes);
    }

    @Test
    void theArchiveAdapterRulesFailOnAnAdapterThatNamesTheExtractor() {
        assertBites(ARCHIVE_ADAPTER_STANDS_ALONE, ROOT + ".packs.infra.api.badfixture", "EntrypointReachesArchive");
        assertBites(ARCHIVE_ADAPTER_STANDS_ALONE, ROOT + ".packs.infra.fetch.badfixture", "FetcherReachesArchive");
        assertBites(
                ARCHIVE_ADAPTER_NAMES_NO_OTHER_ADAPTER,
                ROOT + ".packs.infra.archive.badfixture",
                "ArchiveReachesFetcher");
        assertBites(ENTRYPOINT_OVER_APPLICATION, ROOT + ".packs.infra.api.badfixture", "EntrypointReachesArchive");
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
        assertBites(
                sliceDomainDoesNotCross("activations"),
                ROOT + ".activations.domain.badfixture",
                "ActivationDomainNamesPacks");
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

    /**
     * The top-level owners that are not concern slices: the shared kernel, the generated contract, the shared web
     * transport and the root.
     */
    private static final java.util.Set<String> NON_SLICE_OWNERS =
            java.util.Set.of("kernel", "api", "web", "deployable");

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
     * self-checking. The per-slice rule factories ({@code domainIsPure}, {@code applicationIsEdge} and the others of
     * SLICE_RINGS, #140) run over {@code SLICES}, so a registered slice is purity- and edge-gated too.
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

    /**
     * TRANSPORT_STAYS_IN_ADAPTER: the generated contract types are referenced only by an inbound adapter, of any slice
     * ({@code <slice>.infra.api}), and by the shared web transport those adapters use ({@code web}). The composition
     * root names none: the activation set's request-body advice and problem mapping live in its own adapter (#140).
     */
    private static final ArchRule TRANSPORT_STAYS_IN_ADAPTER = noClasses()
            .that()
            .resideOutsideOfPackages(GENERATED_API, SLICE_APIS, WEB)
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
        assertBites(
                TRANSPORT_STAYS_IN_ADAPTER,
                ROOT + ".activations.application.badfixture",
                "ApplicationNamesGeneratedApi");
        assertBites(TRANSPORT_STAYS_IN_ADAPTER, ROOT + ".deployable.transportfixture", "RootNamesGeneratedDto");
    }

    /**
     * WEB_IS_AN_ADAPTER_RING: the shared web transport is an adapter ring, used by the slices' inbound adapters; no
     * domain or application ring depends on it.
     */
    private static final ArchRule WEB_IS_AN_ADAPTER_RING = noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(WEB);

    /** WEB_NAMES_NO_SLICE: the shared web transport names no slice; each slice's adapter names it. */
    private static final ArchRule WEB_NAMES_NO_SLICE = noClasses()
            .that()
            .resideInAPackage(WEB)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    ROOT + ".packs..",
                    ROOT + ".activations..",
                    ROOT + ".profiles..",
                    ROOT + ".missions..",
                    ROOT + ".artifacts..");

    @Test
    @DisplayName("WEB_IS_AN_ADAPTER_RING")
    void theWebTransportIsAnAdapterRing() {
        WEB_IS_AN_ADAPTER_RING.check(classes);
        WEB_NAMES_NO_SLICE.check(classes);
    }

    @Test
    void theWebRingRulesFailOnTheKnownBads() {
        assertBites(
                WEB_IS_AN_ADAPTER_RING, ROOT + ".activations.application.badfixture", "ApplicationNamesWebTransport");
        assertBites(WEB_NAMES_NO_SLICE, ROOT + ".web.badfixture", "WebNamesASlice", "WebNamesTheArtifactsEdge");
    }

    /**
     * DOMAIN_NAMES_NO_TRANSPORT: no domain ring, the kernel's {@code kernel.domain} included, depends on the kernel's
     * transport vocabulary ({@code kernel.transport}); a transport question is the application ring's or an adapter's.
     */
    private static final ArchRule DOMAIN_NAMES_NO_TRANSPORT = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(KERNEL_TRANSPORT);

    @Test
    @DisplayName("DOMAIN_NAMES_NO_TRANSPORT")
    void noDomainNamesTheKernelTransport() {
        DOMAIN_NAMES_NO_TRANSPORT.check(classes);
    }

    @Test
    void theDomainTransportRuleFailsOnADomainClassThatNamesIt() {
        assertBites(
                DOMAIN_NAMES_NO_TRANSPORT,
                ROOT + ".activations.domain.badfixture",
                "DomainNamesKernelTransport",
                "EntityTags");
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

    // ---- Per-slice rings (#140) -------------------------------------------------------------------------------
    // The packs-pinned rules above stay as they are; each factory below holds every registered slice to the same ring,
    // and its packs instance runs beside the old constant. A ring a slice lacks selects nothing (allowEmptyShould).

    /** The packages of one ring of a slice, for example {@code ring("activations", "infra.api")}. */
    private static String ring(String slice, String ring) {
        return ROOT + "." + slice + "." + ring + "..";
    }

    /** DOMAIN_IS_PURE per slice: a slice's domain depends only on itself, the kernel, {@code java} and JSpecify. */
    private static ArchRule domainIsPure(String slice) {
        return noClasses()
                .that()
                .resideInAPackage(ring(slice, "domain"))
                .should()
                .dependOnClassesThat()
                .resideOutsideOfPackages(ring(slice, "domain"), KERNEL, JAVA, JSPECIFY)
                .allowEmptyShould(true);
    }

    /** APPLICATION_IS_EDGE per slice: itself, its domain, the kernel, {@code java}, JSpecify and Spring only. */
    private static ArchRule applicationIsEdge(String slice) {
        return noClasses()
                .that()
                .resideInAPackage(ring(slice, "application"))
                .should()
                .dependOnClassesThat()
                .resideOutsideOfPackages(
                        ring(slice, "application"), ring(slice, "domain"), KERNEL, JAVA, JSPECIFY, FRAMEWORK)
                .allowEmptyShould(true);
    }

    /** ENTRYPOINT_OVER_APPLICATION per slice: the inbound adapter names no other adapter of its slice. */
    private static ArchRule entrypointOverApplication(String slice) {
        return noClasses()
                .that()
                .resideInAPackage(ring(slice, "infra.api"))
                .should()
                .dependOnClassesThat(JavaClass.Predicates.resideInAPackage(ring(slice, "infra"))
                        .and(JavaClass.Predicates.resideOutsideOfPackage(ring(slice, "infra.api"))))
                .allowEmptyShould(true);
    }

    /** ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS per slice: only the slice's adapters and the root name them. */
    private static ArchRule onlyDeployableComposesAdapters(String slice) {
        return noClasses()
                .that()
                .resideOutsideOfPackages(ring(slice, "infra"), DEPLOYABLE)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(ring(slice, "infra"))
                .allowEmptyShould(true);
    }

    /** A repository or gateway port of the slice's domain, by its name. */
    private static boolean isPortOf(String slice, JavaClass type) {
        return type.getPackageName().startsWith(ROOT + "." + slice + ".domain")
                && (type.getSimpleName().endsWith("Repository")
                        || type.getSimpleName().endsWith("Gateway"));
    }

    /** PORTS_LIVE_IN_DOMAIN (1/2) per slice: a repository or gateway interface of the slice is in its domain. */
    private static ArchRule portInterfacesResideInDomain(String slice) {
        return classes()
                .that()
                .resideInAPackage(ROOT + "." + slice + "..")
                .and()
                .areInterfaces()
                .and(DescribedPredicate.describe(
                        "are named as a repository or gateway",
                        (JavaClass c) -> c.getSimpleName().endsWith("Repository")
                                || c.getSimpleName().endsWith("Gateway")))
                .should()
                .resideInAPackage(ring(slice, "domain"))
                .allowEmptyShould(true);
    }

    /** PORTS_LIVE_IN_DOMAIN (2/2) per slice: a class implementing a port of the slice's domain is in its storage. */
    private static ArchRule portImplementationsResideInStorage(String slice) {
        return noClasses()
                .that()
                .resideOutsideOfPackage(ring(slice, "infra.storage"))
                .and()
                .areNotInterfaces()
                .should(new ArchCondition<JavaClass>("implement a repository or gateway port of " + slice) {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        item.getAllRawInterfaces().stream()
                                .filter(i -> isPortOf(slice, i))
                                .forEach(i -> events.add(SimpleConditionEvent.satisfied(
                                        item, item.getName() + " implements the port " + i.getName())));
                    }
                })
                .allowEmptyShould(true);
    }

    /** A type a storage method of the slice may return: a JDK, kernel, JSpecify or own-domain type. */
    private static boolean isDomainReturnable(String slice, JavaClass type) {
        String name = type.getPackageName();
        return type.isPrimitive()
                || name.startsWith("java.")
                || name.startsWith(ROOT + ".kernel")
                || name.startsWith(ROOT + "." + slice + ".domain")
                || name.startsWith("org.jspecify");
    }

    /** STORAGE_RETURNS_DOMAIN per slice: a storage class implementing a domain port returns only domain types. */
    private static ArchRule storageReturnsDomain(String slice) {
        return classes()
                .that()
                .resideInAPackage(ring(slice, "infra.storage"))
                .and()
                .areNotInterfaces()
                .and(DescribedPredicate.describe(
                        "implement a domain port of " + slice,
                        (JavaClass c) -> c.getAllRawInterfaces().stream()
                                .anyMatch(i -> i.getPackageName().startsWith(ROOT + "." + slice + ".domain"))))
                .should(new ArchCondition<JavaClass>("return only domain, kernel and java types from public methods") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        item.getMethods().stream()
                                .filter(m -> m.getModifiers().contains(JavaModifier.PUBLIC))
                                .forEach(m -> m.getReturnType().getAllInvolvedRawTypes().stream()
                                        .filter(t -> !isDomainReturnable(slice, t))
                                        .forEach(t -> events.add(SimpleConditionEvent.violated(
                                                m, m.getFullName() + " returns the non-domain type " + t.getName()))));
                    }
                })
                .allowEmptyShould(true);
    }

    @Test
    @DisplayName("SLICE_RINGS")
    void everySliceIsHeldToTheSameRings() {
        for (String slice : SLICES) {
            domainIsPure(slice).check(classes);
            applicationIsEdge(slice).check(classes);
            entrypointOverApplication(slice).check(classes);
            onlyDeployableComposesAdapters(slice).check(classes);
            portInterfacesResideInDomain(slice).check(classes);
            portImplementationsResideInStorage(slice).check(classes);
            storageReturnsDomain(slice).check(classes);
        }
    }

    @Test
    void thePerSliceDomainRuleFailsOnEachSlicesKnownBad() {
        String packs = ROOT + ".packs.domain.badfixture";
        assertBites(domainIsPure("packs"), packs, "DomainUsesSpring", "DomainReachesOutward");
        assertBites(domainIsPure("profiles"), ROOT + ".profiles.domain.badfixture", "ProfileDomainReachesPacks");
        assertBites(domainIsPure("missions"), ROOT + ".missions.domain.badfixture", "MissionDomainReachesPacks");
        String activations = ROOT + ".activations.domain.badfixture";
        assertBites(
                domainIsPure("activations"),
                activations,
                "ActivationDomainReachesApplication",
                "ActivationDomainNamesPacks");
        assertBites(ONION_DOMAIN_DEPENDS_ON_NOTHING_OUTWARD, activations, "ActivationDomainReachesApplication");
    }

    @Test
    void thePerSliceApplicationRuleFailsOnEachSlicesKnownBad() {
        assertBites(applicationIsEdge("packs"), ROOT + ".packs.application.badfixture", "ReachesStorage");
        assertBites(
                applicationIsEdge("activations"),
                ROOT + ".activations.application.badfixture",
                "ApplicationNamesPacks",
                "ApplicationNamesGeneratedApi");
    }

    @Test
    void thePerSliceAdapterRulesFailOnTheKnownBads() {
        String api = ROOT + ".packs.infra.api.badfixture";
        assertBites(entrypointOverApplication("packs"), api, "EntrypointReachesStorage", "EntrypointReachesFetcher");
        assertBites(onlyDeployableComposesAdapters("packs"), ROOT + ".packs.application.badfixture", "ReachesStorage");
        assertBites(
                ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS,
                ROOT + ".activations.infra.packs.badfixture",
                "AdapterNamesPacksStorage");
    }

    @Test
    void thePerSlicePortRulesFailOnAMisplacedPortOrImplementation() {
        assertBites(
                portInterfacesResideInDomain("packs"), ROOT + ".packs.application.badfixture", "MisplacedRepository");
        assertBites(
                portImplementationsResideInStorage("packs"),
                ROOT + ".packs.infra.api.badfixture",
                "ImplementsPortInEntrypoint");
        assertBites(
                storageReturnsDomain("packs"),
                ROOT + ".packs.infra.storage.badfixture",
                "StorageLeaksTransportType",
                "org.springframework.http.ResponseEntity");
    }

    /**
     * A SLICE OVER ANOTHER SLICE (1/2): the activations slice names the packs slice from one adapter package only,
     * {@code activations.infra.packs}, which implements the slice's own {@code AvailablePacks} port.
     */
    private static final ArchRule ACTIVATIONS_NAME_PACKS_ONLY_FROM_ADAPTER = noClasses()
            .that()
            .resideInAPackage(ROOT + ".activations..")
            .and()
            .resideOutsideOfPackage(ACTIVATIONS_PACKS_ADAPTER)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(ROOT + ".packs..");

    /**
     * A SLICE OVER ANOTHER SLICE (2/2): that adapter names only the packs application ring and the packs domain values
     * its answers expose ({@code ClosureView}), never a packs adapter.
     */
    private static final ArchRule ACTIVATIONS_ADAPTER_NAMES_PACKS_APPLICATION = noClasses()
            .that()
            .resideInAPackage(ACTIVATIONS_PACKS_ADAPTER)
            .should()
            .dependOnClassesThat(JavaClass.Predicates.resideInAPackage(ROOT + ".packs..")
                    .and(JavaClass.Predicates.resideOutsideOfPackages(PACKS_APPLICATION, PACKS_DOMAIN)));

    @Test
    @DisplayName("A_SLICE_OVER_ANOTHER_SLICE")
    void theActivationsSliceNamesPacksOnlyThroughItsAdapter() {
        ACTIVATIONS_NAME_PACKS_ONLY_FROM_ADAPTER.check(classes);
        ACTIVATIONS_ADAPTER_NAMES_PACKS_APPLICATION.check(classes);
    }

    @Test
    void theCrossSliceRulesFailOnTheKnownBads() {
        assertBites(
                ACTIVATIONS_NAME_PACKS_ONLY_FROM_ADAPTER,
                ROOT + ".activations.application.badfixture",
                "ApplicationNamesPacks");
        assertBites(
                ACTIVATIONS_NAME_PACKS_ONLY_FROM_ADAPTER,
                ROOT + ".activations.infra.api.badfixture",
                "ApiNamesPacksApplication");
        assertBites(
                ACTIVATIONS_NAME_PACKS_ONLY_FROM_ADAPTER,
                ROOT + ".activations.domain.badfixture",
                "ActivationDomainNamesPacks");
        assertBites(
                ACTIVATIONS_ADAPTER_NAMES_PACKS_APPLICATION,
                ROOT + ".activations.infra.packs.badfixture",
                "AdapterNamesPacksStorage");
    }

    // ---- The profiles slice over the packs and activations slices (ADR-0037, amendment of 2026-11-09) ----------
    // The profiles slice spans two modules: its domain (:profiles) and its edge rings (:profiles-edge). The edge asks
    // the packs slice and the activations slice, each from one adapter package of its own, and only their application
    // and domain rings; and neither of those slices names the profiles edge back.

    private static final String PACKS = ROOT + ".packs..";
    private static final String ACTIVATIONS = ROOT + ".activations..";
    private static final String PROFILES = ROOT + ".profiles..";
    /** The one profiles package that may name the packs slice. */
    private static final String PROFILES_PACKS_ADAPTER = ROOT + ".profiles.infra.packs..";
    /** The one profiles package that may name the activations slice. */
    private static final String PROFILES_ACTIVATIONS_ADAPTER = ROOT + ".profiles.infra.activations..";

    private static final String ACTIVATIONS_APPLICATION = ROOT + ".activations.application..";
    private static final String ACTIVATIONS_DOMAIN_RING = ROOT + ".activations.domain..";
    private static final String PROFILES_APPLICATION = ROOT + ".profiles.application..";
    private static final String PROFILES_INFRA = ROOT + ".profiles.infra..";

    /** The profiles edge rings, each of which must be on the classpath for the rules below to check anything. */
    private static final List<String> PROFILES_EDGE_RINGS = List.of(
            "ai.speckitty.charter.profiles.application",
            "ai.speckitty.charter.profiles.infra.api",
            "ai.speckitty.charter.profiles.infra.packs",
            "ai.speckitty.charter.profiles.infra.activations");

    /** A PROFILES OVER PACKS (1/2): no profiles class names the packs slice, but those of its packs adapter. */
    private static final ArchRule PROFILES_NAME_PACKS_ONLY_FROM_ADAPTER = noClasses()
            .that()
            .resideInAPackage(PROFILES)
            .and()
            .resideOutsideOfPackage(PROFILES_PACKS_ADAPTER)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(PACKS);

    /**
     * A PROFILES OVER PACKS (2/2): that adapter names only the packs application ring and the packs domain values its
     * answers carry, never a packs adapter: no {@code Hypermedia}, no {@code ArtifactDtos}, no {@code CharterHeaders}.
     */
    private static final ArchRule PROFILES_PACKS_ADAPTER_NAMES_PACKS_APPLICATION = noClasses()
            .that()
            .resideInAPackage(PROFILES_PACKS_ADAPTER)
            .should()
            .dependOnClassesThat(JavaClass.Predicates.resideInAPackage(PACKS)
                    .and(JavaClass.Predicates.resideOutsideOfPackages(PACKS_APPLICATION, PACKS_DOMAIN)));

    /** A PROFILES OVER ACTIVATIONS (1/2): no profiles class names the activations slice, but those of its adapter. */
    private static final ArchRule PROFILES_NAME_ACTIVATIONS_ONLY_FROM_ADAPTER = noClasses()
            .that()
            .resideInAPackage(PROFILES)
            .and()
            .resideOutsideOfPackage(PROFILES_ACTIVATIONS_ADAPTER)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(ACTIVATIONS);

    /** A PROFILES OVER ACTIVATIONS (2/2): that adapter names only the activations application and domain rings. */
    private static final ArchRule PROFILES_ACTIVATIONS_ADAPTER_NAMES_ACTIVATIONS_APPLICATION = noClasses()
            .that()
            .resideInAPackage(PROFILES_ACTIVATIONS_ADAPTER)
            .should()
            .dependOnClassesThat(JavaClass.Predicates.resideInAPackage(ACTIVATIONS)
                    .and(JavaClass.Predicates.resideOutsideOfPackages(
                            ACTIVATIONS_APPLICATION, ACTIVATIONS_DOMAIN_RING)));

    /**
     * THE EDGE IS NOT NAMED BACK: the packs and activations slices never name the profiles edge rings
     * ({@code profiles.application}, {@code profiles.infra}). They may name the profiles domain: the pack store binds it.
     */
    private static final ArchRule NO_PACKS_OR_ACTIVATIONS_CLASS_NAMES_THE_PROFILES_EDGE = noClasses()
            .that()
            .resideInAnyPackage(PACKS, ACTIVATIONS)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(PROFILES_APPLICATION, PROFILES_INFRA);

    @Test
    @DisplayName("PROFILES_OVER_PACKS_AND_ACTIVATIONS")
    void theProfilesSliceNamesPacksAndActivationsOnlyThroughItsAdapters() {
        PROFILES_NAME_PACKS_ONLY_FROM_ADAPTER.check(classes);
        PROFILES_PACKS_ADAPTER_NAMES_PACKS_APPLICATION.check(classes);
        PROFILES_NAME_ACTIVATIONS_ONLY_FROM_ADAPTER.check(classes);
        PROFILES_ACTIVATIONS_ADAPTER_NAMES_ACTIVATIONS_APPLICATION.check(classes);
        NO_PACKS_OR_ACTIVATIONS_CLASS_NAMES_THE_PROFILES_EDGE.check(classes);
    }

    /**
     * The slice rules use {@code allowEmptyShould}, so a module missing from the classpath passes with nothing checked.
     * This test fails instead: every profiles edge ring holds a class, which means {@code :deployable} depends on
     * {@code :profiles-edge} and the rules above, and the per-slice rings, gate the real packages.
     */
    @Test
    void everyProfilesEdgeRingIsOnTheGatedClasspath() {
        for (String ring : PROFILES_EDGE_RINGS) {
            assertThat(classes.stream()
                            .filter(c -> c.getPackageName().equals(ring)
                                    || c.getPackageName().startsWith(ring + ".")))
                    .as("classes in the ring %s", ring)
                    .isNotEmpty();
        }
    }

    @Test
    void theProfilesOverPacksRulesFailOnTheKnownBads() {
        assertBites(
                PROFILES_NAME_PACKS_ONLY_FROM_ADAPTER,
                ROOT + ".profiles.application.badfixture",
                "ProfileApplicationNamesPacks");
        assertBites(
                PROFILES_NAME_PACKS_ONLY_FROM_ADAPTER,
                ROOT + ".profiles.infra.api.badfixture",
                "ProfileApiNamesPacksApi",
                "ArtifactController");
        assertBites(
                PROFILES_NAME_PACKS_ONLY_FROM_ADAPTER,
                ROOT + ".profiles.domain.badfixture",
                "ProfileDomainReachesPacks");
        assertBites(
                PROFILES_PACKS_ADAPTER_NAMES_PACKS_APPLICATION,
                ROOT + ".profiles.infra.packs.badfixture",
                "ProfilePacksAdapterNamesPacksStorage",
                "ConfigRoot");
    }

    @Test
    void theProfilesOverActivationsRulesFailOnTheKnownBads() {
        assertBites(
                PROFILES_NAME_ACTIVATIONS_ONLY_FROM_ADAPTER,
                ROOT + ".profiles.application.badfixture",
                "ProfileApplicationNamesActivations");
        assertBites(
                PROFILES_ACTIVATIONS_ADAPTER_NAMES_ACTIVATIONS_APPLICATION,
                ROOT + ".profiles.infra.activations.badfixture",
                "ProfileActivationsAdapterNamesActivationsApi",
                "ActivationController");
    }

    @Test
    void theEdgeIsNotNamedBackRuleFailsOnPacksAndActivationsClassesThatNameIt() {
        assertBites(
                NO_PACKS_OR_ACTIVATIONS_CLASS_NAMES_THE_PROFILES_EDGE,
                ROOT + ".packs.application.badfixture",
                "ApplicationNamesProfilesApplication",
                "ProfileUseCaseStandIn");
        assertBites(
                NO_PACKS_OR_ACTIVATIONS_CLASS_NAMES_THE_PROFILES_EDGE,
                ROOT + ".activations.application.badfixture",
                "ApplicationNamesProfilesInfra",
                "ProfileApiStandIn");
    }

    /** The per-slice rings reach the profiles edge too: the same known-bads bite the rules of the {@code profiles} slice. */
    @Test
    void thePerSliceRingRulesBiteOnTheProfilesEdgeRings() {
        assertBites(
                applicationIsEdge("profiles"),
                ROOT + ".profiles.application.badfixture",
                "ProfileApplicationNamesPacks",
                "ProfileApplicationNamesActivations");
        assertBites(
                onlyDeployableComposesAdapters("profiles"),
                ROOT + ".activations.application.badfixture",
                "ApplicationNamesProfilesInfra");
    }

    // ---- The missions slice over the packs and activations slices (ADR-0037, amendment of 2026-11-09) ----------
    // The missions slice follows the profiles pattern: its domain stays in :missions, which packs.infra.storage binds,
    // and its edge rings live in :missions-edge. The edge asks the packs slice and the activations slice, each from one
    // adapter package of its own, and only their application and domain rings; and neither of those slices names the
    // missions edge back.

    private static final String MISSIONS = ROOT + ".missions..";
    /** The one missions package that may name the packs slice. */
    private static final String MISSIONS_PACKS_ADAPTER = ROOT + ".missions.infra.packs..";
    /** The one missions package that may name the activations slice. */
    private static final String MISSIONS_ACTIVATIONS_ADAPTER = ROOT + ".missions.infra.activations..";

    private static final String MISSIONS_APPLICATION = ROOT + ".missions.application..";
    private static final String MISSIONS_INFRA = ROOT + ".missions.infra..";

    /** The missions edge rings, each of which must be on the classpath for the rules below to check anything. */
    private static final List<String> MISSIONS_EDGE_RINGS = List.of(
            "ai.speckitty.charter.missions.application",
            "ai.speckitty.charter.missions.infra.api",
            "ai.speckitty.charter.missions.infra.packs",
            "ai.speckitty.charter.missions.infra.activations");

    /** A MISSIONS OVER PACKS (1/2): no missions class names the packs slice, but those of its packs adapter. */
    private static final ArchRule MISSIONS_NAME_PACKS_ONLY_FROM_ADAPTER = noClasses()
            .that()
            .resideInAPackage(MISSIONS)
            .and()
            .resideOutsideOfPackage(MISSIONS_PACKS_ADAPTER)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(PACKS);

    /**
     * A MISSIONS OVER PACKS (2/2): that adapter names only the packs application ring and the packs domain values its
     * answers carry, never a packs adapter: no {@code Hypermedia}, no {@code ArtifactDtos}, no {@code CharterHeaders}.
     */
    private static final ArchRule MISSIONS_PACKS_ADAPTER_NAMES_PACKS_APPLICATION = noClasses()
            .that()
            .resideInAPackage(MISSIONS_PACKS_ADAPTER)
            .should()
            .dependOnClassesThat(JavaClass.Predicates.resideInAPackage(PACKS)
                    .and(JavaClass.Predicates.resideOutsideOfPackages(PACKS_APPLICATION, PACKS_DOMAIN)));

    /** A MISSIONS OVER ACTIVATIONS (1/2): no missions class names the activations slice, but those of its adapter. */
    private static final ArchRule MISSIONS_NAME_ACTIVATIONS_ONLY_FROM_ADAPTER = noClasses()
            .that()
            .resideInAPackage(MISSIONS)
            .and()
            .resideOutsideOfPackage(MISSIONS_ACTIVATIONS_ADAPTER)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(ACTIVATIONS);

    /** A MISSIONS OVER ACTIVATIONS (2/2): that adapter names only the activations application and domain rings. */
    private static final ArchRule MISSIONS_ACTIVATIONS_ADAPTER_NAMES_ACTIVATIONS_APPLICATION = noClasses()
            .that()
            .resideInAPackage(MISSIONS_ACTIVATIONS_ADAPTER)
            .should()
            .dependOnClassesThat(JavaClass.Predicates.resideInAPackage(ACTIVATIONS)
                    .and(JavaClass.Predicates.resideOutsideOfPackages(
                            ACTIVATIONS_APPLICATION, ACTIVATIONS_DOMAIN_RING)));

    /**
     * THE MISSIONS EDGE IS NOT NAMED BACK: the packs and activations slices never name the missions edge rings
     * ({@code missions.application}, {@code missions.infra}). They may name the missions domain: the pack store binds
     * it.
     */
    private static final ArchRule NO_PACKS_OR_ACTIVATIONS_CLASS_NAMES_THE_MISSIONS_EDGE = noClasses()
            .that()
            .resideInAnyPackage(PACKS, ACTIVATIONS)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(MISSIONS_APPLICATION, MISSIONS_INFRA);

    @Test
    @DisplayName("MISSIONS_OVER_PACKS_AND_ACTIVATIONS")
    void theMissionsSliceNamesPacksAndActivationsOnlyThroughItsAdapters() {
        MISSIONS_NAME_PACKS_ONLY_FROM_ADAPTER.check(classes);
        MISSIONS_PACKS_ADAPTER_NAMES_PACKS_APPLICATION.check(classes);
        MISSIONS_NAME_ACTIVATIONS_ONLY_FROM_ADAPTER.check(classes);
        MISSIONS_ACTIVATIONS_ADAPTER_NAMES_ACTIVATIONS_APPLICATION.check(classes);
        NO_PACKS_OR_ACTIVATIONS_CLASS_NAMES_THE_MISSIONS_EDGE.check(classes);
    }

    /**
     * The slice rules use {@code allowEmptyShould}, so a module missing from the classpath passes with nothing checked.
     * This test fails instead: every missions edge ring holds a class, which means {@code :deployable} depends on
     * {@code :missions-edge} and the rules above, and the per-slice rings, gate the real packages.
     */
    @Test
    void everyMissionsEdgeRingIsOnTheGatedClasspath() {
        for (String ring : MISSIONS_EDGE_RINGS) {
            assertThat(classes.stream()
                            .filter(c -> c.getPackageName().equals(ring)
                                    || c.getPackageName().startsWith(ring + ".")))
                    .as("classes in the ring %s", ring)
                    .isNotEmpty();
        }
    }

    @Test
    void theMissionsOverPacksRulesFailOnTheKnownBads() {
        assertBites(
                MISSIONS_NAME_PACKS_ONLY_FROM_ADAPTER,
                ROOT + ".missions.application.badfixture",
                "MissionApplicationNamesPacks");
        assertBites(
                MISSIONS_NAME_PACKS_ONLY_FROM_ADAPTER,
                ROOT + ".missions.infra.api.badfixture",
                "MissionApiNamesPacksApi",
                "ArtifactController");
        assertBites(
                MISSIONS_NAME_PACKS_ONLY_FROM_ADAPTER,
                ROOT + ".missions.domain.badfixture",
                "MissionDomainReachesPacks");
        assertBites(
                MISSIONS_PACKS_ADAPTER_NAMES_PACKS_APPLICATION,
                ROOT + ".missions.infra.packs.badfixture",
                "MissionPacksAdapterNamesPacksStorage",
                "ConfigRoot");
    }

    @Test
    void theMissionsOverActivationsRulesFailOnTheKnownBads() {
        assertBites(
                MISSIONS_NAME_ACTIVATIONS_ONLY_FROM_ADAPTER,
                ROOT + ".missions.application.badfixture",
                "MissionApplicationNamesActivations");
        assertBites(
                MISSIONS_ACTIVATIONS_ADAPTER_NAMES_ACTIVATIONS_APPLICATION,
                ROOT + ".missions.infra.activations.badfixture",
                "MissionActivationsAdapterNamesActivationsApi",
                "ActivationController");
    }

    @Test
    void theMissionsEdgeIsNotNamedBackRuleFailsOnPacksAndActivationsClassesThatNameIt() {
        assertBites(
                NO_PACKS_OR_ACTIVATIONS_CLASS_NAMES_THE_MISSIONS_EDGE,
                ROOT + ".packs.application.badfixture",
                "ApplicationNamesMissionsApplication",
                "MissionUseCaseStandIn");
        assertBites(
                NO_PACKS_OR_ACTIVATIONS_CLASS_NAMES_THE_MISSIONS_EDGE,
                ROOT + ".activations.application.badfixture",
                "ApplicationNamesMissionsInfra",
                "MissionApiStandIn");
    }

    /** The per-slice rings reach the missions edge too: the same known-bads bite the rules of the {@code missions} slice. */
    @Test
    void thePerSliceRingRulesBiteOnTheMissionsEdgeRings() {
        assertBites(
                applicationIsEdge("missions"),
                ROOT + ".missions.application.badfixture",
                "MissionApplicationNamesPacks",
                "MissionApplicationNamesActivations");
        assertBites(
                onlyDeployableComposesAdapters("missions"),
                ROOT + ".activations.application.badfixture",
                "ApplicationNamesMissionsInfra");
    }

    // ---- The artifacts edge over the packs and activations slices (ADR-0037, amendment of 2026-11-13; research R13) -
    // The asset, skill and references reads of both trees live in :artifacts-edge, which follows the missions edge's
    // pattern with no domain module: :activations may not render a packs domain type (ACTIVATIONS_NAME_PACKS_ONLY_FROM_
    // ADAPTER). The edge asks the packs slice and the activations slice, each from one adapter package of its own, and
    // only their application and domain rings; and neither of those slices names the edge back.

    private static final String ARTIFACTS = ROOT + ".artifacts..";
    /** The one artifacts package that may name the packs slice. */
    private static final String ARTIFACTS_PACKS_ADAPTER = ROOT + ".artifacts.infra.packs..";
    /** The one artifacts package that may name the activations slice. */
    private static final String ARTIFACTS_ACTIVATIONS_ADAPTER = ROOT + ".artifacts.infra.activations..";

    /** The artifacts edge rings, each of which must be on the classpath for the rules below to check anything. */
    private static final List<String> ARTIFACTS_EDGE_RINGS = List.of(
            "ai.speckitty.charter.artifacts.application",
            "ai.speckitty.charter.artifacts.infra.api",
            "ai.speckitty.charter.artifacts.infra.packs",
            "ai.speckitty.charter.artifacts.infra.activations");

    /** AN ARTIFACTS EDGE OVER PACKS (1/2): no artifacts class names the packs slice, but those of its packs adapter. */
    private static final ArchRule ARTIFACTS_NAME_PACKS_ONLY_FROM_ADAPTER = noClasses()
            .that()
            .resideInAPackage(ARTIFACTS)
            .and()
            .resideOutsideOfPackage(ARTIFACTS_PACKS_ADAPTER)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(PACKS);

    /**
     * AN ARTIFACTS EDGE OVER PACKS (2/2): that adapter names only the packs application ring and the packs domain
     * values its answers carry, never a packs adapter: no {@code Hypermedia}, no {@code ArtifactDtos}, no
     * {@code CharterHeaders}.
     */
    private static final ArchRule ARTIFACTS_PACKS_ADAPTER_NAMES_PACKS_APPLICATION = noClasses()
            .that()
            .resideInAPackage(ARTIFACTS_PACKS_ADAPTER)
            .should()
            .dependOnClassesThat(JavaClass.Predicates.resideInAPackage(PACKS)
                    .and(JavaClass.Predicates.resideOutsideOfPackages(PACKS_APPLICATION, PACKS_DOMAIN)));

    /** AN ARTIFACTS EDGE OVER ACTIVATIONS (1/2): no artifacts class names the activations slice, but its adapter's. */
    private static final ArchRule ARTIFACTS_NAME_ACTIVATIONS_ONLY_FROM_ADAPTER = noClasses()
            .that()
            .resideInAPackage(ARTIFACTS)
            .and()
            .resideOutsideOfPackage(ARTIFACTS_ACTIVATIONS_ADAPTER)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(ACTIVATIONS);

    /** AN ARTIFACTS EDGE OVER ACTIVATIONS (2/2): that adapter names only the activations application and domain rings. */
    private static final ArchRule ARTIFACTS_ACTIVATIONS_ADAPTER_NAMES_ACTIVATIONS_APPLICATION = noClasses()
            .that()
            .resideInAPackage(ARTIFACTS_ACTIVATIONS_ADAPTER)
            .should()
            .dependOnClassesThat(JavaClass.Predicates.resideInAPackage(ACTIVATIONS)
                    .and(JavaClass.Predicates.resideOutsideOfPackages(
                            ACTIVATIONS_APPLICATION, ACTIVATIONS_DOMAIN_RING)));

    /**
     * THE ARTIFACTS EDGE IS NOT NAMED BACK: the packs and activations slices never name any artifacts class. The edge
     * has no domain module, so unlike the profiles and missions edges there is no domain the pack store may bind.
     */
    private static final ArchRule NO_PACKS_OR_ACTIVATIONS_CLASS_NAMES_THE_ARTIFACTS_EDGE = noClasses()
            .that()
            .resideInAnyPackage(PACKS, ACTIVATIONS)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(ARTIFACTS);

    @Test
    @DisplayName("ARTIFACTS_EDGE_OVER_PACKS_AND_ACTIVATIONS")
    void theArtifactsEdgeNamesPacksAndActivationsOnlyThroughItsAdapters() {
        ARTIFACTS_NAME_PACKS_ONLY_FROM_ADAPTER.check(classes);
        ARTIFACTS_PACKS_ADAPTER_NAMES_PACKS_APPLICATION.check(classes);
        ARTIFACTS_NAME_ACTIVATIONS_ONLY_FROM_ADAPTER.check(classes);
        ARTIFACTS_ACTIVATIONS_ADAPTER_NAMES_ACTIVATIONS_APPLICATION.check(classes);
        NO_PACKS_OR_ACTIVATIONS_CLASS_NAMES_THE_ARTIFACTS_EDGE.check(classes);
    }

    /**
     * The edge has no domain ring, so it is not in {@link #SLICES} (whose domain rules select a ring the edge lacks).
     * It is held to the per-slice rings that apply to an edge instead: its application ring is an edge
     * (APPLICATION_IS_EDGE), its inbound adapter names no other adapter of its own, only it and the root name its
     * adapters, and it declares no repository or gateway port.
     */
    @Test
    @DisplayName("ARTIFACTS_EDGE_RINGS")
    void theArtifactsEdgeIsHeldToTheEdgeRings() {
        applicationIsEdge("artifacts").check(classes);
        entrypointOverApplication("artifacts").check(classes);
        onlyDeployableComposesAdapters("artifacts").check(classes);
        portInterfacesResideInDomain("artifacts").check(classes);
        portImplementationsResideInStorage("artifacts").check(classes);
    }

    /**
     * The slice rules use {@code allowEmptyShould}, so a module missing from the classpath passes with nothing checked.
     * This test fails instead: every artifacts edge ring holds a class, which means {@code :deployable} depends on
     * {@code :artifacts-edge} and the rules above, and the edge rings, gate the real packages.
     */
    @Test
    void everyArtifactsEdgeRingIsOnTheGatedClasspath() {
        for (String ring : ARTIFACTS_EDGE_RINGS) {
            assertThat(classes.stream()
                            .filter(c -> c.getPackageName().equals(ring)
                                    || c.getPackageName().startsWith(ring + ".")))
                    .as("classes in the ring %s", ring)
                    .isNotEmpty();
        }
    }

    @Test
    void theArtifactsEdgeOverPacksRulesFailOnTheKnownBads() {
        assertBites(
                ARTIFACTS_NAME_PACKS_ONLY_FROM_ADAPTER,
                ROOT + ".artifacts.application.badfixture",
                "ArtifactsApplicationNamesPacks");
        assertBites(
                ARTIFACTS_NAME_PACKS_ONLY_FROM_ADAPTER,
                ROOT + ".artifacts.infra.api.badfixture",
                "ArtifactsApiNamesPacksApi",
                "ArtifactController");
        assertBites(
                ARTIFACTS_PACKS_ADAPTER_NAMES_PACKS_APPLICATION,
                ROOT + ".artifacts.infra.packs.badfixture",
                "ArtifactsPacksAdapterNamesPacksStorage",
                "ConfigRoot");
    }

    @Test
    void theArtifactsEdgeOverActivationsRulesFailOnTheKnownBads() {
        assertBites(
                ARTIFACTS_NAME_ACTIVATIONS_ONLY_FROM_ADAPTER,
                ROOT + ".artifacts.application.badfixture",
                "ArtifactsApplicationNamesActivations");
        assertBites(
                ARTIFACTS_NAME_ACTIVATIONS_ONLY_FROM_ADAPTER,
                ROOT + ".artifacts.infra.packs.badfixture",
                "ArtifactsPacksAdapterNamesActivations");
        assertBites(
                ARTIFACTS_ACTIVATIONS_ADAPTER_NAMES_ACTIVATIONS_APPLICATION,
                ROOT + ".artifacts.infra.activations.badfixture",
                "ArtifactsActivationsAdapterNamesActivationsApi",
                "ActivationController");
    }

    @Test
    void theArtifactsEdgeIsNotNamedBackRuleFailsOnPacksAndActivationsClassesThatNameIt() {
        assertBites(
                NO_PACKS_OR_ACTIVATIONS_CLASS_NAMES_THE_ARTIFACTS_EDGE,
                ROOT + ".packs.application.badfixture",
                "ApplicationNamesArtifactsApplication",
                "ArtifactsUseCaseStandIn");
        assertBites(
                NO_PACKS_OR_ACTIVATIONS_CLASS_NAMES_THE_ARTIFACTS_EDGE,
                ROOT + ".activations.application.badfixture",
                "ApplicationNamesArtifactsInfra",
                "ArtifactsApiStandIn");
    }

    /** The edge rings bite on the artifacts known-bads: the same factories the slices of {@link #SLICES} run. */
    @Test
    void theEdgeRingRulesBiteOnTheArtifactsEdgeRings() {
        assertBites(
                applicationIsEdge("artifacts"),
                ROOT + ".artifacts.application.badfixture",
                "ArtifactsApplicationNamesPacks",
                "ArtifactsApplicationNamesActivations",
                "ArtifactsApplicationNamesWebTransport");
        assertBites(
                entrypointOverApplication("artifacts"),
                ROOT + ".artifacts.infra.api.badfixture",
                "ArtifactsApiNamesPacksAdapter",
                "ArtifactsPacksStandIn");
        assertBites(
                onlyDeployableComposesAdapters("artifacts"),
                ROOT + ".activations.application.badfixture",
                "ApplicationNamesArtifactsInfra");
        assertBites(
                portInterfacesResideInDomain("artifacts"),
                ROOT + ".artifacts.application.badfixture",
                "ArtifactsMisplacedRepository");
        assertBites(
                portImplementationsResideInStorage("artifacts"),
                ROOT + ".artifacts.infra.api.badfixture",
                "ArtifactsImplementsPortInEntrypoint");
    }

    // ---- Code-ownership supplements (kept from the incremental rules) ---------------------------------------

    /**
     * The port signatures of the kernel and the packs domain name only kernel, packs domain, {@code java} and
     * JSpecify types (belt and braces atop DOMAIN_IS_PURE). The known-bad sits in the packs domain itself, so the
     * unchanged rule bites on it. Every slice's domain is held to the same rule over its own types by
     * {@link #portSignaturesNameOnlyDomainTypes(String)}.
     */
    private static final ArchRule PORT_SIGNATURES_NAME_ONLY_DOMAIN_TYPES = classes()
            .that()
            .areInterfaces()
            .and()
            .resideInAnyPackage(KERNEL, PACKS_DOMAIN)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(KERNEL, PACKS_DOMAIN, JAVA, JSPECIFY);

    /**
     * PORT_SIGNATURES_NAME_ONLY_DOMAIN_TYPES per slice: an interface of a slice's domain names only its own domain's
     * types, the kernel's, {@code java} and JSpecify; never another slice's domain, an adapter or a library.
     */
    private static ArchRule portSignaturesNameOnlyDomainTypes(String slice) {
        return classes()
                .that()
                .areInterfaces()
                .and()
                .resideInAPackage(ring(slice, "domain"))
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(ring(slice, "domain"), KERNEL, JAVA, JSPECIFY)
                .allowEmptyShould(true);
    }

    @Test
    void portSignaturesNameOnlyDomainTypes() {
        PORT_SIGNATURES_NAME_ONLY_DOMAIN_TYPES.check(classes);
        SLICES.forEach(slice -> portSignaturesNameOnlyDomainTypes(slice).check(classes));
    }

    @Test
    void thePerSlicePortSignatureRuleFailsOnAnActivationsPortThatNamesAnotherSlice() {
        assertBites(
                portSignaturesNameOnlyDomainTypes("activations"),
                ROOT + ".activations.domain.badfixture",
                "ActivationPortNamesPacks",
                "ai.speckitty.charter.packs.domain.Pack");
        assertBites(
                portSignaturesNameOnlyDomainTypes("packs"),
                ROOT + ".packs.domain.badfixture",
                "DomainWithInfraImport",
                "DomainRepositoryService");
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
     * domain's own words, that it does not know what went wrong. The enum itself is exempt. The refusal renderer the
     * inbound adapters share lives in the web ring ({@code web.RefusalHandler}), which is part of that edge.
     */
    private static ArchRule onlyTheInboundAdapterNamesInternalError() {
        return noClasses()
                .that()
                .resideOutsideOfPackages(PACKS_API, WEB)
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
     * Rule B: only the pack policy, the enums and the inbound edge (the packs adapter and the shared web transport's
     * refusal renderer) name {@code RefusalCode.PACK_LEVEL_MISMATCH}; {@code RefusalHandler$1} reads it with
     * {@code getstatic}.
     */
    private static ArchRule onlyThePolicyNamesTheMismatchRefusalRule() {
        return noClasses()
                .that(DescribedPredicate.not(namedWithPrefix(
                        "ai.speckitty.charter.packs.domain.PackPolicy",
                        "ai.speckitty.charter.packs.domain.PackFindingCode",
                        "ai.speckitty.charter.kernel.domain.RefusalCode",
                        "ai.speckitty.charter.packs.infra.api.",
                        "ai.speckitty.charter.web.")))
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
